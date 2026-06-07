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
 * <p>For stacks, prefers looking up the canonical {@link NeuItem} via
 * {@link SkyblockIdExtractor} so the rarity matches NEU data exactly.
 * Falls back to parsing the stack's current lore.</p>
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

        // Fast path: look up canonical NeuItem
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry != null) {
            String id = SkyblockIdExtractor.extract(stack);
            if (id != null) {
                NeuItem item = registry.getByInternalName(id).orElse(null);
                if (item != null) {
                    return extract(item);
                }
            }
        }

        // Fallback: parse lore from the stack itself
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            String text = lore.lines().getLast().getString();
            if (text != null && !text.isEmpty()) {
                return SkyblockRarity.fromLore(text);
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
