package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.core.search.SearchQuery;
import com.github.kdgaming0.skyrecipes.core.search.SearchQueryParser;
import com.github.kdgaming0.skyrecipes.core.search.SkyblockSearchIndex;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvItemListOverlayAccessor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.CustomDataAccessor;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Replaces RRV's inventory slot highlighting with SkyBlock ID-aware matching.
 *
 * <p>RRV's native implementation compares vanilla {@code Item} types:
 * {@code stack.getItem() == slot.getItem().getItem()}. This breaks for SkyBlock
 * because ~8,000 items share ~200 base items (mostly {@code minecraft:player_head}).
 *
 * <p>This mixin extracts {@code ExtraAttributes.id} from both the filtered result
 * stacks and the inventory slot stacks, enabling exact SkyBlock item matching.
 * Slots the id/index paths miss are matched against the live stack's name, lore,
 * and enchant NBT, so enchanted books and items with an enchant applied highlight
 * when the enchant name is searched.</p>
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class ItemViewOverlayMixin {

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
    private static SearchQuery cachedParsedQuery = null;
    @Unique
    private static Object cachedIndexIdentity = null;

    // -- Live-stack matching ----------------------------------------------------
    // The filtered-id and index paths only know the *repo* item, so they miss
    // everything the server adds at runtime: applied enchants, reforges, and
    // enchanted books (Hypixel books all share id ENCHANTED_BOOK — the specific
    // enchant lives in ExtraAttributes/lore, never in the repo entry). This path
    // evaluates keyword/phrase/regex clauses against the live stack's name, lore,
    // and enchant NBT. All three caches are render-thread-only and effectively
    // identity-keyed: ItemStack does not override equals/hashCode (MC 26.1.2), so
    // WeakHashMap compares keys by identity — and its weak keys let stacks from
    // refreshed/closed containers be collected instead of pinning them until a
    // size cap forced a clear-all. liveText survives query changes (NBT doesn't
    // change under one instance); liveMatch is per query.
    @Unique
    private static final Map<ItemStack, String> liveTextCache = new WeakHashMap<>();
    @Unique
    private static final Map<ItemStack, Boolean> liveMatchCache = new WeakHashMap<>();
    // Full per-slot verdict (id extraction + filtered-id lookup + index match + live
    // match) for the current query; cleared alongside liveMatchCache when the query
    // or index changes. Container refreshes deliver new ItemStack instances, so
    // stale entries are never served — the weak keys collect them.
    @Unique
    private static final Map<ItemStack, Boolean> slotMatchCache = new WeakHashMap<>();

    @Unique
    private static boolean liveStackMatches(ItemStack stack, SearchQuery parsed) {
        if (parsed == null) return false;
        // Structured clauses (stats, filters, category, flags) need index data the
        // live stack can't verify; those queries stay on the index path.
        if (!parsed.stats().isEmpty() || !parsed.filters().isEmpty()
                || parsed.categoryPath() != null || !parsed.booleanFlags().isEmpty()) {
            return false;
        }
        Boolean cached = liveMatchCache.get(stack);
        if (cached != null) return cached;

        String text = liveSearchText(stack);
        boolean matched = true;
        for (SearchQuery.KeywordClause kw : parsed.keywords()) {
            if (!text.contains(kw.token())) {
                matched = false;
                break;
            }
        }
        if (matched) {
            for (SearchQuery.PhraseClause phrase : parsed.phrases()) {
                if (!text.contains(phrase.text())) {
                    matched = false;
                    break;
                }
            }
        }
        if (matched) {
            for (SearchQuery.RegexClause regex : parsed.regexes()) {
                if (!SkyblockSearchIndex.regexFindWithinLine(text, regex.pattern())) {
                    matched = false;
                    break;
                }
            }
        }
        liveMatchCache.put(stack, matched);
        return matched;
    }

    /**
     * Lowercased, color-stripped, '\n'-joined searchable text of a live stack:
     * display name, lore lines, enchant names (Hypixel {@code ExtraAttributes.enchantments}
     * keys, legacy {@code StoredEnchantments} entries, and vanilla enchantment components),
     * and the SkyBlock id with separators spaced out.
     */
    @Unique
    private static String liveSearchText(ItemStack stack) {
        String cached = liveTextCache.get(stack);
        if (cached != null) return cached;

        StringBuilder sb = new StringBuilder(128);
        sb.append(TextUtil.stripColorCodes(stack.getHoverName().getString())).append('\n');

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                sb.append(TextUtil.stripColorCodes(line.getString())).append('\n');
            }
        }

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            // Read-only view; copyTag() would deep-copy the tree per stack.
            CompoundTag tag = ((CustomDataAccessor) (Object) data).skyrecipes$getTag();
            CompoundTag extra = tag.getCompound("ExtraAttributes").orElse(null);
            if (extra != null) {
                CompoundTag enchants = extra.getCompound("enchantments").orElse(null);
                if (enchants != null) {
                    for (String key : enchants.keySet()) {
                        sb.append(key.replace('_', ' ')).append('\n');
                    }
                }
            }
            // Through the extractor, not the raw tag: an id like PET or ENCHANTED_BOOK is
            // useless to search, and the expanded name is what the user types.
            String skyblockId = SkyblockIdExtractor.extract(stack);
            if (skyblockId != null) {
                sb.append(skyblockId.replace('_', ' ').replace(';', ' ')).append('\n');
            }
            var stored = tag.getListOrEmpty("StoredEnchantments");
            for (int i = 0; i < stored.size(); i++) {
                String id = stored.getCompoundOrEmpty(i).getStringOr("id", "");
                int colon = id.lastIndexOf(':');
                sb.append((colon >= 0 ? id.substring(colon + 1) : id).replace('_', ' ')).append('\n');
            }
        }

        appendEnchantmentIds(stack.get(DataComponents.ENCHANTMENTS), sb);
        appendEnchantmentIds(stack.get(DataComponents.STORED_ENCHANTMENTS), sb);

        String text = sb.toString().toLowerCase();
        liveTextCache.put(stack, text);
        return text;
    }

    @Unique
    private static void appendEnchantmentIds(ItemEnchantments enchantments, StringBuilder sb) {
        if (enchantments == null || enchantments.isEmpty()) return;
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            holder.unwrapKey().ifPresent(key ->
                    sb.append(key.identifier().getPath().replace('_', ' ')).append('\n'));
        }
    }

    @Unique
    private static void dimSlot(GuiGraphicsExtractor guiGraphics, Slot slot) {
        guiGraphics.fill(slot.x, slot.y,
                slot.x + SLOT_SIZE, slot.y + SLOT_SIZE,
                DIM_OVERLAY_COLOR);
    }

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

    /**
     * Draws a notice in the item panel when the pipeline failed with no data,
     * so the empty list is explained in place and points at the fixes.
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"), remap = false)
    private void skyrecipes$renderPipelineFailureNotice(GuiGraphicsExtractor guiGraphics,
                                                        int mouseX, int mouseY, float partialTicks,
                                                        CallbackInfo ci) {
        if (SkyRecipesClientPlugin.getSearchIndex() != null || !SkyRecipesClientPlugin.isPipelineFailed()) {
            return;
        }
        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        Font font = Minecraft.getInstance().font;
        int centerX = self.checkedX() + self.checkedWidth() / 2;
        int y = self.checkedY() + self.checkedHeight() / 2 - 18;
        int maxWidth = self.checkedWidth();
        drawScaledCenteredLine(guiGraphics, font, Component.literal("SkyBlock data failed to download"),
                centerX, y, 0xFFFF5555, maxWidth);
        drawScaledCenteredLine(guiGraphics, font, Component.literal("See /skyrecipes status for details"),
                centerX, y + 14, 0xFFAAAAAA, maxWidth);
    }

    @Unique
    private static void drawScaledCenteredLine(GuiGraphicsExtractor guiGraphics, Font font,
                                               Component line, int centerX, int y, int color, int maxWidth) {
        float scale = Math.min(1.0F, (maxWidth - 8.0F) / font.width(line));
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(centerX, y);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.centeredText(font, line, 0, 0, color);
        guiGraphics.pose().popMatrix();
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

        // Rebuild cached sets when the query changes or a reload republished the index
        // (same query string against a new index must not serve the stale sets).
        var index = SkyRecipesClientPlugin.getSearchIndex();
        Set<String> filteredIds;
        Set<String> filteredVanillaNames;
        if (query.equals(cachedQuery) && index == cachedIndexIdentity) {
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
            cachedParsedQuery = SearchQueryParser.parse(query);
            cachedIndexIdentity = index;
            liveMatchCache.clear();
            slotMatchCache.clear();
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

            ItemStack slotStack = slot.getItem();

            // The verdict for a (stack instance, query) pair is constant — evaluate
            // once and serve every later frame from the identity cache.
            Boolean cachedVerdict = slotMatchCache.get(slotStack);
            boolean matched;
            if (cachedVerdict != null) {
                matched = cachedVerdict;
            } else {
                String slotId = SkyblockIdExtractor.extract(slotStack);

                if (slotId != null) {
                    // Fast path: exact SkyBlock ID in the filtered item list
                    matched = filteredIds.contains(slotId);

                    // Repo-token fallback: covers structured filters/category queries
                    if (!matched && index != null) {
                        matched = index.itemMatchesInventoryQuery(slotId, cachedParsedQuery);
                    }
                } else {
                    matched = filteredVanillaNames.contains(slotStack.getHoverName().getString().toLowerCase());
                }

                // Live-stack fallback: applied enchants, enchanted books, reforged names —
                // anything the repo entry doesn't know about this specific stack.
                if (!matched) {
                    matched = liveStackMatches(slotStack, cachedParsedQuery);
                }

                slotMatchCache.put(slotStack, matched);
            }

            if (!matched) {
                dimSlot(guiGraphics, slot);
            }
        }

        guiGraphics.pose().popMatrix();
        ci.cancel();
    }
}
