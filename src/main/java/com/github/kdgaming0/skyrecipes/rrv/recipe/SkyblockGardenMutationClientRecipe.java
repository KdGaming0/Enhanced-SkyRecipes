package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.model.garden.GardenMutation;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RRV client recipe for a single Garden mutation layout.
 *
 * <p>The mutation's gridSize×gridSize layout is centered inside the 6×6 slot grid.
 * Surface type, water requirement, and cost info are rendered below the grid.</p>
 */
public class SkyblockGardenMutationClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int GRID_SIZE = 6;

    private final GardenMutation mutation;
    private final Map<Integer, SlotContent> slotContents;

    public SkyblockGardenMutationClientRecipe(Identifier id, GardenMutation mutation,
                                              ItemRegistry itemRegistry) {
        super(id);
        this.mutation = mutation;
        this.slotContents = buildSlotContents(mutation, itemRegistry);
    }

    private static Map<Integer, SlotContent> buildSlotContents(GardenMutation mutation,
                                                               ItemRegistry itemRegistry) {
        Map<Integer, SlotContent> map = new HashMap<>();
        int offset = (GRID_SIZE - mutation.gridSize()) / 2;

        for (int row = 0; row < mutation.gridSize(); row++) {
            for (int col = 0; col < mutation.gridSize(); col++) {
                int slotId = (row + offset) * GRID_SIZE + (col + offset);
                ItemStack stack = resolveStack(mutation, row, col, itemRegistry);
                if (stack != null && !stack.isEmpty()) {
                    map.put(slotId, SlotContent.of(stack));
                }
            }
        }
        return map;
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

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockGardenMutationRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (Map.Entry<Integer, SlotContent> e : slotContents.entrySet()) {
            ctx.bindSlot(e.getKey(), e.getValue());
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        return slotContents.values().stream().toList();
    }

    @Override
    public List<SlotContent> getResults() {
        return slotContents.values().stream().toList();
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int left = pos.left();
        int top = pos.top();

        // Surface + water indicator at top
        StringBuilder header = new StringBuilder();
        header.append("§7Surface: §f").append(mutation.surface());
        if (mutation.needsWater()) {
            header.append(" §b[W]");
        }
        graphics.text(font, header.toString(), left, top - 12, 0xFFFFFFFF, true);

        // Info text below grid
        int gridBottom = top + GRID_SIZE * 18 + 12;
        String info = String.format("§6%,d Coins §c+%,d Copper §7| §e%d stg",
                mutation.costCoins(), mutation.rewardCopper(), mutation.stages());
        graphics.text(font, info, left, gridBottom, 0xFFFFFFFF, true);

        // Effects summary
        if (!mutation.effects().isEmpty()) {
            StringBuilder fx = new StringBuilder("§7Effects: ");
            for (int i = 0; i < mutation.effects().size() && i < 3; i++) {
                if (i > 0) fx.append(", ");
                fx.append("§e").append(mutation.effects().get(i).name());
            }
            graphics.text(font, fx.toString(), left, gridBottom + 10, 0xFFFFFFFF, true);
        }
    }
}
