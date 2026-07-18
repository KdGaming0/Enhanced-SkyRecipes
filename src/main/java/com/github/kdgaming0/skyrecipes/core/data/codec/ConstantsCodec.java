package com.github.kdgaming0.skyrecipes.core.data.codec;

import com.github.kdgaming0.skyrecipes.core.mob.MobRenderDefinition;
import com.github.kdgaming0.skyrecipes.core.model.AttributeShardData;
import com.github.kdgaming0.skyrecipes.core.model.EssenceUpgradeData;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeData;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeStoneData;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MessagePack codec for the constants section of the binary cache.
 *
 * <p>Each section's pack and unpack methods sit next to each other so a format
 * change is always edited as a pair. The binary layout must stay byte-identical
 * between {@code pack} and {@code unpack}; bump
 * {@code BinaryDataCompiler.SCHEMA_VERSION} when it changes.</p>
 */
public final class ConstantsCodec {

    private ConstantsCodec() {
    }

    public static void pack(MessagePacker packer, ConstantsRegistry constants) throws IOException {
        packer.packMapHeader(12);

        packer.packString("parents");
        packParents(packer, constants.getAllParents());

        packer.packString("essenceCosts");
        packEssenceCosts(packer, constants.getAllEssenceCosts());

        packer.packString("bazaarItems");
        CodecUtil.packStringCollection(packer, constants.getBazaarItems());

        packer.packString("museum");
        CodecUtil.packStringStringMap(packer, constants.getAllMuseumCategories());

        packer.packString("museumChildren");
        CodecUtil.packStringStringMap(packer, constants.getMuseumChildren());

        packer.packString("reforges");
        packReforges(packer, constants.getAllReforges());

        packer.packString("reforgeStones");
        packReforgeStones(packer, constants.getAllReforgeStones());

        packer.packString("knownStats");
        CodecUtil.packStringCollection(packer, constants.getKnownStats());

        packer.packString("reforgeNameToStone");
        CodecUtil.packStringStringMap(packer, constants.getReforgeNameToStone());

        packer.packString("mobDefinitions");
        packMobDefinitions(packer, constants.getAllMobDefinitions());

        packer.packString("mobSkins");
        packMobSkins(packer, constants.getAllMobSkins());

        packer.packString("attributeShards");
        packAttributeShards(packer, constants.getAllAttributeShards());
    }

