package com.github.kdgaming0.skyrecipes.mixin.overlay;

import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvItemListOverlayAccessor;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Replaces RRV's inventory slot highlighting with SkyBlock ID-aware matching.
 *
 * <p>RRV's native implementation compares vanilla {@code Item} types:
 * {@code stack.getItem() == slot.getItem().getItem()}. This breaks for SkyBlock
 * because ~8,000 items share ~200 base items (mostly {@code minecraft:player_head}).
 *
 * <p>This mixin extracts {@code ExtraAttributes.id} from both the filtered result
 * stacks and the inventory slot stacks, enabling exact SkyBlock item matching.
 * Vanilla items without a SkyBlock ID fall back to display-name keyword matching.
 * Enchanted books are checked by enchantment name.</p>
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class ItemViewOverlayMixin {

    /**
     * Defers the first {@code updateQuery} call until SkyRecipes data is ready.
     *
     * <p>If the overlay is opened before {@link SkyRecipesClientPlugin}'s batched injection
     * has finished, {@code getSearchIndex()} returns {@code null}. Running a search at that
     * moment would operate on an empty {@code fullStackList()} (all vanilla items filtered out
     * and no stack-sensitives registered yet), producing an empty item list. Cancelling the
     * query here prevents that flash-of-empty-list. When injection completes,
     * {@code OverlayManager.updateOverlaysAndWidgets(true)} triggers {@code onScreenChanged}
     * again and the query re-runs with the index populated.</p>
     */
    @Inject(method = "updateQuery", at = @At("HEAD"), cancellable = true, remap = false)
    private void skyrecipes$deferQueryUntilReady(String newQuery, CallbackInfo ci) {
        if (SkyRecipesClientPlugin.getSearchIndex() == null) {
            ci.cancel();
        }
    }

    @Unique
    private static final int SLOT_SIZE = 18;
    @Unique
    private static final int DIM_OVERLAY_COLOR = 0x80000000;

    // -- Per-frame cache for filtered item sets --------------------------------
    @Unique
    private static String cachedQuery = null;
    @Unique
    private static Set<String> cachedFilteredIds = Set.of();
    @Unique
    private static Set<String> cachedFilteredVanillaNames = Set.of();

    @Unique
    private static boolean enchantedBookMatches(ItemStack stack, String query) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return false;
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        if (!tag.contains("StoredEnchantments")) return false;

        String lowerQuery = query.toLowerCase();
        var enchantments = tag.getListOrEmpty("StoredEnchantments");
        for (int i = 0; i < enchantments.size(); i++) {
            var entry = enchantments.getCompoundOrEmpty(i);
            String id = entry.getStringOr("id", "").toLowerCase();
            if (id.contains(lowerQuery)) return true;
            // Also check just the enchantment name part
            int colon = id.lastIndexOf(':');
            String name = colon >= 0 ? id.substring(colon + 1) : id;
            if (name.contains(lowerQuery)) return true;
        }
        return false;
    }

    @Unique
    private static boolean vanillaNameMatchesQuery(ItemStack stack, String query) {
        String name = stack.getHoverName().getString().toLowerCase();
        if (name.isBlank()) return false;

        // Simple keyword containment check
        for (String word : query.toLowerCase().split("\\s+")) {
            if (word.length() > 1 && !word.startsWith("%") && !word.contains(":")
                    && !word.contains(">") && !word.contains("<") && !word.contains("=")) {
                if (name.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private static void dimSlot(GuiGraphicsExtractor guiGraphics, Slot slot) {
        guiGraphics.fill(slot.x, slot.y,
                slot.x + SLOT_SIZE, slot.y + SLOT_SIZE,
                DIM_OVERLAY_COLOR);
    }

    @Inject(
            method = "renderItemHighlighting",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void skyrecipes$renderItemHighlighting(
            AbstractContainerScreen<?> screen,
            GuiGraphicsExtractor guiGraphics,
            int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {

        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        if (!self.isItemFilterMode()) {
            return;
        }

        String query = self.getCurrentQuery();
        boolean hasFilter = query != null && !query.isBlank();
        if (!hasFilter) {
            return;
        }

        // Rebuild cached sets only when the query changes
        Set<String> filteredIds;
        Set<String> filteredVanillaNames;
        if (query.equals(cachedQuery)) {
            filteredIds = cachedFilteredIds;
            filteredVanillaNames = cachedFilteredVanillaNames;
        } else {
            filteredIds = new HashSet<>();
            filteredVanillaNames = new HashSet<>();
            for (ItemStack stack : ((AbstractRrvItemListOverlayAccessor) self).skyrecipes$getAvailableItems()) {
                String id = SkyblockIdExtractor.extract(stack);
                if (id != null) {
                    filteredIds.add(id);
                } else {
                    String name = stack.getHoverName().getString().toLowerCase();
                    if (!name.isBlank()) {
                        filteredVanillaNames.add(name);
                    }
                }
            }
            cachedQuery = query;
            cachedFilteredIds = filteredIds;
            cachedFilteredVanillaNames = filteredVanillaNames;
        }

        int left = OverlayManager.INSTANCE.currentInfo().leftPos() - 1;
        int top = OverlayManager.INSTANCE.currentInfo().topPos() - 1;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(left, top);

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive() || !slot.isHighlightable()) {
                continue;
            }

            if (slot.getItem().isEmpty()) {
                dimSlot(guiGraphics, slot);
                continue;
            }

            boolean matched = false;
            ItemStack slotStack = slot.getItem();
            String slotId = SkyblockIdExtractor.extract(slotStack);

            if (slotId != null) {
                // Fast path: exact SkyBlock ID match
                matched = filteredIds.contains(slotId);

                // Fallback: evaluate query directly against the item's tokens
                if (!matched) {
                    var index = SkyRecipesClientPlugin.getSearchIndex();
                    if (index != null) {
                        matched = index.itemMatchesInventoryQuery(slotId, query);
                    }
                }
            } else {
                // Vanilla item fallback
                matched = filteredVanillaNames.contains(slotStack.getHoverName().getString().toLowerCase());

                // Enchanted book: check enchantment name against query keywords
                if (!matched && slotStack.getItem() == Items.ENCHANTED_BOOK) {
                    matched = enchantedBookMatches(slotStack, query);
                }

                // Generic display name contains any query keyword
                if (!matched) {
                    matched = vanillaNameMatchesQuery(slotStack, query);
                }
            }

            if (!matched) {
                dimSlot(guiGraphics, slot);
            }
        }

        guiGraphics.pose().popMatrix();
        ci.cancel();
    }
}
