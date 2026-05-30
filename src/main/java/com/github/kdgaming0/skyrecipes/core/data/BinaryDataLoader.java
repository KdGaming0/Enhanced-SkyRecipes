package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.model.*;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Runtime loader that reads the compiled binary .mpk file from the mod JAR
 * and deserializes it into registries.
 */
public class BinaryDataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryDataLoader.class);

    private static final byte[] EXPECTED_MAGIC = new byte[] { 'S', 'K', 'Y', '1' };
    private static final int EXPECTED_SCHEMA = 1;

    private ItemRegistry itemRegistry;
    private ConstantsRegistry constantsRegistry;

    public boolean load(InputStream input) {
        long startTime = System.currentTimeMillis();
        try {
            // Read header
            byte[] magic = new byte[4];
            if (input.read(magic) != 4 || !Arrays.equals(magic, EXPECTED_MAGIC)) {
                LOGGER.error("Invalid binary magic bytes. Expected SKY1.");
                return false;
            }

            int schemaVersion = readInt(input);
            long buildTimestamp = readLong(input);
            int itemCount = readInt(input);
            int sectionCount = readInt(input);
            long commitHash = readLong(input);
            long itemsOffset = readLong(input);
            long itemsLength = readLong(input);
            long constantsOffset = readLong(input);
            long constantsLength = readLong(input);

            if (schemaVersion != EXPECTED_SCHEMA) {
                LOGGER.error("Binary schema version mismatch: expected {}, got {}. Data may be stale or incompatible.",
                    EXPECTED_SCHEMA, schemaVersion);
                return false;
            }

            LOGGER.info("Loading binary: schema={}, items={}, sections={}, built={}",
                schemaVersion, itemCount, sectionCount, new Date(buildTimestamp));

            // Read items section
            skip(input, itemsOffset - 64); // 64 = header size already read
            byte[] itemsBytes = new byte[(int) itemsLength];
            if (input.read(itemsBytes) != itemsLength) {
                LOGGER.error("Failed to read full items section");
                return false;
            }

            List<NeuItem> items = unpackItems(itemsBytes, itemCount);
            this.itemRegistry = new ItemRegistry(items);

            // Read constants section
            skip(input, constantsOffset - itemsOffset - itemsLength);
            byte[] constantsBytes = new byte[(int) constantsLength];
            if (input.read(constantsBytes) != constantsLength) {
                LOGGER.error("Failed to read full constants section");
                return false;
            }

            this.constantsRegistry = unpackConstants(constantsBytes);

            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.info("Binary loaded in {} ms. Items: {}, Parents: {}, Essence: {}, Bazaar: {}, Museum: {}",
                elapsed,
                itemRegistry.size(),
                constantsRegistry.getAllParents().size(),
                constantsRegistry.getAllEssenceCosts().size(),
                0, // bazaar count not exposed directly
                0  // museum count not exposed directly
            );
            return true;

        } catch (IOException e) {
            LOGGER.error("Failed to load binary data", e);
            return false;
        }
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public ConstantsRegistry getConstantsRegistry() {
        return constantsRegistry;
    }

    // ---- Header reading helpers ----

    private int readInt(InputStream in) throws IOException {
        return (in.read() << 24) | (in.read() << 16) | (in.read() << 8) | in.read();
    }

    private long readLong(InputStream in) throws IOException {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (in.read() & 0xFFL);
        }
        return value;
    }

    private void skip(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) break;
            remaining -= skipped;
        }
    }

    // ---- MessagePack deserialization ----

    private List<NeuItem> unpackItems(byte[] data, int expectedCount) throws IOException {
        List<NeuItem> items = new ArrayList<>(expectedCount);
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            int count = unpacker.unpackArrayHeader();
            for (int i = 0; i < count; i++) {
                int mapSize = unpacker.unpackMapHeader();
                String internalName = "";
                String itemId = "";
                String displayName = "";
                String nbtTag = "";
                List<String> lore = Collections.emptyList();
                int damage = 0;
                String clickCommand = "";
                String craftText = "";
                String infoType = "";
                List<String> info = Collections.emptyList();
                NeuRecipe recipe = null;
                List<NeuRecipe> recipes = null;
                String slayerReq = null;
                boolean vanilla = false;

                for (int j = 0; j < mapSize; j++) {
                    String key = unpacker.unpackString();
                    switch (key) {
                        case "internalName" -> internalName = unpacker.unpackString();
                        case "itemId" -> itemId = unpacker.unpackString();
                        case "displayName" -> displayName = unpacker.unpackString();
                        case "nbtTag" -> nbtTag = unpacker.unpackString();
                        case "lore" -> lore = unpackStringList(unpacker);
                        case "damage" -> damage = unpacker.unpackInt();
                        case "clickCommand" -> clickCommand = unpacker.unpackString();
                        case "craftText" -> craftText = unpacker.unpackString();
                        case "infoType" -> infoType = unpacker.unpackString();
                        case "info" -> info = unpackStringList(unpacker);
                        case "recipe" -> recipe = unpackRecipe(unpacker);
                        case "recipes" -> recipes = unpackRecipeList(unpacker);
                        case "slayerReq" -> {
                            if (!unpacker.tryUnpackNil()) {
                                slayerReq = unpacker.unpackString();
                            }
                        }
                        case "vanilla" -> vanilla = unpacker.unpackBoolean();
                        default -> unpacker.skipValue();
                    }
                }
                items.add(new NeuItem(internalName, itemId, displayName, nbtTag, lore, damage,
                    clickCommand, craftText, infoType, info, recipe, recipes, slayerReq, vanilla));
            }
        }
        return items;
    }

    private List<String> unpackStringList(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackArrayHeader();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(unpacker.unpackString());
        }
        return list;
    }

    private NeuRecipe unpackRecipe(MessageUnpacker unpacker) throws IOException {
        if (unpacker.tryUnpackNil()) {
            return null;
        }
        int mapSize = unpacker.unpackMapHeader();
        String type = "";
        Map<String, Value> raw = new LinkedHashMap<>();
        for (int i = 0; i < mapSize; i++) {
            String key = unpacker.unpackString();
            if (key.equals("_type")) {
                type = unpacker.unpackString();
            } else {
                raw.put(key, unpacker.unpackValue());
            }
        }

        return switch (type) {
            case "crafting" -> new NeuRecipe.CraftingRecipe(
                unpackStringMap(raw),
                raw.containsKey("count") ? raw.get("count").asIntegerValue().asInt() : 1,
                raw.containsKey("overrideOutputId") ? raw.get("overrideOutputId").asStringValue().asString() : ""
            );
            case "forge" -> new NeuRecipe.ForgeRecipe(
                raw.containsKey("inputs") ? unpackStringList(raw.get("inputs")) : Collections.emptyList(),
                raw.containsKey("count") ? raw.get("count").asIntegerValue().asInt() : 1,
                raw.containsKey("overrideOutputId") ? raw.get("overrideOutputId").asStringValue().asString() : "",
                raw.containsKey("duration") ? raw.get("duration").asIntegerValue().asInt() : 0
            );
            case "katgrade" -> new NeuRecipe.KatGradeRecipe(
                raw.containsKey("coins") ? raw.get("coins").asIntegerValue().asInt() : 0,
                raw.containsKey("time") ? raw.get("time").asIntegerValue().asInt() : 0,
                raw.containsKey("input") ? raw.get("input").asStringValue().asString() : "",
                raw.containsKey("output") ? raw.get("output").asStringValue().asString() : "",
                raw.containsKey("items") ? unpackStringList(raw.get("items")) : Collections.emptyList()
            );
            case "npc_shop" -> {
                List<NeuRecipe.NpcShopRecipe.Cost> costs = new ArrayList<>();
                if (raw.containsKey("cost")) {
                    for (Value v : raw.get("cost").asArrayValue()) {
                        Map<Value, Value> cm = v.asMapValue().map();
                        String item = "";
                        int cost = 0;
                        for (Map.Entry<Value, Value> e : cm.entrySet()) {
                            String k = e.getKey().asStringValue().asString();
                            if (k.equals("item")) item = e.getValue().asStringValue().asString();
                            else if (k.equals("cost")) cost = e.getValue().asIntegerValue().asInt();
                        }
                        costs.add(new NeuRecipe.NpcShopRecipe.Cost(item, cost));
                    }
                }
                yield new NeuRecipe.NpcShopRecipe(
                    raw.containsKey("npc") ? raw.get("npc").asStringValue().asString() : "",
                    costs,
                    raw.containsKey("result") ? raw.get("result").asStringValue().asString() : ""
                );
            }
            case "drops" -> {
                List<NeuRecipe.DropsRecipe.Drop> drops = new ArrayList<>();
                if (raw.containsKey("drops")) {
                    for (Value v : raw.get("drops").asArrayValue()) {
                        Map<Value, Value> dm = v.asMapValue().map();
                        String id = "";
                        String chance = "";
                        for (Map.Entry<Value, Value> e : dm.entrySet()) {
                            String k = e.getKey().asStringValue().asString();
                            if (k.equals("id")) id = e.getValue().asStringValue().asString();
                            else if (k.equals("chance")) chance = e.getValue().asStringValue().asString();
                        }
                        drops.add(new NeuRecipe.DropsRecipe.Drop(id, chance));
                    }
                }
                yield new NeuRecipe.DropsRecipe(drops);
            }
            case "trade" -> new NeuRecipe.TradeRecipe(
                raw.containsKey("inputs") ? unpackStringList(raw.get("inputs")) : Collections.emptyList(),
                raw.containsKey("output") ? raw.get("output").asStringValue().asString() : "",
                raw.containsKey("count") ? raw.get("count").asIntegerValue().asInt() : 1
            );
            default -> null;
        };
    }

    private List<NeuRecipe> unpackRecipeList(MessageUnpacker unpacker) throws IOException {
        if (unpacker.tryUnpackNil()) {
            return null;
        }
        int size = unpacker.unpackArrayHeader();
        List<NeuRecipe> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            NeuRecipe r = unpackRecipe(unpacker);
            if (r != null) list.add(r);
        }
        return list;
    }

    private Map<String, String> unpackStringMap(Map<String, Value> raw) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, Value> e : raw.entrySet()) {
            if (!e.getValue().isNilValue()) {
                map.put(e.getKey(), e.getValue().asStringValue().asString());
            }
        }
        return map;
    }

    private List<String> unpackStringList(Value value) {
        List<String> list = new ArrayList<>();
        for (Value v : value.asArrayValue()) {
            list.add(v.asStringValue().asString());
        }
        return list;
    }

    private ConstantsRegistry unpackConstants(byte[] data) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            int mapSize = unpacker.unpackMapHeader();
            Map<String, List<String>> parents = new LinkedHashMap<>();
            Map<String, EssenceUpgradeData> essenceCosts = new LinkedHashMap<>();
            Set<String> bazaarItems = new HashSet<>();
            Map<String, String> museumCategories = new LinkedHashMap<>();

            for (int i = 0; i < mapSize; i++) {
                String key = unpacker.unpackString();
                switch (key) {
                    case "parents" -> {
                        int psize = unpacker.unpackMapHeader();
                        for (int j = 0; j < psize; j++) {
                            String parent = unpacker.unpackString();
                            int csize = unpacker.unpackArrayHeader();
                            List<String> children = new ArrayList<>(csize);
                            for (int k = 0; k < csize; k++) {
                                children.add(unpacker.unpackString());
                            }
                            parents.put(parent, children);
                        }
                    }
                    case "essenceCosts" -> {
                        int esize = unpacker.unpackMapHeader();
                        for (int j = 0; j < esize; j++) {
                            String itemName = unpacker.unpackString();
                            int imapSize = unpacker.unpackMapHeader();
                            String essenceType = "";
                            Map<Integer, Integer> costs = new LinkedHashMap<>();
                            Map<Integer, List<String>> extraItems = new LinkedHashMap<>();
                            for (int k = 0; k < imapSize; k++) {
                                String ik = unpacker.unpackString();
                                if (ik.equals("type")) {
                                    essenceType = unpacker.unpackString();
                                } else if (ik.equals("items")) {
                                    int xsize = unpacker.unpackMapHeader();
                                    for (int m = 0; m < xsize; m++) {
                                        int tier = Integer.parseInt(unpacker.unpackString());
                                        int asize = unpacker.unpackArrayHeader();
                                        List<String> reqs = new ArrayList<>(asize);
                                        for (int n = 0; n < asize; n++) {
                                            reqs.add(unpacker.unpackString());
                                        }
                                        extraItems.put(tier, reqs);
                                    }
                                } else {
                                    try {
                                        costs.put(Integer.parseInt(ik), unpacker.unpackInt());
                                    } catch (NumberFormatException e) {
                                        unpacker.skipValue();
                                    }
                                }
                            }
                            essenceCosts.put(itemName, new EssenceUpgradeData(essenceType, costs, extraItems));
                        }
                    }
                    case "bazaarItems" -> {
                        int bsize = unpacker.unpackArrayHeader();
                        for (int j = 0; j < bsize; j++) {
                            bazaarItems.add(unpacker.unpackString());
                        }
                    }
                    case "museum" -> {
                        int msize = unpacker.unpackMapHeader();
                        for (int j = 0; j < msize; j++) {
                            museumCategories.put(unpacker.unpackString(), unpacker.unpackString());
                        }
                    }
                    default -> unpacker.skipValue();
                }
            }
            return new ConstantsRegistry(parents, essenceCosts, bazaarItems, museumCategories);
        }
    }
}
