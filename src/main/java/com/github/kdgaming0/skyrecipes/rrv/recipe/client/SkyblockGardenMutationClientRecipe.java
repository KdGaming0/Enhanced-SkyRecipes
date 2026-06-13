package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.garden.GardenMutation;
import com.github.kdgaming0.skyrecipes.core.model.garden.GardenMutationRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockGardenMutationRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * RRV client recipe for a single Garden mutation layout.
 *
 * <p>Slot 0 shows the required surface block. Slots 1–36 form a 6×6 grid
 * where the mutation's layout is centred.</p>
 *
 * <p>Two borders are drawn:</p>
 * <ul>
 *   <li><b>Base-item border</b> — 1px solid around the target crop (the mutation result)</li>
 *   <li><b>Multi-block border</b> — 2px dashed around any crop (target or ingredient)
 *       that has a {@code cropSize} larger than 1×1. When the layout already expands a
 *       multi-block ingredient into multiple cells, the border follows the exact cell
 *       bounding box rather than being centred on the first cell.</li>
 * </ul>
 */
public class SkyblockGardenMutationClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int GRID_SIZE = 6;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_ORIGIN_X = 19;
    private static final int GRID_ORIGIN_Y = 24;
    private static final int SURFACE_SLOT = 0;

    private static final int NAME_X = 26;
    private static final int NAME_Y = 8;
    private static final int WATER_DROPLET_X = 128;
    private static final int WATER_DROPLET_Y = 10;
    private static final int WATER_DROPLET_SIZE = 4;

    private static final int INFO_TEXT_Y = 136;

    // ── border colors ─────────────────────────────────────────────────────────
    private static final int TARGET_BORDER_COLOR = 0xFFAA66FF;   // 1px solid
    private static final int DASH_TARGET_COLOR = 0xFF6BB3FF;     // 2px dashed
    private static final int DASH_INGREDIENT_COLOR = 0xFFC4A44A; // 2px dashed

    // ── dashed line style ─────────────────────────────────────────────────────
    private static final int DASH_ON = 2;
    private static final int DASH_OFF = 2;
    private static final int DASH_STROKE = 1;

    private static final Set<String> NEGATIVE_EFFECTS = Set.of(
            "Harvest Loss", "XP Loss", "Water Drain"
    );

    private final GardenMutation mutation;
    private final ItemStack surfaceStack;
    private final Map<Integer, SlotContent> gridSlots;
    private final List<SlotContent> ingredientList;
    private final List<SlotContent> resultList;
    // Border geometry is fixed by the immutable mutation layout, so it is computed once
    // here rather than every frame. renderOverlay just draws these.
    private final List<DashedBorder> multiBlockBorders;
    @Nullable
    private final SolidBorder baseItemBorder;
    @Nullable
    private List<Component> cachedTooltip;
    @Nullable
    private Component cachedName;

    public SkyblockGardenMutationClientRecipe(Identifier id, GardenMutation mutation,
                                              ItemRegistry itemRegistry, List<String> wikiUrls) {
        super(id, wikiUrls);
        this.mutation = mutation;
        this.surfaceStack = resolveSurfaceStack(mutation.surface());
        GridData data = buildGrid(mutation, itemRegistry);
        this.gridSlots = data.slots;
        this.ingredientList = data.ingredients;
        this.resultList = data.results;
        this.multiBlockBorders = computeMultiBlockBorders(mutation);
        this.baseItemBorder = computeBaseItemBorder(mutation);
    }

    // ── grid construction ─────────────────────────────────────────────────────

    private static GridData buildGrid(GardenMutation mutation, ItemRegistry itemRegistry) {
        Map<Integer, SlotContent> slots = new HashMap<>();
        List<SlotContent> ingredients = new ArrayList<>();
        List<SlotContent> results = new ArrayList<>();

        int offset = (GRID_SIZE - mutation.gridSize()) / 2;

        for (int row = 0; row < mutation.gridSize(); row++) {
            for (int col = 0; col < mutation.gridSize(); col++) {
                int visRow = row + offset;
                int visCol = col + offset;
                int slotId = 1 + visRow * GRID_SIZE + visCol;

                ItemStack stack = resolveStack(mutation, row, col, itemRegistry);
                if (!stack.isEmpty()) {
                    SlotContent content = SlotContent.of(stack);
                    slots.put(slotId, content);

                    if (mutation.isTarget(row, col)) {
                        results.add(content);
                    } else if (mutation.isIngredient(row, col)) {
                        ingredients.add(content);
                    }
                }
            }
        }
        return new GridData(slots, ingredients, results);
    }

    private static ItemStack resolveStack(GardenMutation mutation, int row, int col,
                                          ItemRegistry itemRegistry) {
        String internalName;
        if (mutation.isTarget(row, col)) {
            internalName = mutation.id();
        } else if (mutation.isIngredient(row, col)) {
            internalName = mutation.ingredientIdAt(row, col);
        } else {
            return ItemStack.EMPTY;
        }

        if (internalName == null || internalName.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return itemRegistry.getByInternalName(internalName)
                .map(item -> ItemStackBuilder.build(item, 1))
                .orElse(ItemStack.EMPTY);
    }

    private static ItemStack resolveSurfaceStack(String surface) {
        return switch (surface) {
            case "Farmland" -> new ItemStack(Items.DIRT);
            case "Soul Sand" -> new ItemStack(Items.SOUL_SAND);
            case "End Stone" -> new ItemStack(Items.END_STONE);
            case "Sand" -> new ItemStack(Items.SAND);
            case "Mycelium" -> new ItemStack(Items.MYCELIUM);
            default -> new ItemStack(Items.DIRT);
        };
    }

    private static DashedBorder makeDashedBorder(int r1, int c1, int r2, int c2, boolean isTarget) {
        int color = isTarget ? DASH_TARGET_COLOR : DASH_INGREDIENT_COLOR;
        int x1 = GRID_ORIGIN_X + c1 * SLOT_SIZE;
        int y1 = GRID_ORIGIN_Y + r1 * SLOT_SIZE;
        int x2 = GRID_ORIGIN_X + (c2 + 1) * SLOT_SIZE;
        int y2 = GRID_ORIGIN_Y + (r2 + 1) * SLOT_SIZE;
        return new DashedBorder(x1, y1, x2, y2, color, r2, c2, c2 - c1 + 1, r2 - r1 + 1);
    }

    private static void renderDimensionLabel(GuiGraphicsExtractor gfx, int lastRow, int lastCol,
                                             int width, int height) {
        if (width < 2 || height < 2) {
            return;
        }
        var font = Minecraft.getInstance().font;
        Component label = Component.literal(width + "×" + height);
        int textWidth = font.width(label);
        int x = GRID_ORIGIN_X + (lastCol + 1) * SLOT_SIZE - textWidth - 3;
        int y = GRID_ORIGIN_Y + (lastRow + 1) * SLOT_SIZE - font.lineHeight - 2;
        gfx.text(font, label, x, y, 0xFFCCDDFF, true);
    }

    private static void markCovered(boolean[][] covered, int r1, int c1, int r2, int c2) {
        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                if (r >= 0 && r < GRID_SIZE && c >= 0 && c < GRID_SIZE) {
                    covered[r][c] = true;
                }
            }
        }
    }

    // ── RRV contract ──────────────────────────────────────────────────────────

    private static boolean isCovered(boolean[][] covered, int r, int c) {
        return r >= 0 && r < GRID_SIZE && c >= 0 && c < GRID_SIZE && covered[r][c];
    }

    private static void drawDashedRect(GuiGraphicsExtractor gfx, int x1, int y1, int x2, int y2, int color) {
        drawDashedLineH(gfx, x1, x2, y1, color);
        drawDashedLineH(gfx, x1, x2, y2 - DASH_STROKE + 1, color);
        drawDashedLineV(gfx, x1, y1, y2, color);
        drawDashedLineV(gfx, x2 - DASH_STROKE + 1, y1, y2, color);
    }

    private static void drawDashedLineH(GuiGraphicsExtractor gfx, int x1, int x2, int y, int color) {
        int x = x1;
        boolean on = true;
        while (x < x2) {
            int len = on ? DASH_ON : DASH_OFF;
            int end = Math.min(x + len, x2);
            if (on) {
                gfx.fill(x, y, end, y + DASH_STROKE, color);
            }
            x = end;
            on = !on;
        }
    }

    private static void drawDashedLineV(GuiGraphicsExtractor gfx, int x, int y1, int y2, int color) {
        int y = y1;
        boolean on = true;
        while (y < y2) {
            int len = on ? DASH_ON : DASH_OFF;
            int end = Math.min(y + len, y2);
            if (on) {
                gfx.fill(x, y, x + DASH_STROKE, end, color);
            }
            y = end;
            on = !on;
        }
    }

    // ── rendering ─────────────────────────────────────────────────────────────

    private static String rarityColorCode(String rarity) {
        return RecipeUiHelper.rarityColorCode(rarity);
    }

    @Nullable
    private static SolidBorder computeBaseItemBorder(GardenMutation mutation) {
        int offset = (GRID_SIZE - mutation.gridSize()) / 2;
        int minR = GRID_SIZE, minC = GRID_SIZE;
        int maxR = -1, maxC = -1;
        boolean hasTarget = false;

        for (int r = 0; r < mutation.gridSize(); r++) {
            for (int c = 0; c < mutation.gridSize(); c++) {
                if (mutation.isTarget(r, c)) {
                    int vr = r + offset, vc = c + offset;
                    minR = Math.min(minR, vr);
                    minC = Math.min(minC, vc);
                    maxR = Math.max(maxR, vr);
                    maxC = Math.max(maxC, vc);
                    hasTarget = true;
                }
            }
        }

        if (!hasTarget) {
            return null;
        }

        int x1 = GRID_ORIGIN_X + minC * SLOT_SIZE + 1;
        int y1 = GRID_ORIGIN_Y + minR * SLOT_SIZE + 1;
        int x2 = GRID_ORIGIN_X + (maxC + 1) * SLOT_SIZE - 1;
        int y2 = GRID_ORIGIN_Y + (maxR + 1) * SLOT_SIZE - 1;
        return new SolidBorder(x1, y1, x2, y2);
    }

    // ── base-item border (1px solid — always drawn) ───────────────────────────

    private static List<DashedBorder> computeMultiBlockBorders(GardenMutation mutation) {
        List<DashedBorder> borders = new ArrayList<>();
        boolean[][] covered = new boolean[GRID_SIZE][GRID_SIZE];
        int offset = (GRID_SIZE - mutation.gridSize()) / 2;

        // ── Target multi-block ────────────────────────────────────────────────────
        GardenMutationRegistry.CropSize targetCs = GardenMutationRegistry.getCropSize(mutation.id());
        if (targetCs != null && (targetCs.width() > 1 || targetCs.height() > 1)) {
            int minR = GRID_SIZE, minC = GRID_SIZE, maxR = -1, maxC = -1;
            for (int r = 0; r < mutation.gridSize(); r++) {
                for (int c = 0; c < mutation.gridSize(); c++) {
                    if (mutation.isTarget(r, c)) {
                        int vr = r + offset, vc = c + offset;
                        minR = Math.min(minR, vr);
                        minC = Math.min(minC, vc);
                        maxR = Math.max(maxR, vr);
                        maxC = Math.max(maxC, vc);
                    }
                }
            }
            if (maxR >= 0) {
                borders.add(makeDashedBorder(minR, minC, maxR, maxC, true));
                markCovered(covered, minR, minC, maxR, maxC);
            }
        }

        // ── Ingredient multi-blocks ───────────────────────────────────────────────
        for (int r = 0; r < mutation.gridSize(); r++) {
            for (int c = 0; c < mutation.gridSize(); c++) {
                if (!mutation.isIngredient(r, c)) continue;

                int top = r + offset, left = c + offset;
                if (isCovered(covered, top, left)) continue;

                String ingId = mutation.ingredientIdAt(r, c);
                GardenMutationRegistry.CropSize cs = GardenMutationRegistry.getCropSize(ingId);
                if (cs == null || (cs.width() <= 1 && cs.height() <= 1)) continue;

                // This is the top-left of a new instance — extend by crop dimensions.
                int bottom = Math.min(top + cs.height() - 1, GRID_SIZE - 1);
                int right = Math.min(left + cs.width() - 1, GRID_SIZE - 1);

                borders.add(makeDashedBorder(top, left, bottom, right, false));
                markCovered(covered, top, left, bottom, right);
            }
        }
        return borders;
    }

    // ── multi-block borders (2px dashed — cropSize-based) ─────────────────────

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockGardenMutationRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(SURFACE_SLOT, SlotContent.of(surfaceStack));
        for (Map.Entry<Integer, SlotContent> e : gridSlots.entrySet()) {
            ctx.bindSlot(e.getKey(), e.getValue());
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        return ingredientList;
    }

    @Override
    public List<SlotContent> getResults() {
        return resultList;
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;

        // Mutation name beside surface slot
        Component name = getNameComponent();
        graphics.text(font, name, NAME_X, NAME_Y, RecipeUiHelper.TEXT_WHITE, true);

        // Water droplet indicator + tooltip
        if (mutation.needsWater()) {
            int dropX = WATER_DROPLET_X;
            int dropY = WATER_DROPLET_Y;
            int dropS = WATER_DROPLET_SIZE;
            graphics.fill(dropX, dropY, dropX + dropS, dropY + dropS, 0xFF00FFFF);
            graphics.fill(dropX + 1, dropY + 1, dropX + 2, dropY + 2, RecipeUiHelper.TEXT_WHITE);

            if (mouseX >= dropX - 2 && mouseX < dropX + dropS + 2
                    && mouseY >= dropY - 2 && mouseY < dropY + dropS + 2) {
                graphics.setComponentTooltipForNextFrame(font,
                        List.of(Component.literal("§bRequires Water")),
                        pos.left() + mouseX, pos.top() + mouseY);
            }
        }

        // "Hover for more info"
        Component hint = Component.literal("§8Hover for more info");
        int hintWidth = font.width(hint);
        int hintX = (pos.width() - hintWidth) / 2;
        graphics.text(font, hint, hintX, INFO_TEXT_Y, RecipeUiHelper.TEXT_WHITE, true);

        // Tooltip on hover over the info area
        if (mouseX >= 0 && mouseX < pos.width()
                && mouseY >= INFO_TEXT_Y - 2 && mouseY < INFO_TEXT_Y + font.lineHeight + 2) {
            List<Component> tooltip = getTooltip();
            if (!tooltip.isEmpty()) {
                graphics.setComponentTooltipForNextFrame(font, tooltip,
                        pos.left() + mouseX, pos.top() + mouseY);
            }
        }

        maintainButtons(screen, pos);
    }

    // ── dashed line drawing ───────────────────────────────────────────────────

    @Override
    public void renderOverlay(RecipeViewScreen screen, RecipePosition pos,
                              GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // Dashed multi-block borders first, then the solid base-item border on top.
        for (DashedBorder b : multiBlockBorders) {
            drawDashedRect(graphics, b.x1(), b.y1(), b.x2(), b.y2(), b.color());
            renderDimensionLabel(graphics, b.labelLastRow(), b.labelLastCol(), b.labelWidth(), b.labelHeight());
        }

        if (baseItemBorder != null) {
            SolidBorder b = baseItemBorder;
            graphics.fill(b.x1(), b.y1(), b.x2(), b.y1() + 1, TARGET_BORDER_COLOR);
            graphics.fill(b.x1(), b.y2() - 1, b.x2(), b.y2(), TARGET_BORDER_COLOR);
            graphics.fill(b.x1(), b.y1(), b.x1() + 1, b.y2(), TARGET_BORDER_COLOR);
            graphics.fill(b.x2() - 1, b.y1(), b.x2(), b.y2(), TARGET_BORDER_COLOR);
        }
    }

    private Component getNameComponent() {
        if (cachedName != null) {
            return cachedName;
        }
        String color = rarityColorCode(mutation.rarity());
        cachedName = Component.literal(color + mutation.name());
        return cachedName;
    }

    private List<Component> getTooltip() {
        if (cachedTooltip != null) {
            return cachedTooltip;
        }
        cachedTooltip = buildTooltip();
        return cachedTooltip;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<Component> buildTooltip() {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.literal("§7Surface: §e" + mutation.surface()));
        lines.add(Component.literal("§7Water: " + (mutation.needsWater() ? "§bYes" : "§7No")));
        lines.add(Component.literal("§7Stages: §e" + (mutation.stages() > 0 ? String.valueOf(mutation.stages()) : "∞")));
        lines.add(Component.literal("§7Cost: §6" + RecipeUiHelper.formatCompactNumber(mutation.costCoins()) + " Coins"));
        lines.add(Component.literal("§7Copper: §c+" + mutation.rewardCopper()));

        if (!mutation.spreadingConditions().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal("§f§lSpreading Conditions"));
            lines.add(Component.literal("§8§m─────────────"));
            for (GardenMutation.SpreadingCondition cond : mutation.spreadingConditions()) {
                lines.add(Component.literal("  §e" + cond.text()));
            }
        }

        if (!mutation.effects().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal("§f§lEffects"));
            lines.add(Component.literal("§8§m─────────────"));
            for (GardenMutation.Effect effect : mutation.effects()) {
                boolean negative = NEGATIVE_EFFECTS.contains(effect.name());
                String arrow = negative ? "§c▼ " : "§a▲ ";
                lines.add(Component.literal("  " + arrow + "§f" + effect.name()));
                lines.add(Component.literal("  §7  " + effect.description()));
            }
        }

        if (!mutation.requiredFor().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal("§f§lRequired For"));
            lines.add(Component.literal("§8§m─────────────"));
            ItemRegistry registry = SkyRecipes.getItemRegistry();
            for (String reqId : mutation.requiredFor()) {
                String display = reqId;
                if (registry != null) {
                    display = registry.getByInternalName(reqId)
                            .map(NeuItem::displayName)
                            .orElse(reqId);
                }
                lines.add(Component.literal("  §8• §7" + display));
            }
        }

        if (mutation.specialMechanic() != null && !mutation.specialMechanic().isBlank()) {
            lines.add(Component.empty());
            lines.add(Component.literal("§c§l⚠ Special Mechanic"));
            lines.add(Component.literal("§8§m─────────────"));
            for (String part : mutation.specialMechanic().split("\n")) {
                lines.add(Component.literal("  §7" + part));
            }
        }

        return Collections.unmodifiableList(lines);
    }

    /**
     * Pixel geometry of one dashed multi-block border plus its dimension-label inputs.
     */
    private record DashedBorder(int x1, int y1, int x2, int y2, int color,
                                int labelLastRow, int labelLastCol,
                                int labelWidth, int labelHeight) {
    }

    // ── tooltip ───────────────────────────────────────────────────────────────

    /**
     * Pixel geometry of the 1px solid border around the target crop.
     */
    private record SolidBorder(int x1, int y1, int x2, int y2) {
    }

    // ── buttons ───────────────────────────────────────────────────────────────

    private record GridData(Map<Integer, SlotContent> slots, List<SlotContent> ingredients,
                            List<SlotContent> results) {
    }
}
