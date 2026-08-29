package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.mob.MobPreview;
import com.github.kdgaming0.skyrecipes.core.mob.MobPreviewResolver;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.render.mob.MobPreviewController;
import com.github.kdgaming0.skyrecipes.core.util.LegacyStringParser;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockDropsRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    /**
     * Result list used for RRV's recipe index only: the drops plus the source mob's own
     * stack. Including the source here makes clicking the mob in the item list resolve to
     * this recipe (via {@link com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockRecipeCache}),
     * while {@link #bindSlots} still renders only {@link #drops}, so the source is never drawn
     * as a stray slot.
     */
    private final List<SlotContent> results;
    private final MobPreviewController previewController;
    private final List<Component> hoverTooltip;
    @Nullable
    private final RecipeViewMenu.AdditionalStackModifier[] chanceModifiers;
    @Nullable
    private Component cachedMobName;

    public SkyblockDropsClientRecipe(Identifier id, String mobName, String renderRef,
                                     @Nullable ItemStack sourceStack,
                                     List<DropEntry> drops, String[] chances,
                                     int level, int xp, int combatXp, int coins,
                                     String location, List<String> extra,
                                     String health, String damage,
                                     List<String> wikiUrls) {
        super(id, wikiUrls);
        this.mobName = mobName != null ? mobName : "";
        this.chances = chances != null ? chances : new String[0];
        this.drops = buildDropsList(drops);
        this.results = buildResultsList(this.drops, sourceStack);
        this.hoverTooltip = buildHoverTooltip(this.mobName, level, xp, combatXp, coins,
                location, extra, health, damage);

        ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
        MobPreview resolved = constants != null ? MobPreviewResolver.resolve(renderRef, constants) : null;
        if (resolved == null) logUnresolvedOnce(renderRef);
        this.previewController = new MobPreviewController(resolved);
        this.chanceModifiers = buildChanceModifiers(drops);
    }

    private static List<Component> buildHoverTooltip(String name, int level, int xp, int combatXp,
                                                      int coins, String location, List<String> extra,
                                                      String health, String damage) {
        List<Component> lines = new ArrayList<>();
        if (!name.isEmpty()) lines.add(LegacyStringParser.parse(name));
        addDetail(lines, "Level", level >= 0 ? Integer.toString(level) : "");
        addDetail(lines, "Health", health);
        addDetail(lines, "Damage", damage);
        addDetail(lines, "Location", location);
        addDetail(lines, "Combat XP", combatXp >= 0 ? formatNumber(combatXp) : "");
        addDetail(lines, "XP", xp >= 0 ? formatNumber(xp) : "");
        addDetail(lines, "Coins", coins >= 0 ? formatNumber(coins) : "");
        if (extra != null && !extra.isEmpty()) {
            lines.add(Component.empty());
            for (String line : extra) lines.add(LegacyStringParser.parse(line));
        }
        return List.copyOf(lines);
    }

    private static void addDetail(List<Component> lines, String label, String value) {
        if (value == null || value.isBlank()) return;
        lines.add(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.YELLOW)));
    }

    private static String formatNumber(int number) {
        return String.format(java.util.Locale.ROOT, "%,d", number);
    }

    private static List<SlotContent> buildDropsList(List<DropEntry> rawDrops) {
        List<SlotContent> out = new ArrayList<>();
        for (DropEntry drop : rawDrops) {
            out.add(SlotContent.of(drop.stack()));
        }
        return out;
    }

    /**
     * Appends the source mob's stack after the drops so its SkyBlock ID is indexed as a
     * result. The source is added last so {@code getResultTier} scans the actual drops first.
     * Falls back to the drops list when the source has no valid stack.
     */
    private static List<SlotContent> buildResultsList(List<SlotContent> drops, @Nullable ItemStack sourceStack) {
        if (sourceStack == null || sourceStack.isEmpty()) {
            return drops;
        }
        List<SlotContent> out = new ArrayList<>(drops.size() + 1);
        out.addAll(drops);
        out.add(SlotContent.of(sourceStack));
        return out;
    }

    private static void logUnresolvedOnce(@Nullable String renderRef) {
        String key = renderRef != null ? renderRef : "<empty>";
        if (LOGGED_UNRESOLVED.add(key)) {
            LOGGER.debug("Unresolved drop-recipe render ref '{}' — placeholder will be drawn.", key);
        }
    }

    private static Component ellipsize(String text, int maxWidth) {
        return RecipeUiHelper.ellipsize(Minecraft.getInstance().font, text, maxWidth, false);
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
            mods[i] = (_, tooltip) -> {
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
        return results;
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

        gfx.text(font, line, x, y, RecipeUiHelper.TEXT_WHITE, true);
    }

    private void renderHoverTooltipIfNeeded(GuiGraphicsExtractor gfx, RecipeViewScreen screen,
                                            RecipePosition pos, int mouseX, int mouseY) {
        if (!previewController.isHovered() || hoverTooltip.isEmpty()) return;
        gfx.setComponentTooltipForNextFrame(
                screen.getFont(),
                hoverTooltip,
                pos.left() + mouseX,
                pos.top() + mouseY);
    }

    public record DropEntry(ItemStack stack, String internalName, String chance) {
    }
}
