package com.github.kdgaming0.skyrecipes.core.search;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemCategoryResolverTest {

    @Test
    void proseDoesNotMasqueradeAsTypeWords() {
        assertCategory(SkyblockItemCategory.UNKNOWN, "TARANTULA_BROODFATHER_3_BOSS",
                "minecraft:skull", "§7air. Destroy them before they hatch into", "§7minions!");
        assertCategory(SkyblockItemCategory.UNKNOWN, "SVEN_PACKMASTER_2_BOSS",
                "minecraft:skull", "§7Ignores your defense. Very painful.");
        assertCategory(SkyblockItemCategory.UNKNOWN, "SILVERFISH_MONSTER",
                "minecraft:skull", "§7typical behavior when encountered in Skyblock. ");
    }

    @Test
    void onlyActualEnchantedBooksUseTheEnchantedBookCategory() {
        assertCategory(SkyblockItemCategory.ENCHANTED_BOOK, "SHARPNESS;5",
                "minecraft:enchanted_book", "§a§lUNCOMMON");
        assertCategory(SkyblockItemCategory.UNKNOWN, "BOOK",
                "minecraft:book", "§f§lCOMMON");
        assertCategory(SkyblockItemCategory.UNKNOWN, "BOOKSHELF",
                "minecraft:bookshelf", "§f§lCOMMON");
        assertCategory(SkyblockItemCategory.UNKNOWN, "HOT_POTATO_BOOK",
                "minecraft:book", "§5§lEPIC");
        assertCategory(SkyblockItemCategory.UNKNOWN, "MOBY_DUCK",
                "minecraft:book", "§9§lRARE");
    }

    @Test
    void canonicalTypeFootersAndStructuralFallbacksStillWork() {
        assertCategory(SkyblockItemCategory.WEAPON, "TEST_SWORD",
                "minecraft:diamond_sword", "§9§lRARE SWORD");
        assertCategory(SkyblockItemCategory.DUNGEON_ITEM, "DUNGEON_LORE_PAPER",
                "minecraft:book", "§9§lRARE DUNGEON ITEM");
        assertCategory(SkyblockItemCategory.MATERIAL, "ENCHANTED_DIAMOND",
                "minecraft:diamond", "§a§lUNCOMMON");
        assertCategory(SkyblockItemCategory.MINION, "COBBLESTONE_GENERATOR_1",
                "minecraft:skull");
    }

    private static void assertCategory(SkyblockItemCategory expected, String internalName,
                                       String itemId, String... lore) {
        NeuItem item = new NeuItem(
                internalName, itemId, internalName, "", List.of(lore), 0,
                "", "", "", List.of(), null, null, null, false, "", 0, 0, 0);
        assertEquals(expected, ItemCategoryResolver.resolve(item), internalName);
    }
}
