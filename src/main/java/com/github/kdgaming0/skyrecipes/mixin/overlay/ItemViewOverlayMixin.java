package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorPanelRenderer;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorSession;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorSessionOwner;
import com.github.kdgaming0.skyrecipes.client.gui.SearchBarCalculator;
import com.github.kdgaming0.skyrecipes.client.gui.SearchSuggestionController;
import com.github.kdgaming0.skyrecipes.client.gui.SearchSuggestionState;
import com.github.kdgaming0.skyrecipes.core.search.SearchQuery;
import com.github.kdgaming0.skyrecipes.core.search.SearchQueryParser;
import com.github.kdgaming0.skyrecipes.core.search.SkyblockSearchIndex;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvItemListOverlayAccessor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.CustomDataAccessor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.ItemViewOverlayAccessor;
import com.github.kdgaming0.skyrecipes.rrv.overlay.DimQuadEmitter;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import com.github.kdgaming0.skyrecipes.rrv.recipe.ShardGuiResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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
import org.spongepowered.asm.mixin.Shadow;
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
public class ItemViewOverlayMixin implements CalculatorSessionOwner {

    @Shadow
    private SearchBar searchbar;

    @Unique
    private CalculatorSession skyrecipes$calculatorSession;
    @Unique
    private boolean skyrecipes$restoringCalculatorQuery;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void skyrecipes$initializeCalculatorSession(CallbackInfo ci) {
        skyrecipes$calculatorSession = new CalculatorSession();
    }