    public static ConstantsRegistry unpack(MessageUnpacker unpacker) throws IOException {
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
                case "parents" -> unpackParents(unpacker, parents);
                case "essenceCosts" -> unpackEssenceCosts(unpacker, essenceCosts);
                case "bazaarItems" -> bazaarItems.addAll(CodecUtil.unpackStringList(unpacker));
                case "museum" -> museumCategories.putAll(CodecUtil.unpackStringStringMap(unpacker));
                case "museumChildren" -> museumChildren.putAll(CodecUtil.unpackStringStringMap(unpacker));
                case "reforges" -> unpackReforges(unpacker, reforges);
                case "reforgeStones" -> unpackReforgeStones(unpacker, reforgeStones);
                case "knownStats" -> knownStats.addAll(CodecUtil.unpackStringList(unpacker));
                case "reforgeNameToStone" -> reforgeNameToStone.putAll(CodecUtil.unpackStringStringMap(unpacker));
                case "mobDefinitions" -> unpackMobDefinitions(unpacker, mobDefinitions);
                case "mobSkins" -> unpackMobSkins(unpacker, mobSkins);
                case "attributeShards" -> unpackAttributeShards(unpacker, attributeShards);
                default -> unpacker.skipValue();
            }
        }
        return new ConstantsRegistry(parents, essenceCosts, bazaarItems, museumCategories,
                reforges, reforgeStones, knownStats, reforgeNameToStone,
                mobDefinitions, mobSkins, museumChildren, attributeShards);
    }

    // ---- parents ----

    private static void packParents(MessagePacker packer, Map<String, List<String>> parents) throws IOException {
        packer.packMapHeader(parents.size());
        for (Map.Entry<String, List<String>> e : parents.entrySet()) {
            packer.packString(e.getKey());
            CodecUtil.packStringCollection(packer, e.getValue());
        }
    }

    private static void unpackParents(MessageUnpacker unpacker, Map<String, List<String>> out) throws IOException {
        int size = unpacker.unpackMapHeader();
        for (int i = 0; i < size; i++) {
            String parent = unpacker.unpackString();
            out.put(parent, CodecUtil.unpackStringList(unpacker));
        }
    }

    // ---- essenceCosts ----

    private static void packEssenceCosts(MessagePacker packer, Map<String, EssenceUpgradeData> essenceCosts) throws IOException {
        packer.packMapHeader(essenceCosts.size());
        for (Map.Entry<String, EssenceUpgradeData> e : essenceCosts.entrySet()) {
            packer.packString(e.getKey());
            EssenceUpgradeData data = e.getValue();
            int mapSize = 1 + data.costsPerStar().size();
            if (!data.extraItemsPerStar().isEmpty()) mapSize++;
            packer.packMapHeader(mapSize);
            packer.packString("type");
            packer.packString(data.essenceType());
            for (Map.Entry<Integer, Integer> ce : data.costsPerStar().entrySet()) {
                packer.packString(String.valueOf(ce.getKey()));
                packer.packInt(ce.getValue());
            }
            if (!data.extraItemsPerStar().isEmpty()) {
                packer.packString("items");
                packer.packMapHeader(data.extraItemsPerStar().size());
                for (Map.Entry<Integer, List<String>> ie : data.extraItemsPerStar().entrySet()) {
                    packer.packString(String.valueOf(ie.getKey()));
                    CodecUtil.packStringCollection(packer, ie.getValue());
                }
            }
        }
    }

    private static void unpackEssenceCosts(MessageUnpacker unpacker, Map<String, EssenceUpgradeData> out) throws IOException {
        int size = unpacker.unpackMapHeader();
        for (int i = 0; i < size; i++) {
            String itemName = unpacker.unpackString();
            int mapSize = unpacker.unpackMapHeader();
            String essenceType = "";
            Map<Integer, Integer> costs = new LinkedHashMap<>();
            Map<Integer, List<String>> extraItems = new LinkedHashMap<>();
            for (int k = 0; k < mapSize; k++) {
                String key = unpacker.unpackString();
                if (key.equals("type")) {
                    essenceType = unpacker.unpackString();
                } else if (key.equals("items")) {
                    int xsize = unpacker.unpackMapHeader();
                    for (int m = 0; m < xsize; m++) {
                        int tier = Integer.parseInt(unpacker.unpackString());
                        extraItems.put(tier, CodecUtil.unpackStringList(unpacker));
                    }
                } else {
                    try {
                        costs.put(Integer.parseInt(key), unpacker.unpackInt());
                    } catch (NumberFormatException e) {
                        unpacker.skipValue();
                    }
                }
            }
            out.put(itemName, new EssenceUpgradeData(essenceType, costs, extraItems));
        }
    }

    // ---- reforges ----

    private static void packReforges(MessagePacker packer, Map<String, ReforgeData> reforges) throws IOException {
        packer.packMapHeader(reforges.size());
        for (Map.Entry<String, ReforgeData> e : reforges.entrySet()) {
            packer.packString(e.getKey());
            ReforgeData d = e.getValue();
            int mapSize = 3;
            if (!d.statsPerRarity().isEmpty()) mapSize++;
            if (!d.reforgeAbility().isEmpty()) mapSize++;
            if (!d.reforgeCosts().isEmpty()) mapSize++;
            packer.packMapHeader(mapSize);
            packer.packString("reforgeName");
            packer.packString(d.reforgeName());
            packer.packString("itemTypes");
            packer.packString(d.itemTypes());
            packer.packString("requiredRarities");
            CodecUtil.packStringCollection(packer, d.requiredRarities());
            if (!d.statsPerRarity().isEmpty()) {
                packer.packString("reforgeStats");
                packStatsPerRarity(packer, d.statsPerRarity());
            }
            if (!d.reforgeAbility().isEmpty()) {
                packer.packString("reforgeAbility");
                CodecUtil.packStringStringMap(packer, d.reforgeAbility());
            }
            if (!d.reforgeCosts().isEmpty()) {
                packer.packString("reforgeCosts");
                packCosts(packer, d.reforgeCosts());
            }
        }
    }

    private static void unpackReforges(MessageUnpacker unpacker, Map<String, ReforgeData> out) throws IOException {
        int size = unpacker.unpackMapHeader();
        for (int i = 0; i < size; i++) {
            String name = unpacker.unpackString();
            int mapSize = unpacker.unpackMapHeader();
            String reforgeName = "";
            String itemTypes = "";
            List<String> requiredRarities = new ArrayList<>();
            Map<String, Map<String, Number>> stats = new LinkedHashMap<>();
            Map<String, String> ability = new LinkedHashMap<>();
            Map<String, Number> costs = new LinkedHashMap<>();
            for (int k = 0; k < mapSize; k++) {
                String key = unpacker.unpackString();
                switch (key) {
                    case "reforgeName" -> reforgeName = unpacker.unpackString();
                    case "itemTypes" -> itemTypes = unpacker.unpackString();
                    case "requiredRarities" -> requiredRarities = CodecUtil.unpackStringList(unpacker);
                    case "reforgeStats" -> stats = CodecUtil.unpackStringNumberMapMap(unpacker);
                    case "reforgeAbility" -> ability = CodecUtil.unpackStringStringMap(unpacker);
                    case "reforgeCosts" -> costs = CodecUtil.unpackStringNumberMap(unpacker);
                    default -> unpacker.skipValue();
                }
            }
            out.put(name, new ReforgeData(reforgeName, itemTypes, requiredRarities, stats, ability, costs));
        }
    }

    // ---- reforgeStones ----

    private static void packReforgeStones(MessagePacker packer, Map<String, ReforgeStoneData> reforgeStones) throws IOException {
        packer.packMapHeader(reforgeStones.size());
        for (Map.Entry<String, ReforgeStoneData> e : reforgeStones.entrySet()) {
            packer.packString(e.getKey());
            ReforgeStoneData d = e.getValue();
            int mapSize = 5;
            if (!d.reforgeAbility().isEmpty()) mapSize++;
            if (!d.reforgeCosts().isEmpty()) mapSize++;
            if (!d.reforgeStats().isEmpty()) mapSize++;
            packer.packMapHeader(mapSize);
            packer.packString("internalName");
            packer.packString(d.internalName());
            packer.packString("reforgeName");
            packer.packString(d.reforgeName());
            packer.packString("reforgeType");
            packer.packString(d.reforgeType());
            packer.packString("itemTypes");
            packer.packString(d.itemTypes());
            packer.packString("requiredRarities");
            CodecUtil.packStringCollection(packer, d.requiredRarities());
            if (!d.reforgeAbility().isEmpty()) {
                packer.packString("reforgeAbility");
                CodecUtil.packStringStringMap(packer, d.reforgeAbility());
            }
            if (!d.reforgeCosts().isEmpty()) {
                packer.packString("reforgeCosts");
                packCosts(packer, d.reforgeCosts());
            }
            if (!d.reforgeStats().isEmpty()) {
                packer.packString("reforgeStats");
                packStatsPerRarity(packer, d.reforgeStats());
            }
        }
    }

    private static void unpackReforgeStones(MessageUnpacker unpacker, Map<String, ReforgeStoneData> out) throws IOException {
        int size = unpacker.unpackMapHeader();
        for (int i = 0; i < size; i++) {
            String name = unpacker.unpackString();
            int mapSize = unpacker.unpackMapHeader();
            String internalName = "";
            String reforgeName = "";
            String reforgeType = "";
            String itemTypes = "";
            List<String> requiredRarities = new ArrayList<>();
            Map<String, String> ability = new LinkedHashMap<>();
            Map<String, Number> costs = new LinkedHashMap<>();
            Map<String, Map<String, Number>> stats = new LinkedHashMap<>();
            for (int k = 0; k < mapSize; k++) {
                String key = unpacker.unpackString();
                switch (key) {
                    case "internalName" -> internalName = unpacker.unpackString();
                    case "reforgeName" -> reforgeName = unpacker.unpackString();
                    case "reforgeType" -> reforgeType = unpacker.unpackString();
                    case "itemTypes" -> itemTypes = unpacker.unpackString();
                    case "requiredRarities" -> requiredRarities = CodecUtil.unpackStringList(unpacker);
                    case "reforgeAbility" -> ability = CodecUtil.unpackStringStringMap(unpacker);
                    case "reforgeCosts" -> costs = CodecUtil.unpackStringNumberMap(unpacker);
                    case "reforgeStats" -> stats = CodecUtil.unpackStringNumberMapMap(unpacker);
                    default -> unpacker.skipValue();
                }
            }
            out.put(name, new ReforgeStoneData(
                    internalName, reforgeName, reforgeType, itemTypes, requiredRarities, ability, costs, stats
            ));
        }
    }

    /** Rarity → (stat → value); values are packed as doubles and unpack as {@link Double}. */
    private static void packStatsPerRarity(MessagePacker packer, Map<String, Map<String, Number>> stats) throws IOException {
        packer.packMapHeader(stats.size());
        for (Map.Entry<String, Map<String, Number>> se : stats.entrySet()) {
            packer.packString(se.getKey());
            packer.packMapHeader(se.getValue().size());
            for (Map.Entry<String, Number> stat : se.getValue().entrySet()) {
                packer.packString(stat.getKey());
                packer.packDouble(stat.getValue().doubleValue());
            }
        }
    }

    /** Costs are packed as ints and unpack as {@link Integer}. */
    private static void packCosts(MessagePacker packer, Map<String, Number> costs) throws IOException {
        packer.packMapHeader(costs.size());
        for (Map.Entry<String, Number> ce : costs.entrySet()) {
            packer.packString(ce.getKey());
            packer.packInt(ce.getValue().intValue());
        }
    }

    // ---- mobDefinitions ----

    private static void packMobDefinitions(MessagePacker packer, Map<String, MobRenderDefinition> mobDefinitions) throws IOException {
        packer.packMapHeader(mobDefinitions.size());
        for (Map.Entry<String, MobRenderDefinition> e : mobDefinitions.entrySet()) {
            packer.packString(e.getKey());
            packMobRenderDefinition(packer, e.getValue());
        }
    }

    private static void unpackMobDefinitions(MessageUnpacker unpacker, Map<String, MobRenderDefinition> out) throws IOException {
        int size = unpacker.unpackMapHeader();
        for (int i = 0; i < size; i++) {
            String ref = unpacker.unpackString();
            MobRenderDefinition def = unpackMobRenderDefinition(unpacker);
            if (def != null) {
                out.put(ref, def);
            }
        }
    }

    private static void packMobRenderDefinition(MessagePacker packer, MobRenderDefinition d) throws IOException {
        int mapSize = 1;
        if (d.horseKind() != null) mapSize++;
        if (d.skinPath() != null) mapSize++;
        if (d.helmetItemId() != null) mapSize++;
        if (d.rider() != null) mapSize++;
        packer.packMapHeader(mapSize);
        packer.packString("entityKind");
        packer.packString(d.entityKind());
        if (d.horseKind() != null) {
            packer.packString("horseKind");
            packer.packString(d.horseKind());
        }
        if (d.skinPath() != null) {
            packer.packString("skinPath");
            packer.packString(d.skinPath());
        }
        if (d.helmetItemId() != null) {
            packer.packString("helmetItemId");
            packer.packString(d.helmetItemId());
        }
        if (d.rider() != null) {
            packer.packString("rider");
            packMobRenderDefinition(packer, d.rider());
        }
    }

    private static MobRenderDefinition unpackMobRenderDefinition(MessageUnpacker unpacker) throws IOException {
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

    // ---- mobSkins ----

    private static void packMobSkins(MessagePacker packer, Map<String, byte[]> mobSkins) throws IOException {
        packer.packMapHeader(mobSkins.size());
        for (Map.Entry<String, byte[]> e : mobSkins.entrySet()) {
            packer.packString(e.getKey());
            packer.packBinaryHeader(e.getValue().length);
            packer.addPayload(e.getValue());
        }
    }

    private static void unpackMobSkins(MessageUnpacker unpacker, Map<String, byte[]> out) throws IOException {
        int size = unpacker.unpackMapHeader();
        for (int i = 0; i < size; i++) {
            String path = unpacker.unpackString();
            int binLen = unpacker.unpackBinaryHeader();
            out.put(path, unpacker.readPayload(binLen));
        }
    }

    // ---- attributeShards ----

    private static void packAttributeShards(MessagePacker packer, Map<String, AttributeShardData> attributeShards) throws IOException {
        packer.packMapHeader(attributeShards.size());
        for (Map.Entry<String, AttributeShardData> e : attributeShards.entrySet()) {
            packer.packString(e.getKey());
            AttributeShardData d = e.getValue();
            packer.packMapHeader(7);
            packer.packString("shardName");
            packer.packString(d.shardName());
            packer.packString("abilityName");
            packer.packString(d.abilityName());
            packer.packString("rarity");
            packer.packString(d.rarity());
            packer.packString("alignment");
            packer.packString(d.alignment());
            packer.packString("family");
            CodecUtil.packStringCollection(packer, d.family());
            packer.packString("shardId");
            packer.packString(d.shardId());
            packer.packString("bazaarName");
            packer.packString(d.bazaarName());
        }
    }

    private static void unpackAttributeShards(MessageUnpacker unpacker, Map<String, AttributeShardData> out) throws IOException {
        int size = unpacker.unpackMapHeader();
        for (int i = 0; i < size; i++) {
            String internalName = unpacker.unpackString();
            int mapSize = unpacker.unpackMapHeader();
            String shardName = "";
            String abilityName = "";
            String rarity = "";
            String alignment = "";
            List<String> family = Collections.emptyList();
            String shardId = "";
            String bazaarName = "";
            for (int k = 0; k < mapSize; k++) {
                String key = unpacker.unpackString();
                switch (key) {
                    case "shardName" -> shardName = unpacker.unpackString();
                    case "abilityName" -> abilityName = unpacker.unpackString();
                    case "rarity" -> rarity = unpacker.unpackString();
                    case "alignment" -> alignment = unpacker.unpackString();
                    case "family" -> family = CodecUtil.unpackStringList(unpacker);
                    case "shardId" -> shardId = unpacker.unpackString();
                    case "bazaarName" -> bazaarName = unpacker.unpackString();
                    default -> unpacker.skipValue();
                }
            }
            out.put(internalName, new AttributeShardData(
                    internalName, shardName, abilityName, rarity, alignment, family, shardId, bazaarName));
        }
    }
}
