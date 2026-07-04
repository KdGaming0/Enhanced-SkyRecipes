package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.render.mob.MobPreviewRenderer;
import com.github.kdgaming0.skyrecipes.core.render.mob.PlayerSkinRenderer;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.NpcInfoRegistry;
import com.github.kdgaming0.skyrecipes.rrv.recipe.RecipeViewOpener;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockNpcShopRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SkyblockNpcShopClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int NPC_RENDER_X = -1;
    private static final int NPC_RENDER_Y = 17;
    private static final int NPC_RENDER_WIDTH = 33;
    private static final int NPC_RENDER_HEIGHT = 30;
    private static final int NPC_NAME_Y = 2;
    private static final int BUTTON_ROW_Y_OFFSET = 49;
    private static final int NPC_INFO_BUTTON_WIDTH = 56;
    private static final int NPC_INFO_BUTTON_HEIGHT = 12;
    private static final int BUTTON_GAP = 4;

    // 2×4 cost grid starting at (29, 16) with 18px spacing
    private static final int COST_GRID_ORIGIN_X = 29;
    private static final int COST_GRID_ORIGIN_Y = 14;
    private static final int COST_SLOT_SPACING = 18;

    private final String npcDisplayName;
    private final String npcInternalName;
    private final List<ShopCost> costs;
    private final ItemStack result;
    private final ItemStack npcHead;
    /** Compact count per cost slot, precomputed (BigDecimal math is too costly per frame); null = no label. */
    private final Component[] costLabels;

    public SkyblockNpcShopClientRecipe(Identifier id, String npcDisplayName, String npcInternalName,
                                       List<ShopCost> costs, ItemStack result,
                                       List<String> wikiUrls, ItemStack npcHead) {
        super(id, wikiUrls);
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
        this.npcInternalName = npcInternalName != null ? npcInternalName : "";
        this.costs = costs;
        this.result = result;
        this.npcHead = npcHead != null ? npcHead : ItemStack.EMPTY;
        this.costLabels = new Component[costs.size()];
        for (int i = 0; i < costs.size(); i++) {
            ShopCost cost = costs.get(i);
            if (cost.isCoins() || cost.count() >= 1000) {
                costLabels[i] = Component.literal(RecipeUiHelper.formatCompactNumber(cost.count()));
            }
        }
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockNpcShopRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < costs.size() && i < 8; i++) {
            ctx.bindSlot(i, SlotContent.of(costs.get(i).stack()));
        }
        ctx.bindSlot(8, SlotContent.of(result));
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        for (ShopCost cost : costs) {
            list.add(SlotContent.of(cost.stack()));
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(SlotContent.of(result));
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;

        // NPC name text — centered above the render area and slots
        if (!npcDisplayName.isEmpty()) {
            Component name = Component.literal(npcDisplayName);
            int textWidth = font.width(name);
            int x = (pos.width() - textWidth) / 2;
            graphics.text(font, name, x, NPC_NAME_Y, RecipeUiHelper.TEXT_WHITE, true);
        }

        // NPC skin preview — full player model using the same path as Drops recipes
        renderNpcSkin(graphics, pos);

        maintainButtons(screen, pos);
    }

    @Override
    public void renderOverlay(RecipeViewScreen screen, RecipePosition pos,
                              GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        renderCompactCounts(graphics, Minecraft.getInstance().font);
    }

    private void renderNpcSkin(GuiGraphicsExtractor graphics, RecipePosition pos) {
        if (npcHead.isEmpty()) return;

        ResolvableProfile profile = npcHead.get(DataComponents.PROFILE);
        if (profile == null) return;

        PlayerSkinRenderCache cache = Minecraft.getInstance().playerSkinRenderCache();
        PlayerSkinRenderCache.RenderInfo renderInfo = cache.getOrDefault(profile);
        //noinspection ConstantValue
        if (renderInfo == null) return;

        Identifier texture = renderInfo.playerSkin().body().texturePath();
        //noinspection ConstantValue
        if (texture == null) return;

        PlayerSkinRenderer.render(graphics, texture,
                pos.left() + NPC_RENDER_X, pos.top() + NPC_RENDER_Y,
                NPC_RENDER_WIDTH, NPC_RENDER_HEIGHT,
                MobPreviewRenderer.getRotationAngle());
    }

    private void renderCompactCounts(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font) {
        for (int i = 0; i < costLabels.length && i < 8; i++) {
            Component text = costLabels[i];
            if (text == null) continue;

            int textWidth = font.width(text);

            int slotX = COST_GRID_ORIGIN_X + (i % 4) * COST_SLOT_SPACING;
            int slotY = COST_GRID_ORIGIN_Y + (i / 4) * COST_SLOT_SPACING;

            int x = slotX + 17 - textWidth;
            int y = slotY + 9;

            graphics.text(font, text, x, y, RecipeUiHelper.TEXT_WHITE, true);
        }
    }

    @Override
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        Button wiki = addWikiButton(screen, pos);

        SkyblockInfoClientRecipe infoRecipe = NpcInfoRegistry.get(npcInternalName);
        if (infoRecipe != null) {
            int btnY = pos.top() + BUTTON_ROW_Y_OFFSET;
            int wikiRight = pos.left() + getType().getDisplayWidth() - 16;
            int btnX = wikiRight - BUTTON_GAP - NPC_INFO_BUTTON_WIDTH;

            Button infoBtn = Button.builder(Component.literal("NPC Info"), _ -> RecipeViewOpener.open(infoRecipe))
                    .pos(btnX, btnY)
                    .size(NPC_INFO_BUTTON_WIDTH, NPC_INFO_BUTTON_HEIGHT)
                    .build();
            screen.addRecipeWidget(infoBtn);
            return infoBtn;
        }

        return wiki;
    }

    public record ShopCost(ItemStack stack, String internalName, int count, boolean isCoins) {
    }
}