    @Unique
    private CalculatorSession skyrecipes$calculatorSession() {
        if (skyrecipes$calculatorSession == null) {
            skyrecipes$calculatorSession = new CalculatorSession();
        }
        return skyrecipes$calculatorSession;
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

    @Override
    public CalculatorSession skyrecipes$getCalculatorSession() {
        return skyrecipes$calculatorSession();
    }

    @Override
    public void skyrecipes$refreshEffectiveQuery() {
        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        skyrecipes$restoringCalculatorQuery = true;
        try {
            ((ItemViewOverlayAccessor) self).skyrecipes$updateQuery(self.getCurrentQuery());
        } finally {
            skyrecipes$restoringCalculatorQuery = false;
        }
        CalculatorSession session = skyrecipes$calculatorSession();
        SearchBarCalculator.Calculation calculation = session.calculation();
        if (session.isActive() && calculation != null) {
            skyrecipes$applyCalculatorSuggestion(calculation);
        }
    }

    @Override
    public void skyrecipes$reconcileCalculatorConfig() {
        CalculatorSession session = skyrecipes$calculatorSession();
        if (!session.needsConfigReconciliation()) {
            return;
        }
        if (!SkyRecipesConfig.calculatorEnabled) {
            skyrecipes$restoreSearchAfterCalculatorConfigChange();
            return;
        }

        SearchBarCalculator.Calculation calculation = session.classifyAndEvaluate(session.input());
        if (!calculation.isCalculator()) {
            skyrecipes$restoreSearchAfterCalculatorConfigChange();
            return;
        }
        session.update(session.input(), ((ItemViewOverlay) (Object) this).getCurrentQuery(), calculation);
        skyrecipes$applyCalculatorSuggestion(calculation);
    }

    @Unique
    private void skyrecipes$restoreSearchAfterCalculatorConfigChange() {
        String restore = skyrecipes$calculatorSession().exitAndRestoreQuery();
        skyrecipes$clearSuggestionState();
        if (searchbar != null && !restore.equals(searchbar.getValue())) {
            searchbar.setValue(restore);
            return;
        }
        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        if (!restore.equals(self.getCurrentQuery())) {
            ((ItemViewOverlayAccessor) self).skyrecipes$updateQuery(restore);
        }
    }

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
    private static DimQuadEmitter skyrecipes$dimEmitter() {
        return new DimQuadEmitter(SLOT_SIZE, DIM_OVERLAY_COLOR);
    }

    /**
     * Blank-query highlighting: dims the empty slots and nothing else.
     *
     * <p>Falling through to RRV here instead of cancelling is what this replaces, and it was
     * expensive for nothing. RRV's {@code renderItemHighlighting} dims a slot when
     * {@code !slot.hasItem() || availableItems.stream().noneMatch(sameBaseItem) &&
     * getTooltipMatch(stack, query) == 0} — so for every slot whose base {@code Item} is not
     * among the ~200 that SkyBlock's ~8,500 stacks are built on, it scans the entire
     * available list <em>and then builds the item's full tooltip</em>, per slot per frame.
     * Spark measured that at ~4% of render-thread time.</p>
     *
     * <p>With an empty query it buys nothing: {@code getTooltipMatch} tests
     * {@code key.startsWith(query)}, which is trivially true for {@code ""}, so it returns 1
     * for any item carrying a translatable tooltip line and the dim condition collapses to
     * {@code !slot.hasItem()}.</p>
     *
     * <p><b>Deliberate deviation:</b> an item with <em>no</em> translatable tooltip line at all
     * (a server-sent stack with a literal name and literal lore, whose base item is also absent
     * from the SkyBlock list) would take {@code getTooltipMatch == 0} and be dimmed by RRV even
     * with an empty search box. That is spurious — an empty query means "no filter", so nothing
     * filled should read as excluded — and it is what the non-blank path already does once every
     * item matches. Dimming empty slots is kept because both RRV and the main path below do it.</p>
     */
    @Unique
    private static void skyrecipes$dimEmptySlotsOnly(AbstractContainerScreen<?> screen,
                                                     GuiGraphicsExtractor guiGraphics) {
        int left = OverlayManager.INSTANCE.currentInfo().leftPos() - 1;
        int top = OverlayManager.INSTANCE.currentInfo().topPos() - 1;

        DimQuadEmitter emitter = skyrecipes$dimEmitter();
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(left, top);
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive() || !slot.isHighlightable()) {
                continue;
            }
            if (slot.getItem().isEmpty()) {
                emitter.add(guiGraphics, slot.x, slot.y);
            }
        }
        emitter.flush(guiGraphics);
        guiGraphics.pose().popMatrix();
    }

    /**
     * Keeps calculator input out of RRV's effective item query, then retains the
     * existing startup deferral for ordinary searches.
     */
    @Inject(method = "updateQuery", at = @At("HEAD"), cancellable = true, remap = false)
    private void skyrecipes$handleCalculatorOrDeferredQuery(String newQuery, CallbackInfo ci) {
        CalculatorSession session = skyrecipes$calculatorSession();

        if (session.isRebuilding() || skyrecipes$restoringCalculatorQuery) {
            if (SkyRecipesClientPlugin.getSearchIndex() == null) {
                ci.cancel();
            }
            return;
        }

        if (SkyRecipesConfig.calculatorEnabled) {
            SearchBarCalculator.Calculation calculation = session.classifyAndEvaluate(newQuery);
            if (calculation.isCalculator()) {
                ItemViewOverlay self = (ItemViewOverlay) (Object) this;
                String currentQuery = self.getCurrentQuery();
                boolean entering = !session.isActive();
                session.update(newQuery, currentQuery, calculation);

                if (entering && SkyRecipesClientPlugin.getSearchIndex() != null
                        && !session.savedSearchQuery().equals(currentQuery)) {
                    skyrecipes$restoringCalculatorQuery = true;
                    try {
                        ((ItemViewOverlayAccessor) self).skyrecipes$updateQuery(session.savedSearchQuery());
                    } finally {
                        skyrecipes$restoringCalculatorQuery = false;
                    }
                }

                skyrecipes$applyCalculatorSuggestion(calculation);
                ci.cancel();
                return;
            }
        }

        if (session.isActive()) {
            session.exitForNormalQuery();
            skyrecipes$clearSuggestionState();
        }
        if (SkyRecipesConfig.calculatorEnabled
                && SkyRecipesConfig.calculatorInputMode == SkyRecipesConfig.CalculatorInputMode.SMART
                && SearchBarCalculator.isSmartPrefix(newQuery)) {
            session.rememberSmartPrefix(((ItemViewOverlay) (Object) this).getCurrentQuery());
        } else {
            session.clearSmartPrefix();
        }

        if (SkyRecipesClientPlugin.getSearchIndex() == null) {
            ci.cancel();
        }
    }

    @Inject(method = "onScreenChanged", at = @At("HEAD"), remap = false)
    private void skyrecipes$beginCalculatorRebuild(AbstractRrvOverlay.InventoryPositionInfo info, CallbackInfo ci) {
        CalculatorSession session = skyrecipes$calculatorSession();
        if (session.isActive()) {
            session.setRebuilding(true);
            session.invalidatePresentation();
        }
    }

    @Inject(method = "onScreenChanged", at = @At("TAIL"), remap = false)
    private void skyrecipes$restoreCalculatorAfterRebuild(AbstractRrvOverlay.InventoryPositionInfo info, CallbackInfo ci) {
        CalculatorSession session = skyrecipes$calculatorSession();
        if (!session.isRebuilding()) {
            return;
        }
        session.setRebuilding(false);
        if (!SkyRecipesConfig.calculatorEnabled) {
            skyrecipes$restoreSearchAfterCalculatorConfigChange();
            return;
        }

        String retainedInput = session.input();
        SearchBarCalculator.Calculation calculation = session.classifyAndEvaluate(retainedInput);
        if (!calculation.isCalculator()) {
            skyrecipes$restoreSearchAfterCalculatorConfigChange();
            return;
        }
        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        session.update(retainedInput, self.getCurrentQuery(), calculation);
        if (SkyRecipesClientPlugin.getSearchIndex() != null
                && !session.savedSearchQuery().equals(self.getCurrentQuery())) {
            skyrecipes$restoringCalculatorQuery = true;
            try {
                ((ItemViewOverlayAccessor) self).skyrecipes$updateQuery(session.savedSearchQuery());
            } finally {
                skyrecipes$restoringCalculatorQuery = false;
            }
        }
        if (searchbar != null && !retainedInput.equals(searchbar.getValue())) {
            searchbar.setValue(retainedInput);
        }
        SearchBarCalculator.Calculation current = session.calculation();
        if (current != null) {
            skyrecipes$applyCalculatorSuggestion(current);
        }
    }

    @Unique
    private void skyrecipes$applyCalculatorSuggestion(SearchBarCalculator.Calculation calculation) {
        if (searchbar == null) {
            skyrecipes$clearSuggestionState();
            return;
        }
        CalculatorPanelRenderer.syncSuggestion(
                searchbar, skyrecipes$calculatorSession(), calculation);
    }

    @Unique
    private void skyrecipes$clearSuggestionState() {
        if (searchbar == null) {
            SearchSuggestionState.clear();
        } else {
            SearchSuggestionController.clear(searchbar);
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
            Screen rawScreen,
            GuiGraphicsExtractor guiGraphics,
            int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {

        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        if (!self.isItemFilterMode()) {
            return;
        }

        // Nothing to dim without menu slots; RRV's own body no-ops here too.
        if (!(rawScreen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        String query = self.getCurrentQuery();
        if (query == null || query.isBlank()) {
            skyrecipes$dimEmptySlotsOnly(screen, guiGraphics);
            ci.cancel();
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

        DimQuadEmitter emitter = skyrecipes$dimEmitter();
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(left, top);

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive() || !slot.isHighlightable()) {
                continue;
            }

            if (slot.getItem().isEmpty()) {
                emitter.add(guiGraphics, slot.x, slot.y);
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
                if (slotId == null) {
                    // Shards in the Hunting Box / Attribute Menu / fusion GUIs are display-only
                    // stacks with no id; recover it so they match like any other SkyBlock item.
                    slotId = ShardGuiResolver.resolve(slotStack, screen);
                }

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
                emitter.add(guiGraphics, slot.x, slot.y);
            }
        }

        emitter.flush(guiGraphics);
        guiGraphics.pose().popMatrix();
        ci.cancel();
    }
}
