package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.mob.MobRenderDefinition;
import com.github.kdgaming0.skyrecipes.core.model.*;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

/**
 * Runtime loader that reads the compiled binary .mpk file from disk
 * and deserializes it into registries.
 *
 * <p>Supports memory-mapped file access for fast loading.</p>
 */
public class BinaryDataLoader {

    public static final int EXPECTED_SCHEMA = 10;
    private static final int HEADER_SIZE = 96;
    private static final long MAX_METADATA_LENGTH = 1 << 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryDataLoader.class);
    private static final byte[] EXPECTED_MAGIC = new byte[]{'S', 'K', 'Y', '2'};
    private ByteBuffer fileBuffer;
    private ItemRegistry itemRegistry;
    private ConstantsRegistry constantsRegistry;
    private BinaryMetadata metadata;
    private LoadFailure lastFailure = LoadFailure.NONE;

    /**
     * Read only the embedded metadata section from a v8 binary, without
     * deserializing items or constants. Used for cheap ETag comparisons.
     */
    public static BinaryMetadata readEmbeddedMetadata(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            if (raf.length() < HEADER_SIZE) {
                throw new IOException("Binary file too small");
            }
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (!Arrays.equals(magic, EXPECTED_MAGIC)) {
                throw new IOException("Invalid binary magic bytes");
            }
            int schemaVersion = raf.readInt();
            if (schemaVersion != EXPECTED_SCHEMA) {
                throw new IOException("Schema version mismatch: " + schemaVersion);
            }
            raf.seek(64);
            long metadataOffset = raf.readLong();
            long metadataLength = raf.readLong();
            if (metadataLength <= 0 || metadataLength > MAX_METADATA_LENGTH
                    || metadataOffset + metadataLength > raf.length()) {
                throw new IOException("Invalid metadata section bounds");
            }
            raf.seek(metadataOffset);
            byte[] bytes = new byte[(int) metadataLength];
            raf.readFully(bytes);
            return BinaryMetadata.fromBytes(bytes);
        }
    }

    /**
     * Load binary data from a file path.
     *
     * <p>Validation order: size, magic, schema, section bounds, payload
     * CRC32C, metadata section, items, constants. Each rejection logs a
     * distinct reason and sets {@link #getLastFailure()}.</p>
     *
     * @param path path to the .mpk file
     * @return true if loaded successfully
     */
    public boolean load(Path path) {
        long startTime = System.currentTimeMillis();
        lastFailure = LoadFailure.CORRUPT;
        try {
            close(); // release any previous mapping

            this.fileBuffer = MmapUtil.mapFile(path);

            if (fileBuffer.remaining() < HEADER_SIZE) {
                LOGGER.error("Binary file too small ({} bytes). Expected at least {} bytes.",
                        fileBuffer.remaining(), HEADER_SIZE);
                return false;
            }

            // Read header
            byte[] magic = new byte[4];
            fileBuffer.get(magic);
            if (!Arrays.equals(magic, EXPECTED_MAGIC)) {
                LOGGER.error("Invalid binary magic bytes. Expected SKY2.");
                return false;
            }

            int schemaVersion = fileBuffer.getInt();
            long buildTimestamp = fileBuffer.getLong();
            int itemCount = fileBuffer.getInt();
            int sectionCount = fileBuffer.getInt();
            fileBuffer.getLong();
            long itemsOffset = fileBuffer.getLong();
            long itemsLength = fileBuffer.getLong();
            long constantsOffset = fileBuffer.getLong();
            long constantsLength = fileBuffer.getLong();
            long metadataOffset = fileBuffer.getLong();
            long metadataLength = fileBuffer.getLong();
            int expectedCrc = fileBuffer.getInt();

            if (schemaVersion != EXPECTED_SCHEMA) {
                LOGGER.error("Binary schema version mismatch: expected {}, got {}. Data may be stale or incompatible.",
                        EXPECTED_SCHEMA, schemaVersion);
                lastFailure = LoadFailure.SCHEMA_MISMATCH;
                return false;
            }

            long fileSize = fileBuffer.capacity();
            if (itemsOffset + itemsLength > fileSize
                    || constantsOffset + constantsLength > fileSize
                    || metadataOffset + metadataLength > fileSize) {
                LOGGER.error("Binary section offsets exceed file size. File may be truncated.");
                return false;
            }
            if (metadataLength <= 0 || metadataLength > MAX_METADATA_LENGTH) {
                LOGGER.error("Binary metadata section has implausible length {}.", metadataLength);
                return false;
            }

            java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
            crc.update(fileBuffer.duplicate().position(HEADER_SIZE));
            if ((int) crc.getValue() != expectedCrc) {
                LOGGER.error("Binary checksum mismatch (expected {}, got {}). File is corrupt.",
                        Integer.toHexString(expectedCrc), Long.toHexString(crc.getValue() & 0xFFFFFFFFL));
                return false;
            }

            byte[] metadataBytes = new byte[(int) metadataLength];
            fileBuffer.duplicate().position((int) metadataOffset).get(metadataBytes);
            try {
                this.metadata = BinaryMetadata.fromBytes(metadataBytes);
            } catch (Exception e) {
                LOGGER.error("Binary metadata section is unreadable.", e);
                return false;
            }

            LOGGER.info("Loading binary: schema={}, items={}, sections={}, built={}",
                    schemaVersion, itemCount, sectionCount, new Date(buildTimestamp));

            // Read items section
            // Copy to byte[] because msgpack-core 0.9.8 cannot read from
            // MappedByteBuffer (direct buffer) on Java 25+ due to module restrictions.
            byte[] itemsBytes = new byte[(int) itemsLength];
            fileBuffer.duplicate().position((int) itemsOffset).limit((int) (itemsOffset + itemsLength)).get(itemsBytes);
            List<NeuItem> items = unpackItems(itemsBytes, itemCount);
            this.itemRegistry = new ItemRegistry(items);

            // Read constants section
            byte[] constantsBytes = new byte[(int) constantsLength];
            fileBuffer.duplicate().position((int) constantsOffset).limit((int) (constantsOffset + constantsLength)).get(constantsBytes);
            this.constantsRegistry = unpackConstants(constantsBytes);

            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.info("Binary loaded in {} ms. Items: {}, Parents: {}, Essence: {}, Bazaar: {}, Museum: {}, Reforges: {}, ReforgeStones: {}, MobDefs: {}, MobSkins: {}",
                    elapsed,
                    itemRegistry.size(),
                    constantsRegistry.getAllParents().size(),
                    constantsRegistry.getAllEssenceCosts().size(),
                    constantsRegistry.getBazaarItems().size(),
                    constantsRegistry.getAllMuseumCategories().size(),
                    constantsRegistry.getAllReforges().size(),
                    constantsRegistry.getAllReforgeStones().size(),
                    constantsRegistry.getAllMobDefinitions().size(),
                    constantsRegistry.getAllMobSkins().size()
            );
            lastFailure = LoadFailure.NONE;
            return true;

        } catch (Exception e) {
            // msgpack throws RuntimeExceptions (MessagePackException) on data that
            // passes the CRC but is semantically corrupt — same contract: return false.
            LOGGER.error("Failed to load binary data", e);
            return false;
        } finally {
            // Everything is copied to heap during load, so the mapping is never
            // read again — release it immediately. A held mapping also locks the
            // file on Windows, blocking moves and later compiles.
            if (fileBuffer != null) {
                MmapUtil.unmap(fileBuffer);
                fileBuffer = null;
            }
            if (lastFailure != LoadFailure.NONE) {
                close();
            }
        }
    }

    /**
     * Drop the loaded registries. The file mapping is already released at the
     * end of {@link #load(Path)}; this only frees the heap data.
     */
    public void close() {
        if (fileBuffer != null) {
            MmapUtil.unmap(fileBuffer);
            fileBuffer = null;
        }
        itemRegistry = null;
        constantsRegistry = null;
        metadata = null;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public ConstantsRegistry getConstantsRegistry() {
        return constantsRegistry;
    }

    /**
     * Metadata embedded in the loaded binary; non-null after a successful {@link #load(Path)}.
     */
    public BinaryMetadata getMetadata() {
        return metadata;
    }

    public LoadFailure getLastFailure() {
        return lastFailure;
    }

    private List<NeuItem> unpackItems(byte[] data, int expectedCount) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            return unpackItemsInternal(unpacker, expectedCount);
        }
    }

    // ---- MessagePack deserialization ----

    private List<NeuItem> unpackItemsInternal(MessageUnpacker unpacker, int expectedCount) throws IOException {
        List<NeuItem> items = new ArrayList<>(expectedCount);
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
            String island = "";
            int x = 0, y = 0, z = 0;

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
                    case "island" -> island = unpacker.unpackString();
                    case "x" -> x = unpacker.unpackInt();
                    case "y" -> y = unpacker.unpackInt();
                    case "z" -> z = unpacker.unpackInt();
                    default -> unpacker.skipValue();
                }
            }
            items.add(new NeuItem(internalName, itemId, displayName, nbtTag, lore, damage,
                    clickCommand, craftText, infoType, info, recipe, recipes, slayerReq, vanilla,
                    island, x, y, z));
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

    private Map<String, String> unpackStringStringMap(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackMapHeader();
        Map<String, String> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(unpacker.unpackString(), unpacker.unpackString());
        }
        return map;
    }

    private Map<String, Number> unpackStringNumberMap(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackMapHeader();
        Map<String, Number> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = unpacker.unpackString();
            Value v = unpacker.unpackValue();
            if (v.isIntegerValue()) {
                map.put(key, v.asIntegerValue().asInt());
            } else if (v.isFloatValue()) {
                map.put(key, v.asFloatValue().toDouble());
            } else if (v.isNumberValue()) {
                map.put(key, v.asNumberValue().toDouble());
            }
        }
        return map;
    }

    private Map<String, Map<String, Number>> unpackStringNumberMapMap(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackMapHeader();
        Map<String, Map<String, Number>> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = unpacker.unpackString();
            map.put(key, unpackStringNumberMap(unpacker));
        }
        return map;
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
                String name = raw.containsKey("name") ? raw.get("name").asStringValue().asString() : "";
                String render = raw.containsKey("render") ? raw.get("render").asStringValue().asString() : "";
                yield new NeuRecipe.DropsRecipe(name, render, drops);
            }
            case "trade" -> new NeuRecipe.TradeRecipe(
                    raw.containsKey("cost") ? raw.get("cost").asStringValue().asString() : "",
                    raw.containsKey("result") ? raw.get("result").asStringValue().asString() : "",
                    raw.containsKey("count") ? raw.get("count").asIntegerValue().asInt() : 1,
                    raw.containsKey("min") ? raw.get("min").asIntegerValue().asInt() : 0,
                    raw.containsKey("max") ? raw.get("max").asIntegerValue().asInt() : 0
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
            if (!e.getValue().isNilValue() && e.getValue().isStringValue()) {
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
            return unpackConstantsInternal(unpacker);
        }
    }

    private ConstantsRegistry unpackConstantsInternal(MessageUnpacker unpacker) throws IOException {
        int mapSize = unpacker.unpackMapHeader();
        Map<String, List<String>> parents = new LinkedHashMap<>();
        Map<String, EssenceUpgradeData> essenceCosts = new LinkedHashMap<>();
        Set<String> bazaarItems = new HashSet<>();
        Map<String, String> museumCategories = new LinkedHashMap<>();
        Map<String, String> museumChildren = new LinkedHashMap<>();
        Map<String, ReforgeData> reforges = new LinkedHashMap<>();
        Map<String, ReforgeStoneData> reforgeStones = new LinkedHashMap<>();
        Set<String> knownStats = new HashSet<>();
        Map<String, String> reforgeNameToStone = new LinkedHashMap<>();
        Map<String, MobRenderDefinition> mobDefinitions = new LinkedHashMap<>();
        Map<String, byte[]> mobSkins = new LinkedHashMap<>();
        Map<String, AttributeShardData> attributeShards = new LinkedHashMap<>();

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
                case "museumChildren" -> {
                    int msize = unpacker.unpackMapHeader();
                    for (int j = 0; j < msize; j++) {
                        museumChildren.put(unpacker.unpackString(), unpacker.unpackString());
                    }
                }
                case "reforges" -> {
                    int rsize = unpacker.unpackMapHeader();
                    for (int j = 0; j < rsize; j++) {
                        String name = unpacker.unpackString();
                        int rmapSize = unpacker.unpackMapHeader();
                        String reforgeName = "";
                        String itemTypes = "";
                        List<String> requiredRarities = new ArrayList<>();
                        Map<String, Map<String, Number>> stats = new LinkedHashMap<>();
                        Map<String, String> ability = new LinkedHashMap<>();
                        Map<String, Number> costs = new LinkedHashMap<>();
                        for (int k = 0; k < rmapSize; k++) {
                            String rk = unpacker.unpackString();
                            switch (rk) {
                                case "reforgeName" -> reforgeName = unpacker.unpackString();
                                case "itemTypes" -> itemTypes = unpacker.unpackString();
                                case "requiredRarities" -> requiredRarities = unpackStringList(unpacker);
                                case "reforgeStats" -> stats = unpackStringNumberMapMap(unpacker);
                                case "reforgeAbility" -> ability = unpackStringStringMap(unpacker);
                                case "reforgeCosts" -> costs = unpackStringNumberMap(unpacker);
                                default -> unpacker.skipValue();
                            }
                        }
                        reforges.put(name, new ReforgeData(reforgeName, itemTypes, requiredRarities, stats, ability, costs));
                    }
                }
                case "reforgeStones" -> {
                    int rsize = unpacker.unpackMapHeader();
                    for (int j = 0; j < rsize; j++) {
                        String name = unpacker.unpackString();
                        int rmapSize = unpacker.unpackMapHeader();
                        String internalName = "";
                        String reforgeName = "";
                        String reforgeType = "";
                        String itemTypes = "";
                        List<String> requiredRarities = new ArrayList<>();
                        Map<String, String> ability = new LinkedHashMap<>();
                        Map<String, Number> costs = new LinkedHashMap<>();
                        Map<String, Map<String, Number>> stats = new LinkedHashMap<>();
                        for (int k = 0; k < rmapSize; k++) {
                            String rk = unpacker.unpackString();
                            switch (rk) {
                                case "internalName" -> internalName = unpacker.unpackString();
                                case "reforgeName" -> reforgeName = unpacker.unpackString();
                                case "reforgeType" -> reforgeType = unpacker.unpackString();
                                case "itemTypes" -> itemTypes = unpacker.unpackString();
                                case "requiredRarities" -> requiredRarities = unpackStringList(unpacker);
                                case "reforgeAbility" -> ability = unpackStringStringMap(unpacker);
                                case "reforgeCosts" -> costs = unpackStringNumberMap(unpacker);
                                case "reforgeStats" -> stats = unpackStringNumberMapMap(unpacker);
                                default -> unpacker.skipValue();
                            }
                        }
                        reforgeStones.put(name, new ReforgeStoneData(
                                internalName, reforgeName, reforgeType, itemTypes, requiredRarities, ability, costs, stats
                        ));
                    }
                }
                case "knownStats" -> {
                    int ssize = unpacker.unpackArrayHeader();
                    for (int j = 0; j < ssize; j++) {
                        knownStats.add(unpacker.unpackString());
                    }
                }
                case "reforgeNameToStone" -> {
                    int rsize = unpacker.unpackMapHeader();
                    for (int j = 0; j < rsize; j++) {
                        reforgeNameToStone.put(unpacker.unpackString(), unpacker.unpackString());
                    }
                }
                case "mobDefinitions" -> {
                    int msize = unpacker.unpackMapHeader();
                    for (int j = 0; j < msize; j++) {
                        String ref = unpacker.unpackString();
                        MobRenderDefinition def = unpackMobRenderDefinition(unpacker);
                        if (def != null) {
                            mobDefinitions.put(ref, def);
                        }
                    }
                }
                case "mobSkins" -> {
                    int msize = unpacker.unpackMapHeader();
                    for (int j = 0; j < msize; j++) {
                        String path = unpacker.unpackString();
                        int binLen = unpacker.unpackBinaryHeader();
                        byte[] bytes = unpacker.readPayload(binLen);
                        mobSkins.put(path, bytes);
                    }
                }
                case "attributeShards" -> {
                    int ssize = unpacker.unpackMapHeader();
                    for (int j = 0; j < ssize; j++) {
                        String internalName = unpacker.unpackString();
                        int smapSize = unpacker.unpackMapHeader();
                        String shardName = "";
                        String abilityName = "";
                        String rarity = "";
                        String alignment = "";
                        List<String> family = Collections.emptyList();
                        String shardId = "";
                        String bazaarName = "";
                        for (int k = 0; k < smapSize; k++) {
                            String sk = unpacker.unpackString();
                            switch (sk) {
                                case "shardName" -> shardName = unpacker.unpackString();
                                case "abilityName" -> abilityName = unpacker.unpackString();
                                case "rarity" -> rarity = unpacker.unpackString();
                                case "alignment" -> alignment = unpacker.unpackString();
                                case "family" -> family = unpackStringList(unpacker);
                                case "shardId" -> shardId = unpacker.unpackString();
                                case "bazaarName" -> bazaarName = unpacker.unpackString();
                                default -> unpacker.skipValue();
                            }
                        }
                        attributeShards.put(internalName, new AttributeShardData(
                                internalName, shardName, abilityName, rarity, alignment, family, shardId, bazaarName));
                    }
                }
                default -> unpacker.skipValue();
            }
        }
        return new ConstantsRegistry(parents, essenceCosts, bazaarItems, museumCategories, reforges, reforgeStones, knownStats, reforgeNameToStone, mobDefinitions, mobSkins, museumChildren, attributeShards);
    }

    private MobRenderDefinition unpackMobRenderDefinition(MessageUnpacker unpacker) throws IOException {
        int mapSize = unpacker.unpackMapHeader();
        String entityKind = "";
        String horseKind = null;
        String skinPath = null;
        String helmetItemId = null;
        MobRenderDefinition rider = null;
        for (int i = 0; i < mapSize; i++) {
            String key = unpacker.unpackString();
            switch (key) {
                case "entityKind" -> entityKind = unpacker.unpackString();
                case "horseKind" -> horseKind = unpacker.unpackString();
                case "skinPath" -> skinPath = unpacker.unpackString();
                case "helmetItemId" -> helmetItemId = unpacker.unpackString();
                case "rider" -> rider = unpackMobRenderDefinition(unpacker);
                default -> unpacker.skipValue();
            }
        }
        if (entityKind.isEmpty()) return null;
        return new MobRenderDefinition(entityKind, horseKind, skinPath, helmetItemId, rider);
    }

    /**
     * Why the most recent {@link #load(Path)} returned false. Lets callers
     * distinguish a stale-but-intact file (recompile) from real corruption
     * (quarantine before recompiling).
     */
    public enum LoadFailure {NONE, SCHEMA_MISMATCH, CORRUPT}
}
