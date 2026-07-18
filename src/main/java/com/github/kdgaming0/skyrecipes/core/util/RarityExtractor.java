package com.github.kdgaming0.skyrecipes.core.util;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockRarity;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Extracts {@link SkyblockRarity} from {@link ItemStack}s and {@link NeuItem}s.
 *
 * <p>For stacks, prefers the stack's own lore so runtime upgrades
 * (recombobulated items) report their live rarity. Falls back to the
 * canonical {@link NeuItem} looked up via {@link SkyblockIdExtractor}.</p>
 */
public final class RarityExtractor {

    private RarityExtractor() {
    }

    /**
     * Extract rarity from an {@link ItemStack}.
     *
     * @param stack the stack to inspect
     * @return the rarity, or {@link SkyblockRarity#COMMON} if unknown
     */
    public static SkyblockRarity extract(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SkyblockRarity.COMMON;
        }

        // Live lore first: reflects recombobulator and other runtime upgrades.
        // Scan bottom-up — the rarity line is normally last, but menu screens
        // (auction house, bazaar) append extra lines below it.
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            var lines = lore.lines();
            for (int i = lines.size() - 1; i >= 0; i--) {
                String text = lines.get(i).getString();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                SkyblockRarity rarity = SkyblockRarity.fromLoreOrNull(text);
                if (rarity != null) {
                    return rarity;
                }
            }
        }

        // Fallback: canonical NeuItem rarity
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry != null) {
            String id = SkyblockIdExtractor.extract(stack);
            if (id != null) {
                NeuItem item = registry.getOrNull(id);
                if (item != null) {
                    return extract(item);
                }
            }
        }

        return SkyblockRarity.COMMON;
    }

    /**
     * Extract rarity from a {@link NeuItem} using its lore.
     *
     * @param item the NEU item definition
     * @return the rarity, or {@link SkyblockRarity#COMMON} if the item has no lore
     */
    public static SkyblockRarity extract(NeuItem item) {
        if (item == null || item.lore() == null || item.lore().isEmpty()) {
            return SkyblockRarity.COMMON;
        }
        return SkyblockRarity.fromLore(item.lore().getLast());
    }
}
