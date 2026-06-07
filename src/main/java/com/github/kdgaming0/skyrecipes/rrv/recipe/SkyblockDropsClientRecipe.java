package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.mob.MobPreview;
import com.github.kdgaming0.skyrecipes.core.mob.MobPreviewResolver;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.render.MobPreviewController;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side drop recipe. Delegates mob-preview entity lifecycle to
 * {@link MobPreviewController} so this class only handles slots, text, and buttons.
 */
public class SkyblockDropsClientRecipe extends AbstractSkyblockClientRecipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyblockDropsClientRecipe.class);
    private static final Set<String> LOGGED_UNRESOLVED = ConcurrentHashMap.newKeySet();

    private final String mobName;
    private final String[] chances;
    private final List<SlotContent> drops;
    private final MobPreviewController previewController;
    @Nullable private final RecipeViewMenu.AdditionalStackModifier[] chanceModifiers;
    @Nullable private Component cachedMobName;

    public SkyblockDropsClientRecipe(Identifier id, String mobName, String renderRef,
                                     List<DropEntry> drops, String[] chances,
                                     List<String> wikiUrls) {
        super(id, wikiUrls);
        this.mobName = mobName != null ? mobName : "";
        this.chances = chances != null ? chances : new String[0];
        this.drops = buildDropsList(drops);

        ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
        MobPreview resolved = constants != null ? MobPreviewResolver.resolve(renderRef, constants) : null;
        if (resolved == null) logUnresolvedOnce(renderRef);
        this.previewController = new MobPreviewController(resolved);
        this.chanceModifiers = buildChanceModifiers(drops);
    }

    private static List<SlotContent> buildDropsList(List<DropEntry> rawDrops) {
        List<SlotContent> out = new ArrayList<>();
        for (DropEntry drop : rawDrops) {
            out.add(SlotContent.of(drop.stack()));
        }
        return out;
    }

    private static void logUnresolvedOnce(@Nullable String renderRef) {
        String key = renderRef != null ? renderRef : "<empty>";
        if (LOGGED_UNRESOLVED.add(key)) {
            LOGGER.debug("Unresolved drop-recipe render ref '{}' — placeholder will be drawn.", key);
        }
    }

    @Nullable
    private RecipeViewMenu.AdditionalStackModifier[] buildChanceModifiers(List<DropEntry> rawDrops) {
        if (chances.length == 0) return null;
        int limit = Math.min(chances.length, rawDrops.size());
        RecipeViewMenu.AdditionalStackModifier[] mods = new RecipeViewMenu.AdditionalStackModifier[limit];
        for (int i = 0; i < limit; i++) {
            String chance = chances[i];
            if (chance == null || chance.isEmpty()) continue;
            if (i >= drops.size() || drops.get(i).isEmpty()) continue;
            Component line = Component.literal("§7Drop chance: §e§l" + chance);
            mods[i] = (stack, tooltip) -> {
                tooltip.addLast(Component.empty());
                tooltip.addLast(line);
            };
        }
        return mods;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockDropsRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < drops.size() && i < getType().getSlotCount(); i++) {
            if (i < 9) {
                ctx.bindSlot(i, drops.get(i));
            } else {
                ctx.bindOptionalSlot(i, drops.get(i), RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            }
        }
        if (chanceModifiers != null) {
            for (int i = 0; i < chanceModifiers.length; i++) {
                RecipeViewMenu.AdditionalStackModifier mod = chanceModifiers[i];
                if (mod != null) ctx.addAdditionalStackModifier(i, mod);
            }
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        return drops;
    }

    @Override
    public void initRecipe() {
        super.initRecipe();
        previewController.init();
    }

    @Override
    public void fadeRecipe() {
        super.fadeRecipe();
        previewController.fade();
    }

    @Override
    public void tick() {
        previewController.tick();
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        previewController.render(graphics, pos.left(), pos.top(), mouseX, mouseY, partialTicks);
        renderMobName(graphics, pos);
        renderHoverTooltipIfNeeded(graphics, screen, pos, mouseX, mouseY);
        maintainButtons(screen, pos);
    }

    private void renderMobName(GuiGraphicsExtractor gfx, RecipePosition pos) {
        if (mobName.isEmpty()) return;

        int maxWidth = pos.width() - 8;
        Component line = cachedMobName;
        if (line == null) {
            line = ellipsize(mobName, maxWidth);
            cachedMobName = line;
        }

        var font = Minecraft.getInstance().font;
        int textWidth = font.width(line);
        int x = (pos.width() - textWidth) / 2;
        int y = 4;

        gfx.text(font, line, x, y, 0xFFFFFFFF, true);
    }

    private void renderHoverTooltipIfNeeded(GuiGraphicsExtractor gfx, RecipeViewScreen screen,
                                            RecipePosition pos, int mouseX, int mouseY) {
        if (!previewController.isHovered() || mobName.isEmpty()) return;

        Component tip = Component.literal(mobName).withStyle(ChatFormatting.GOLD);
        gfx.setComponentTooltipForNextFrame(
                screen.getFont(),
                List.of(tip),
                pos.left() + mouseX,
                pos.top() + mouseY);
    }

    @Override
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        if (wikiUrls.isEmpty()) return null;
        String url = wikiUrls.stream()
                .filter(u -> u != null && !u.isEmpty())
                .findFirst()
                .orElse(null);
        if (url == null) return null;

        int btnX = pos.left() + pos.width() - 16;
        int btnY = pos.top() + pos.height() - 16;
        Button wikiButton = Button.builder(Component.literal("W"), b -> {
            try {
                Util.getPlatform().openUri(URI.create(url));
            } catch (Exception e) {
                // ignore
            }
        }).pos(btnX, btnY).size(12, 12)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Open Wiki")))
                .build();
        screen.addRecipeWidget(wikiButton);
        return wikiButton;
    }

    private static Component ellipsize(String text, int maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) {
            return Component.literal(text);
        }
        String ellipsis = "…";
        int avail = maxWidth - font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(sb.toString() + text.charAt(i)) > avail) break;
            sb.append(text.charAt(i));
        }
        return Component.literal(sb + ellipsis);
    }

    public List<DropEntry> getDrops() {
        List<DropEntry> list = new ArrayList<>();
        for (int i = 0; i < drops.size() && i < chances.length; i++) {
            ItemStack stack = drops.get(i).getByIndex(0);
            list.add(new DropEntry(stack, "", chances[i]));
        }
        return list;
    }

    public record DropEntry(ItemStack stack, String internalName, String chance) {
    }
}
