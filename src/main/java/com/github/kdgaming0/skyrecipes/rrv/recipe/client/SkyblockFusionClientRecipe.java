package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.fusion.ShardFusionData;
import com.github.kdgaming0.skyrecipes.core.util.LegacyStringParser;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockFusionRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * RRV client recipe showing every fusion combination that produces one attribute
 * shard, paged inside a single card.
 *
 * <p>The output is a real bound slot (hover/tooltip/bookmark work); the input
 * pair is custom-rendered so only valid combinations are ever shown. Pages
 * auto-advance until the user steps manually with the ◀/▶ buttons.</p>
 */
public class SkyblockFusionClientRecipe extends AbstractSkyblockClientRecipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyblockFusionClientRecipe.class);

    private static final String SKYSHARDS_URL = "https://skyshards.com/";
    private static final long AUTO_CYCLE_MS = 1500;

    private static final int TITLE_Y = 6;
    private static final int INPUT_A_X = 20;
    private static final int INPUT_B_X = 55;
    private static final int ITEM_ROW_Y = 20;
    private static final int PAGE_ROW_Y = 42;
    private static final int PAGE_BTN_SIZE = 12;
    private static final int PAGE_LABEL_Y = PAGE_ROW_Y + 2;
    private static final int BUTTON_ROW_Y = 62;
    private static final int BUTTON_HEIGHT = 12;

    private final ShardFusionContext context;
    private final int outputIdx;
    /** Packed pairs (see {@link ShardFusionData}), sorted into display order. */
    private final int[] pairs;
    private final int[] distinctInputs;
    private final String bazaarSearch;

    // -- Transient UI state (reset when the card is opened) -------------------
    private int page;
    private boolean autoCycle = true;
    private long lastAdvanceMs;

    // -- Lazily built slot contents (benign race, see ShardFusionContext) -----
    private volatile SlotContent outputContent;
    private volatile List<SlotContent> ingredientContents;

    public SkyblockFusionClientRecipe(Identifier id, ShardFusionContext context,
                                      int outputIdx, int[] pairs, int[] distinctInputs,
                                      List<String> wikiUrls, String bazaarSearch) {
        super(id, wikiUrls);
        this.context = context;
        this.outputIdx = outputIdx;
        this.pairs = pairs;
        this.distinctInputs = distinctInputs;
        this.bazaarSearch = bazaarSearch != null ? bazaarSearch : "";
    }

    /** NEU internal name of the produced shard — used for result indexing. */
    public String getOutputInternalName() {
        return context.internalName(outputIdx);
    }

    /** NEU internal names of every distinct input shard — used for usage indexing. */
    public List<String> getInputInternalNames() {
        List<String> names = new ArrayList<>(distinctInputs.length);
        for (int idx : distinctInputs) {
            String name = context.internalName(idx);
            if (name != null) names.add(name);
        }
        return names;
    }

    // -- RRV contract ---------------------------------------------------------

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockFusionRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(0, getOutputContent());
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(getOutputContent());
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> contents = ingredientContents;
        if (contents == null) {
            List<ItemStack> stacks = new ArrayList<>(distinctInputs.length);
            for (int idx : distinctInputs) {
                ItemStack stack = context.stack(idx);
                if (!stack.isEmpty()) stacks.add(stack);
            }
            contents = List.of(SlotContent.of(stacks));
            ingredientContents = contents;
        }
        return contents;
    }

    private SlotContent getOutputContent() {
        SlotContent content = outputContent;
        if (content == null) {
            content = SlotContent.of(context.stack(outputIdx));
            outputContent = content;
        }
        return content;
    }

    @Override
    public void initRecipe() {
        super.initRecipe();
        page = 0;
        autoCycle = true;
        lastAdvanceMs = System.currentTimeMillis();
    }

    // -- Rendering ------------------------------------------------------------

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;

        advanceAutoCycle();

        // Title: output shard name (rarity-coloured via its own § codes)
        Component title = LegacyStringParser.parse(context.displayName(outputIdx));
        int titleX = (pos.width() - font.width(title)) / 2;
        graphics.text(font, title, titleX, TITLE_Y, RecipeUiHelper.TEXT_WHITE, true);

        // Current input pair (custom-rendered so only valid combinations appear)
        int packed = pairs[page];
        renderInput(graphics, font, ShardFusionData.pairFirst(packed), INPUT_A_X, pos, mouseX, mouseY);
        renderInput(graphics, font, ShardFusionData.pairSecond(packed), INPUT_B_X, pos, mouseX, mouseY);

        // Paging label: "current / total"
        Component labelComponent = Component.literal((page + 1) + " / " + pairs.length);
        int labelX = (pos.width() - font.width(labelComponent)) / 2;
        graphics.text(font, labelComponent, labelX, PAGE_LABEL_Y, RecipeUiHelper.TEXT_WHITE, true);

        // Tooltip on the paging label: combination count + pointer to SkyShards
        if (mouseY >= PAGE_ROW_Y - 2 && mouseY < PAGE_ROW_Y + PAGE_BTN_SIZE + 2
                && mouseX >= INPUT_A_X + PAGE_BTN_SIZE && mouseX < pos.width() - INPUT_A_X - PAGE_BTN_SIZE) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                            Component.literal("§e" + pairs.length + " §7possible combination" + (pairs.length == 1 ? "" : "s")),
                            Component.literal("§8Scroll to browse combinations"),
                            Component.literal("§8Best fusion path & prices: skyshards.com")),
                    pos.left() + mouseX, pos.top() + mouseY);
        }

        maintainButtons(screen, pos);
    }

    private void renderInput(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                             int shardIdx, int x, RecipePosition pos, int mouseX, int mouseY) {
        ItemStack stack = context.stack(shardIdx);
        if (stack.isEmpty()) return;

        graphics.item(stack, x, ITEM_ROW_Y);
        int amount = context.fuseAmount(shardIdx);
        graphics.itemDecorations(font, stack, x, ITEM_ROW_Y,
                amount > 1 ? String.valueOf(amount) : null);

        if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= ITEM_ROW_Y - 1 && mouseY < ITEM_ROW_Y + 17) {
            graphics.setTooltipForNextFrame(font, stack, pos.left() + mouseX, pos.top() + mouseY);
        }
    }

    @Override
    public void renderOverlay(RecipeViewScreen screen, RecipePosition pos,
                              GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // Output quantity (1 or 2, varies per pair) on the bound output slot —
        // drawn here so it appears above the slot's item sprite.
        int qty = ShardFusionData.pairQty(pairs[page]);
        if (qty > 1) {
            var font = Minecraft.getInstance().font;
            String text = String.valueOf(qty);
            int x = SkyblockFusionRecipeType.OUTPUT_SLOT_X + 17 - font.width(text);
            int y = SkyblockFusionRecipeType.OUTPUT_SLOT_Y + 9;
            graphics.text(font, Component.literal(text), x, y, RecipeUiHelper.TEXT_WHITE, true);
        }
    }

    private void advanceAutoCycle() {
        if (!autoCycle || pairs.length <= 1) return;
        long now = System.currentTimeMillis();
        if (now - lastAdvanceMs >= AUTO_CYCLE_MS) {
            page = (page + 1) % pairs.length;
            lastAdvanceMs = now;
        }
    }

    private void stepPage(int delta) {
        autoCycle = false;
        page = Math.floorMod(page + delta, pairs.length);
    }

    // -- Custom mouse handling (routed in via RecipeViewScreenMixin) ----------

    /**
     * Mouse wheel over the paging row (◀/▶ buttons and the "current / total"
     * label) steps through combinations instead of flipping recipe pages.
     */
    @Override
    public boolean handleScroll(double relX, double relY, double scrollY) {
        if (pairs.length <= 1) return false;
        boolean overPagingRow = relY >= PAGE_ROW_Y - 2 && relY < PAGE_ROW_Y + PAGE_BTN_SIZE + 2
                && relX >= INPUT_A_X && relX < getType().getDisplayWidth() - INPUT_A_X;
        if (!overPagingRow) return false;
        stepPage(scrollY < 0 ? 1 : -1);
        return true;
    }

    /**
     * Clicking a custom-rendered input shard opens that shard's own recipe view
     * (left = recipe, right = usages), matching RRV's bound-slot behaviour.
     */
    @Override
    public boolean handleClick(double relX, double relY, int button) {
        if (button != 0 && button != 1) return false;
        int packed = pairs[page];
        int shardIdx = -1;
        if (isOverInput(relX, relY, INPUT_A_X)) {
            shardIdx = ShardFusionData.pairFirst(packed);
        } else if (isOverInput(relX, relY, INPUT_B_X)) {
            shardIdx = ShardFusionData.pairSecond(packed);
        }
        if (shardIdx < 0) return false;
        ItemStack stack = context.stack(shardIdx);
        if (stack.isEmpty()) return false;
        ItemViewOverlay.INSTANCE.openRecipeView(stack,
                button == 1 ? ActionType.INPUT : ActionType.RESULT);
        return true;
    }

    private static boolean isOverInput(double relX, double relY, int x) {
        return relX >= x - 1 && relX < x + 17 && relY >= ITEM_ROW_Y - 1 && relY < ITEM_ROW_Y + 17;
    }

    // -- Buttons --------------------------------------------------------------

    @Override
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        AbstractWidget sentinel = addWikiButton(screen, pos, 136, BUTTON_ROW_Y);

        if (pairs.length > 1) {
            Button prev = Button.builder(Component.literal("◀"), _ -> stepPage(-1))
                    .pos(pos.left() + INPUT_A_X, pos.top() + PAGE_ROW_Y)
                    .size(PAGE_BTN_SIZE, PAGE_BTN_SIZE)
                    .build();
            screen.addRecipeWidget(prev);
            Button next = Button.builder(Component.literal("▶"), _ -> stepPage(1))
                    .pos(pos.left() + getType().getDisplayWidth() - INPUT_A_X - PAGE_BTN_SIZE,
                            pos.top() + PAGE_ROW_Y)
                    .size(PAGE_BTN_SIZE, PAGE_BTN_SIZE)
                    .build();
            screen.addRecipeWidget(next);
            sentinel = next;
        }

        Button skyShards = Button.builder(Component.literal("SkyShards.com"), _ -> openSkyShards())
                .pos(pos.left() + 4, pos.top() + BUTTON_ROW_Y)
                .size(82, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("See optimal fusion paths with prices calculator on skyshards.com")))
                .build();
        screen.addRecipeWidget(skyShards);
        sentinel = skyShards;

        if (!bazaarSearch.isEmpty()) {
            Button bazaar = Button.builder(Component.literal("Bazaar"), _ -> sendBazaarCommand())
                    .pos(pos.left() + 89, pos.top() + BUTTON_ROW_Y)
                    .size(44, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Buy this Shard on the Bazaar")))
                    .build();
            screen.addRecipeWidget(bazaar);
            sentinel = bazaar;
        }

        return sentinel;
    }

    private void openSkyShards() {
        try {
            Util.getPlatform().openUri(URI.create(SKYSHARDS_URL));
        } catch (Exception e) {
            LOGGER.debug("Failed to open SkyShards URL", e);
        }
    }

    private void sendBazaarCommand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand("bz " + bazaarSearch);
        }
    }
}
