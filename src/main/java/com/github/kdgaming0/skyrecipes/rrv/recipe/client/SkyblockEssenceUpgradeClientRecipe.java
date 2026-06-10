package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;

import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockEssenceUpgradeRecipeType;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Client recipe for SkyBlock essence upgrades.
 *
 * <p>Layout (152×64 custom asset):</p>
 * <ul>
 *   <li>Slot 0 — input item (left, starred at current level)</li>
 *   <li>Slots 1-6 — up to 6 items in 2×3 grid (centre): coin first, then essence, then other extras</li>
 *   <li>Slot 7 — output item (right, starred at next level with stat deltas)</li>
 * </ul>
 *
 * <p>Grid positions (must stay in sync with {@link SkyblockEssenceUpgradeRecipeType#placeSlots}):</p>
 * <pre>
 *   Slot 1  (45,18)   Slot 2  (63,18)   Slot 3  (81,18)
 *   Slot 4  (45,36)   Slot 5  (63,36)   Slot 6  (81,36)
 * </pre>
 */
public class SkyblockEssenceUpgradeClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int HEADER_Y = 3;
    private static final int OVERFLOW_X = 87;
    private static final int OVERFLOW_Y = 44;

    // Grid slot positions for compact-count overlays.
    private static final int[] GRID_X = {45, 63, 81, 45, 63, 81};
    private static final int[] GRID_Y = {18, 18, 18, 36, 36, 36};

    private final ItemStack inputItem;
    private final ItemStack outputItem;
    private final ItemStack essenceStack;
    private final int starLevel;
    private final List<ItemStack> extraItems;
    private final List<Long> extraAmounts;
    private final String displayName;
    private final long coinAmount;
    private final long essenceAmount;

    public SkyblockEssenceUpgradeClientRecipe(Identifier id,
                                              ItemStack inputItem, ItemStack outputItem,
                                              ItemStack essenceStack, int starLevel,
                                              List<ItemStack> extraItems,
                                              List<Long> extraAmounts,
                                              String displayName, long coinAmount,
                                              long essenceAmount) {
        super(id);
        this.inputItem = inputItem;
        this.outputItem = outputItem;
        this.essenceStack = essenceStack;
        this.starLevel = starLevel;
        this.extraItems = List.copyOf(extraItems);
        this.extraAmounts = List.copyOf(extraAmounts);
        this.displayName = displayName != null ? displayName : "";
        this.coinAmount = coinAmount;
        this.essenceAmount = essenceAmount;
    }

    private static void drawCompact(net.minecraft.client.gui.Font font, GuiGraphicsExtractor graphics,
                                    String text, int slotX, int slotY) {
        Component cmp = Component.literal(text);
        int textWidth = font.width(cmp);
        int x = slotX + 17 - textWidth;
        int y = slotY + 9;
        graphics.text(font, cmp, x, y, RecipeUiHelper.TEXT_WHITE, true);
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockEssenceUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(0, SlotContent.of(inputItem));

        int slot = 1;

        // Coin first (top-left of grid)
        if (coinAmount > 0 && !extraItems.isEmpty()) {
            ctx.bindSlot(slot++, SlotContent.of(extraItems.getFirst()));
        }

        // Essence next
        ctx.bindSlot(slot++, SlotContent.of(essenceStack));

        // Remaining extras fill the rest of the grid
        int startIdx = coinAmount > 0 ? 1 : 0;
        for (int i = startIdx; i < extraItems.size() && slot <= 6; i++) {
            ctx.bindSlot(slot++, SlotContent.of(extraItems.get(i)));
        }

        ctx.bindSlot(7, SlotContent.of(outputItem));
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        list.add(SlotContent.of(inputItem));

        if (coinAmount > 0 && !extraItems.isEmpty()) {
            list.add(SlotContent.of(extraItems.getFirst()));
        }
        list.add(SlotContent.of(essenceStack));
        int startIdx = coinAmount > 0 ? 1 : 0;
        for (int i = startIdx; i < extraItems.size(); i++) {
            list.add(SlotContent.of(extraItems.get(i)));
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(SlotContent.of(outputItem));
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;

        // Header — item name first, then stars
        String headerText = displayName + " §6" + "✪".repeat(starLevel);
        Component header = Component.literal(headerText);
        int headerWidth = font.width(header);
        if (headerWidth > pos.width() - 8) {
            String ellipsis = "…";
            int avail = pos.width() - 8 - font.width(Component.literal(" §6" + "✪".repeat(starLevel) + ellipsis));
            String raw = displayName;
            int lo = 0, hi = raw.length();
            while (lo < hi) {
                int mid = (lo + hi + 1) / 2;
                if (font.width(Component.literal(raw.substring(0, mid))) <= avail) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            header = Component.literal(raw.substring(0, lo) + "§r… §6" + "✪".repeat(starLevel));
            headerWidth = font.width(header);
        }
        int headerX = (pos.width() - headerWidth) / 2;
        graphics.text(font, header, headerX, HEADER_Y, RecipeUiHelper.TEXT_WHITE, true);

        // Overflow indicator
        int totalExtras = extraItems.size() + 1; // +1 for essence
        if (totalExtras > 6) {
            int overflow = totalExtras - 6;
            Component overflowText = Component.literal("§e+" + overflow);
            graphics.text(font, overflowText, OVERFLOW_X, OVERFLOW_Y, RecipeUiHelper.TEXT_WHITE, true);
        }

        maintainButtons(screen, pos);
    }

    /**
     * Draw compact count overlays on top of slot items.
     * Called after RRV has rendered slots, so text appears above item sprites.
     */
    @Override
    public void renderOverlay(RecipeViewScreen screen, RecipePosition pos,
                              GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        renderCompactCounts(graphics, font);
    }

    private void renderCompactCounts(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font) {
        int gridIdx = 0;

        // Coin (slot 1 in grid, top-left)
        if (coinAmount > 0 && gridIdx < GRID_X.length) {
            drawCompact(font, graphics, RecipeUiHelper.formatCompactNumber(coinAmount),
                    GRID_X[gridIdx], GRID_Y[gridIdx]);
            gridIdx++;
        }

        // Essence (next slot in grid)
        if (essenceAmount >= 1000 && gridIdx < GRID_X.length) {
            drawCompact(font, graphics, RecipeUiHelper.formatCompactNumber(essenceAmount),
                    GRID_X[gridIdx], GRID_Y[gridIdx]);
        }
        gridIdx++;

        // Other extras
        int startIdx = coinAmount > 0 ? 1 : 0;
        for (int i = startIdx; i < extraItems.size() && gridIdx < GRID_X.length; i++) {
            long amount = extraAmounts.get(i);
            if (amount >= 1000) {
                drawCompact(font, graphics, RecipeUiHelper.formatCompactNumber(amount),
                        GRID_X[gridIdx], GRID_Y[gridIdx]);
            }
            gridIdx++;
        }
    }

}
