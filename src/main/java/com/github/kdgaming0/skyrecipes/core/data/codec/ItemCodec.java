package com.github.kdgaming0.skyrecipes.core.data.codec;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MessagePack codec for the items section of the binary cache.
 *
 * <p>Each shape's pack and unpack methods sit next to each other so a format
 * change is always edited as a pair. The binary layout must stay byte-identical
 * between {@code pack} and {@code unpack}; bump
 * {@code BinaryDataCompiler.SCHEMA_VERSION} when it changes.</p>
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static void packItems(MessagePacker packer, List<NeuItem> items) throws IOException {
        packer.packArrayHeader(items.size());
        for (NeuItem item : items) {
            packer.packMapHeader(18);
            packer.packString("internalName");
            packer.packString(item.internalName());
            packer.packString("itemId");
            packer.packString(item.itemId());
            packer.packString("displayName");
            packer.packString(item.displayName());
            packer.packString("nbtTag");
            packer.packString(item.nbtTag());

            packer.packString("lore");
            CodecUtil.packStringCollection(packer, item.lore());

            packer.packString("damage");
            packer.packInt(item.damage());
            packer.packString("clickCommand");
            packer.packString(item.clickCommand());
            packer.packString("craftText");
            packer.packString(item.craftText());
            packer.packString("infoType");
            packer.packString(item.infoType());

            packer.packString("info");
            CodecUtil.packStringCollection(packer, item.info());

            // Only crafting recipes appear in the single "recipe" field; every
            // other type lives in the "recipes" list.
            packer.packString("recipe");
            if (item.recipe() instanceof NeuRecipe.CraftingRecipe crafting) {
                packRecipe(packer, crafting);
            } else {
                packer.packNil();
            }

            packer.packString("recipes");
            if (item.recipes() != null) {
                packer.packArrayHeader(item.recipes().size());
                for (NeuRecipe r : item.recipes()) {
                    packRecipe(packer, r);
                }
            } else {
                packer.packNil();
            }

            packer.packString("slayerReq");
            if (item.slayerReq() != null) {
                packer.packString(item.slayerReq());
            } else {
                packer.packNil();
            }

            packer.packString("vanilla");
            packer.packBoolean(item.vanilla());
            packer.packString("island");
            packer.packString(item.island());
            packer.packString("x");
            packer.packInt(item.x());
            packer.packString("y");
            packer.packInt(item.y());
            packer.packString("z");
            packer.packInt(item.z());
        }
    }

    public static List<NeuItem> unpackItems(MessageUnpacker unpacker, int expectedCount) throws IOException {
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
                    case "lore" -> lore = CodecUtil.unpackStringList(unpacker);
                    case "damage" -> damage = unpacker.unpackInt();
                    case "clickCommand" -> clickCommand = unpacker.unpackString();
                    case "craftText" -> craftText = unpacker.unpackString();
                    case "infoType" -> infoType = unpacker.unpackString();
                    case "info" -> info = CodecUtil.unpackStringList(unpacker);
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

    // ---- recipes ----

    static void packRecipe(MessagePacker packer, NeuRecipe recipe) throws IOException {
        switch (recipe) {
            case NeuRecipe.CraftingRecipe c -> {
                packer.packMapHeader(3 + c.grid().size());
                packer.packString("_type");
                packer.packString("crafting");
                packer.packString("count");
                packer.packInt(c.count());
                packer.packString("overrideOutputId");
                packer.packString(c.overrideOutputId());
                for (Map.Entry<String, String> slot : c.grid().entrySet()) {
                    packer.packString(slot.getKey());
                    packer.packString(slot.getValue());
                }
            }
            case NeuRecipe.ForgeRecipe f -> {
                packer.packMapHeader(5);
                packer.packString("_type");
                packer.packString("forge");
                packer.packString("count");
                packer.packInt(f.count());
                packer.packString("overrideOutputId");
                packer.packString(f.overrideOutputId());
                packer.packString("duration");
                packer.packInt(f.duration());
                packer.packString("inputs");
                CodecUtil.packStringCollection(packer, f.inputs());
            }
            case NeuRecipe.KatGradeRecipe k -> {
                packer.packMapHeader(6);
                packer.packString("_type");
                packer.packString("katgrade");
                packer.packString("coins");
                packer.packInt(k.coins());
                packer.packString("time");
                packer.packInt(k.time());
                packer.packString("input");
                packer.packString(k.input());
                packer.packString("output");
                packer.packString(k.output());
                packer.packString("items");
                CodecUtil.packStringCollection(packer, k.items());
            }
            case NeuRecipe.NpcShopRecipe n -> {
                packer.packMapHeader(4);
                packer.packString("_type");
                packer.packString("npc_shop");
                packer.packString("npc");
                packer.packString(n.npc());
                packer.packString("result");
                packer.packString(n.result());
                packer.packString("cost");
                packer.packArrayHeader(n.costs().size());
                for (NeuRecipe.NpcShopRecipe.Cost cost : n.costs()) {
                    packer.packMapHeader(2);
                    packer.packString("item");
                    packer.packString(cost.item());
                    packer.packString("cost");
                    packer.packInt(cost.cost());
                }
            }
            case NeuRecipe.DropsRecipe d -> {
                packer.packMapHeader(10);
                packer.packString("_type");
                packer.packString("drops");
                packer.packString("name");
                packer.packString(d.name());
                packer.packString("render");
                packer.packString(d.render());
                packer.packString("level");
                packer.packInt(d.level());
                packer.packString("xp");
                packer.packInt(d.xp());
                packer.packString("combat_xp");
                packer.packInt(d.combatXp());
                packer.packString("coins");
                packer.packInt(d.coins());
                packer.packString("panorama");
                packer.packString(d.panorama());
                packer.packString("extra");
                CodecUtil.packStringCollection(packer, d.extra());
                packer.packString("drops");
                packer.packArrayHeader(d.drops().size());
                for (NeuRecipe.DropsRecipe.Drop drop : d.drops()) {
                    packer.packMapHeader(3);
                    packer.packString("id");
                    packer.packString(drop.id());
                    packer.packString("chance");
                    packer.packString(drop.chance());
                    packer.packString("extra");
                    CodecUtil.packStringCollection(packer, drop.extra());
                }
            }
            case NeuRecipe.TradeRecipe t -> {
                packer.packMapHeader(6);
                packer.packString("_type");
                packer.packString("trade");
                packer.packString("cost");
                packer.packString(t.cost());
                packer.packString("result");
                packer.packString(t.result());
                packer.packString("count");
                packer.packInt(t.count());
                packer.packString("min");
                packer.packInt(t.min());
                packer.packString("max");
                packer.packInt(t.max());
            }
        }
    }

    static NeuRecipe unpackRecipe(MessageUnpacker unpacker) throws IOException {
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
                    gridOf(raw),
                    raw.containsKey("count") ? raw.get("count").asIntegerValue().asInt() : 1,
                    raw.containsKey("overrideOutputId") ? raw.get("overrideOutputId").asStringValue().asString() : ""
            );
            case "forge" -> new NeuRecipe.ForgeRecipe(
                    raw.containsKey("inputs") ? stringListOf(raw.get("inputs")) : Collections.emptyList(),
                    raw.containsKey("count") ? raw.get("count").asIntegerValue().asInt() : 1,
                    raw.containsKey("overrideOutputId") ? raw.get("overrideOutputId").asStringValue().asString() : "",
                    raw.containsKey("duration") ? raw.get("duration").asIntegerValue().asInt() : 0
            );
            case "katgrade" -> new NeuRecipe.KatGradeRecipe(
                    raw.containsKey("coins") ? raw.get("coins").asIntegerValue().asInt() : 0,
                    raw.containsKey("time") ? raw.get("time").asIntegerValue().asInt() : 0,
                    raw.containsKey("input") ? raw.get("input").asStringValue().asString() : "",
                    raw.containsKey("output") ? raw.get("output").asStringValue().asString() : "",
                    raw.containsKey("items") ? stringListOf(raw.get("items")) : Collections.emptyList()
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
                        List<String> extra = Collections.emptyList();
                        for (Map.Entry<Value, Value> e : dm.entrySet()) {
                            String k = e.getKey().asStringValue().asString();
                            if (k.equals("id")) id = e.getValue().asStringValue().asString();
                            else if (k.equals("chance")) chance = e.getValue().asStringValue().asString();
                            else if (k.equals("extra")) extra = stringListOf(e.getValue());
                        }
                        drops.add(new NeuRecipe.DropsRecipe.Drop(id, chance, extra));
                    }
                }
                String name = raw.containsKey("name") ? raw.get("name").asStringValue().asString() : "";
                String render = raw.containsKey("render") ? raw.get("render").asStringValue().asString() : "";
                int level = raw.containsKey("level") ? raw.get("level").asIntegerValue().asInt() : -1;
                int xp = raw.containsKey("xp") ? raw.get("xp").asIntegerValue().asInt() : -1;
                int combatXp = raw.containsKey("combat_xp") ? raw.get("combat_xp").asIntegerValue().asInt() : -1;
                int coins = raw.containsKey("coins") ? raw.get("coins").asIntegerValue().asInt() : -1;
                String panorama = raw.containsKey("panorama")
                        ? raw.get("panorama").asStringValue().asString() : "";
                List<String> extra = raw.containsKey("extra")
                        ? stringListOf(raw.get("extra")) : Collections.emptyList();
                yield new NeuRecipe.DropsRecipe(name, render, level, xp, combatXp, coins,
                        panorama, extra, drops);
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

    static List<NeuRecipe> unpackRecipeList(MessageUnpacker unpacker) throws IOException {
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

    /**
     * Crafting slots are the leftover string fields of the recipe map; the
     * metadata keys must be excluded or they masquerade as grid slots.
     */
    private static Map<String, String> gridOf(Map<String, Value> raw) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, Value> e : raw.entrySet()) {
            if (e.getKey().equals("count") || e.getKey().equals("overrideOutputId")) continue;
            if (!e.getValue().isNilValue() && e.getValue().isStringValue()) {
                map.put(e.getKey(), e.getValue().asStringValue().asString());
            }
        }
        return map;
    }

    private static List<String> stringListOf(Value value) {
        List<String> list = new ArrayList<>();
        for (Value v : value.asArrayValue()) {
            list.add(v.asStringValue().asString());
        }
        return list;
    }
}
