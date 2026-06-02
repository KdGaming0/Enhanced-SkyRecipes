package com.github.kdgaming0.skyrecipes.core.util;

/**
 * Maps legacy Minecraft item IDs (pre-1.13 / pre-1.20) to modern IDs.
 *
 * <p>NEU repository data uses old item IDs and damage values from older Minecraft
 * versions. This utility translates them to IDs that exist in Minecraft 26.1.</p>
 */
public final class LegacyItemIdMapper {

    private LegacyItemIdMapper() {}

    /**
     * Map a legacy item ID and damage value to a modern item ID.
     *
     * @param itemId the NEU item ID (e.g. "minecraft:skull")
     * @param damage the NEU damage/metadata value
     * @return the modern item ID, or the original if no mapping is needed
     */
    public static String map(String itemId, int damage) {
        if (itemId == null || itemId.isEmpty()) {
            return itemId;
        }

        return switch (itemId) {
            case "minecraft:skull" -> switch (damage) {
                case 0 -> "minecraft:skeleton_skull";
                case 1 -> "minecraft:wither_skeleton_skull";
                case 2 -> "minecraft:zombie_head";
                case 3 -> "minecraft:player_head";
                case 4 -> "minecraft:creeper_head";
                case 5 -> "minecraft:dragon_head";
                default -> "minecraft:player_head";
            };
            case "minecraft:dye" -> switch (damage) {
                case 0 -> "minecraft:ink_sac";
                case 1 -> "minecraft:red_dye";
                case 2 -> "minecraft:green_dye";
                case 3 -> "minecraft:cocoa_beans";
                case 4 -> "minecraft:lapis_lazuli";
                case 5 -> "minecraft:purple_dye";
                case 6 -> "minecraft:cyan_dye";
                case 7 -> "minecraft:light_gray_dye";
                case 8 -> "minecraft:gray_dye";
                case 9 -> "minecraft:pink_dye";
                case 10 -> "minecraft:lime_dye";
                case 11 -> "minecraft:yellow_dye";
                case 12 -> "minecraft:light_blue_dye";
                case 13 -> "minecraft:magenta_dye";
                case 14 -> "minecraft:orange_dye";
                case 15 -> "minecraft:bone_meal";
                default -> "minecraft:white_dye";
            };
            case "minecraft:fish" -> switch (damage) {
                case 0 -> "minecraft:cod";
                case 1 -> "minecraft:salmon";
                case 2 -> "minecraft:tropical_fish";
                case 3 -> "minecraft:pufferfish";
                default -> "minecraft:cod";
            };
            case "minecraft:cooked_fish" -> switch (damage) {
                case 0 -> "minecraft:cooked_cod";
                case 1 -> "minecraft:cooked_salmon";
                default -> "minecraft:cooked_cod";
            };
            case "minecraft:red_flower" -> switch (damage) {
                case 0 -> "minecraft:poppy";
                case 1 -> "minecraft:blue_orchid";
                case 2 -> "minecraft:allium";
                case 3 -> "minecraft:azure_bluet";
                case 4 -> "minecraft:red_tulip";
                case 5 -> "minecraft:orange_tulip";
                case 6 -> "minecraft:white_tulip";
                case 7 -> "minecraft:pink_tulip";
                case 8 -> "minecraft:oxeye_daisy";
                default -> "minecraft:poppy";
            };
            case "minecraft:yellow_flower" -> "minecraft:dandelion";
            case "minecraft:log" -> switch (damage) {
                case 0 -> "minecraft:oak_log";
                case 1 -> "minecraft:spruce_log";
                case 2 -> "minecraft:birch_log";
                case 3 -> "minecraft:jungle_log";
                default -> "minecraft:oak_log";
            };
            case "minecraft:log2" -> switch (damage) {
                case 0 -> "minecraft:acacia_log";
                case 1 -> "minecraft:dark_oak_log";
                default -> "minecraft:acacia_log";
            };
            case "minecraft:leaves" -> switch (damage % 4) {
                case 0 -> "minecraft:oak_leaves";
                case 1 -> "minecraft:spruce_leaves";
                case 2 -> "minecraft:birch_leaves";
                case 3 -> "minecraft:jungle_leaves";
                default -> "minecraft:oak_leaves";
            };
            case "minecraft:leaves2" -> switch (damage % 4) {
                case 0 -> "minecraft:acacia_leaves";
                case 1 -> "minecraft:dark_oak_leaves";
                default -> "minecraft:acacia_leaves";
            };
            case "minecraft:planks" -> switch (damage) {
                case 0 -> "minecraft:oak_planks";
                case 1 -> "minecraft:spruce_planks";
                case 2 -> "minecraft:birch_planks";
                case 3 -> "minecraft:jungle_planks";
                case 4 -> "minecraft:acacia_planks";
                case 5 -> "minecraft:dark_oak_planks";
                default -> "minecraft:oak_planks";
            };
            case "minecraft:sapling" -> switch (damage) {
                case 0 -> "minecraft:oak_sapling";
                case 1 -> "minecraft:spruce_sapling";
                case 2 -> "minecraft:birch_sapling";
                case 3 -> "minecraft:jungle_sapling";
                case 4 -> "minecraft:acacia_sapling";
                case 5 -> "minecraft:dark_oak_sapling";
                default -> "minecraft:oak_sapling";
            };
            case "minecraft:wool" -> colorByDamage(damage, "wool");
            case "minecraft:carpet" -> colorByDamage(damage, "carpet");
            case "minecraft:stained_glass" -> colorByDamage(damage, "stained_glass");
            case "minecraft:stained_glass_pane" -> colorByDamage(damage, "stained_glass_pane");
            case "minecraft:stained_hardened_clay" -> colorByDamage(damage, "terracotta");
            case "minecraft:banner" -> colorByDamage(damage, "banner");
            case "minecraft:stone_slab" -> switch (damage) {
                case 0 -> "minecraft:smooth_stone_slab";
                case 1 -> "minecraft:sandstone_slab";
                case 2 -> "minecraft:petrified_oak_slab";
                case 3 -> "minecraft:cobblestone_slab";
                case 4 -> "minecraft:brick_slab";
                case 5 -> "minecraft:stone_brick_slab";
                case 6 -> "minecraft:nether_brick_slab";
                case 7 -> "minecraft:quartz_slab";
                default -> "minecraft:smooth_stone_slab";
            };
            case "minecraft:wooden_slab" -> switch (damage) {
                case 0 -> "minecraft:oak_slab";
                case 1 -> "minecraft:spruce_slab";
                case 2 -> "minecraft:birch_slab";
                case 3 -> "minecraft:jungle_slab";
                case 4 -> "minecraft:acacia_slab";
                case 5 -> "minecraft:dark_oak_slab";
                default -> "minecraft:oak_slab";
            };
            case "minecraft:double_plant" -> switch (damage) {
                case 0 -> "minecraft:sunflower";
                case 1 -> "minecraft:lilac";
                case 2 -> "minecraft:tall_grass";
                case 3 -> "minecraft:large_fern";
                case 4 -> "minecraft:rose_bush";
                case 5 -> "minecraft:peony";
                default -> "minecraft:sunflower";
            };
            case "minecraft:quartz_block" -> switch (damage) {
                case 0 -> "minecraft:quartz_block";
                case 1 -> "minecraft:chiseled_quartz_block";
                case 2 -> "minecraft:quartz_pillar";
                default -> "minecraft:quartz_block";
            };
            case "minecraft:prismarine" -> switch (damage) {
                case 0 -> "minecraft:prismarine";
                case 1 -> "minecraft:prismarine_bricks";
                case 2 -> "minecraft:dark_prismarine";
                default -> "minecraft:prismarine";
            };
            case "minecraft:stonebrick" -> switch (damage) {
                case 0 -> "minecraft:stone_bricks";
                case 1 -> "minecraft:mossy_stone_bricks";
                case 2 -> "minecraft:cracked_stone_bricks";
                case 3 -> "minecraft:chiseled_stone_bricks";
                default -> "minecraft:stone_bricks";
            };
            case "minecraft:anvil" -> switch (damage) {
                case 0 -> "minecraft:anvil";
                case 1 -> "minecraft:chipped_anvil";
                case 2 -> "minecraft:damaged_anvil";
                default -> "minecraft:anvil";
            };
            case "minecraft:coal" -> switch (damage) {
                case 0 -> "minecraft:coal";
                case 1 -> "minecraft:charcoal";
                default -> "minecraft:coal";
            };
            case "minecraft:dirt" -> switch (damage) {
                case 0 -> "minecraft:dirt";
                case 1 -> "minecraft:coarse_dirt";
                case 2 -> "minecraft:podzol";
                default -> "minecraft:dirt";
            };
            case "minecraft:sand" -> switch (damage) {
                case 0 -> "minecraft:sand";
                case 1 -> "minecraft:red_sand";
                default -> "minecraft:sand";
            };
            case "minecraft:sponge" -> switch (damage) {
                case 0 -> "minecraft:sponge";
                case 1 -> "minecraft:wet_sponge";
                default -> "minecraft:sponge";
            };
            case "minecraft:hardened_clay" -> "minecraft:terracotta";
            case "minecraft:netherbrick" -> "minecraft:nether_brick";
            case "minecraft:slime" -> "minecraft:slime_block";
            case "minecraft:wooden_button" -> "minecraft:oak_button";
            case "minecraft:brick_block" -> "minecraft:bricks";
            case "minecraft:fence_gate" -> "minecraft:oak_fence_gate";
            case "minecraft:fence" -> "minecraft:oak_fence";
            case "minecraft:wooden_pressure_plate" -> "minecraft:oak_pressure_plate";
            case "minecraft:snow_layer" -> "minecraft:snow";
            case "minecraft:mob_spawner" -> "minecraft:spawner";
            case "minecraft:wooden_door" -> "minecraft:oak_door";
            case "minecraft:sign" -> "minecraft:oak_sign";
            case "minecraft:quartz_ore" -> "minecraft:nether_quartz_ore";
            case "minecraft:lit_pumpkin" -> "minecraft:jack_o_lantern";
            case "minecraft:tallgrass" -> "minecraft:short_grass";
            case "minecraft:deadbush" -> "minecraft:dead_bush";
            case "minecraft:trapdoor" -> "minecraft:oak_trapdoor";
            case "minecraft:boat" -> "minecraft:oak_boat";
            case "minecraft:reeds" -> "minecraft:sugar_cane";
            case "minecraft:speckled_melon" -> "minecraft:glistering_melon_slice";
            case "minecraft:firework_charge" -> "minecraft:firework_star";
            case "minecraft:noteblock" -> "minecraft:note_block";
            case "minecraft:web" -> "minecraft:cobweb";
            case "minecraft:fireworks" -> "minecraft:firework_rocket";
            case "minecraft:hay_block" -> "minecraft:hay_block";
            case "minecraft:melon_block" -> "minecraft:melon";
            case "minecraft:waterlily" -> "minecraft:lily_pad";
            case "minecraft:golden_rail" -> "minecraft:powered_rail";
            case "minecraft:bed" -> "minecraft:white_bed";
            case "minecraft:grass" -> "minecraft:grass_block";
            case "minecraft:mycel" -> "minecraft:mycelium";
            case "minecraft:record_13" -> "minecraft:music_disc_13";
            case "minecraft:record_cat" -> "minecraft:music_disc_cat";
            case "minecraft:record_blocks" -> "minecraft:music_disc_blocks";
            case "minecraft:record_chirp" -> "minecraft:music_disc_chirp";
            case "minecraft:record_far" -> "minecraft:music_disc_far";
            case "minecraft:record_mall" -> "minecraft:music_disc_mall";
            case "minecraft:record_mellohi" -> "minecraft:music_disc_mellohi";
            case "minecraft:record_stal" -> "minecraft:music_disc_stal";
            case "minecraft:record_strad" -> "minecraft:music_disc_strad";
            case "minecraft:record_ward" -> "minecraft:music_disc_ward";
            case "minecraft:record_11" -> "minecraft:music_disc_11";
            case "minecraft:record_wait" -> "minecraft:music_disc_wait";
            case "minecraft:stone_slab2" -> switch (damage) {
                case 0 -> "minecraft:red_sandstone_slab";
                case 1 -> "minecraft:purpur_slab";
                case 2 -> "minecraft:prismarine_slab";
                case 3 -> "minecraft:prismarine_brick_slab";
                case 4 -> "minecraft:dark_prismarine_slab";
                case 5 -> "minecraft:mossy_cobblestone_slab";
                case 6 -> "minecraft:smooth_sandstone_slab";
                case 7 -> "minecraft:red_nether_brick_slab";
                default -> "minecraft:red_sandstone_slab";
            };
            case "minecraft:monster_egg" -> switch (damage) {
                case 0 -> "minecraft:infested_stone";
                case 1 -> "minecraft:infested_cobblestone";
                case 2 -> "minecraft:infested_stone_bricks";
                case 3 -> "minecraft:infested_mossy_stone_bricks";
                case 4 -> "minecraft:infested_cracked_stone_bricks";
                case 5 -> "minecraft:infested_chiseled_stone_bricks";
                default -> "minecraft:infested_stone";
            };
            case "minecraft:spawn_egg" -> "minecraft:pig_spawn_egg";
            default -> itemId;
        };
    }

    private static String colorByDamage(int damage, String suffix) {
        String color = switch (damage) {
            case 0 -> "white";
            case 1 -> "orange";
            case 2 -> "magenta";
            case 3 -> "light_blue";
            case 4 -> "yellow";
            case 5 -> "lime";
            case 6 -> "pink";
            case 7 -> "gray";
            case 8 -> "light_gray";
            case 9 -> "cyan";
            case 10 -> "purple";
            case 11 -> "blue";
            case 12 -> "brown";
            case 13 -> "green";
            case 14 -> "red";
            case 15 -> "black";
            default -> "white";
        };
        return "minecraft:" + color + "_" + suffix;
    }
}
