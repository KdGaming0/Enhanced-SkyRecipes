package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.LegacyStringParser;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client recipe for SkyBlock Kat pet rarity upgrades.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Input pet, up to 5 material costs + coin in the 3×2 grid, output pet</li>
 *   <li>Level slider (0–100) that applies the 0.3%-per-level coin discount</li>
 *   <li>Output pet level is converted based on rarity XP curve offsets</li>
 * </ul>
 */
public class SkyblockKatUpgradeClientRecipe extends AbstractSkyblockClientRecipe {

    /* ═══════════════════════════════════════════════════════════════
     *  EDIT SLIDER & TEXT POSITIONS HERE
     *  All coordinates are relative to the 152×96 recipe card.
     * ═══════════════════════════════════════════════════════════════ */

    /**
     * Slider position and size.
     */
    private static final int SLIDER_X = 16;
    private static final int SLIDER_Y = 60;
    private static final int SLIDER_WIDTH = 120;
    private static final int SLIDER_HEIGHT = 20;

    /**
     * Duration text vertical position (centred horizontally).
     */
    private static final int DURATION_Y = 44;

    // -----------------------------------------------------------------
    // Coin is bound as a regular slot inside the 3×2 material grid.
    // Slot 1 = top-left cell (45, 4). Max materials is 4, so no conflict.
    // -----------------------------------------------------------------
    private static final int COIN_SLOT_INDEX = 1;
    private static final int COIN_SLOT_X = 45;
    private static final int COIN_SLOT_Y = 4;

    // -----------------------------------------------------------------
    // Pet rarity offsets from NEU constants/pets.json pet_rarity_offset
    // -----------------------------------------------------------------
    private static final int[] RARITY_OFFSETS = {0, 6, 11, 16, 20, 20};

    private static final java.util.regex.Pattern LEVEL_100_PATTERN =
            java.util.regex.Pattern.compile("(?<!\\d)100(?!\\d)");

    private final NeuItem inputItem;
    private final NeuItem outputItem;
    private final int inputTier;
    private final int outputTier;
    private final long baseCoins;
    private final int timeSeconds;
    private final List<ItemStack> itemCosts;
    private ItemStack input;
    private ItemStack output;
    private ItemStack coinStack;

    private int petLevel = 0;
    @Nullable
    private PetLevelSlider levelSlider;
    @Nullable
    private RecipeViewScreen screenRef;

    public SkyblockKatUpgradeClientRecipe(Identifier id,
                                          NeuItem inputItem, ItemStack input,
                                          NeuItem outputItem, ItemStack output,
                                          long coins, int timeSeconds,
                                          List<ItemStack> itemCosts) {
        super(id);
        this.inputItem = inputItem;
        this.input = input;
        this.outputItem = outputItem;
        this.output = output;
        this.baseCoins = coins;
        this.timeSeconds = timeSeconds;
        this.itemCosts = List.copyOf(itemCosts);

        this.inputTier = extractTier(inputItem);
        this.outputTier = extractTier(outputItem);

        this.coinStack = buildCoinStack(0);
    }

    private static int extractTier(NeuItem neuItem) {
        if (neuItem == null) return 0;
        String internalName = neuItem.internalName();
        if (internalName == null || internalName.isEmpty()) return 0;
        int semi = internalName.lastIndexOf(';');
        if (semi < 0 || semi == internalName.length() - 1) return 0;
        try {
            int tier = Integer.parseInt(internalName.substring(semi + 1));
            return Math.max(0, Math.min(RARITY_OFFSETS.length - 1, tier));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockKatUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(0, SlotContent.of(input));
        // Coin always occupies slot 1 (top-left of material grid)
        ctx.bindSlot(COIN_SLOT_INDEX, SlotContent.of(coinStack));
        // Materials fill slots 2+
        for (int i = 0; i < itemCosts.size() && i < 5; i++) {
            ctx.bindSlot(2 + i, SlotContent.of(itemCosts.get(i)));
        }
        ctx.bindSlot(7, SlotContent.of(output));
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        list.add(SlotContent.of(input));
        for (ItemStack stack : itemCosts) {
            list.add(SlotContent.of(stack));
        }
        list.add(SlotContent.of(coinStack));
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(SlotContent.of(output));
    }

    @Override
    public void initRecipe() {
        super.initRecipe();
        screenRef = null;
    }

    @Override
    public void fadeRecipe() {
        super.fadeRecipe();
        levelSlider = null;
        screenRef = null;
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        this.screenRef = screen;

        var font = Minecraft.getInstance().font;

        // Duration text — centred horizontally (only text line)
        Component duration = RecipeUiHelper.formatDuration(timeSeconds, true, "Time: ");
        int textWidth = font.width(duration);
        int textX = (pos.width() - textWidth) / 2;
        graphics.text(font, duration, textX, DURATION_Y, RecipeUiHelper.TEXT_WHITE, true);

        maintainButtons(screen, pos);
    }

    @Override
    public void renderOverlay(RecipeViewScreen screen, RecipePosition pos,
                              GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;

        long discounted = computeDiscountedCoins(petLevel);
        Component compact = Component.literal(RecipeUiHelper.formatCompactNumber(discounted));
        int compactW = font.width(compact);
        int countX = COIN_SLOT_X + 17 - compactW;
        int countY = COIN_SLOT_Y + 9;

        graphics.text(font, compact, countX, countY, RecipeUiHelper.TEXT_WHITE, true);
    }

    @Override
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        Button wiki = addWikiButton(screen, pos);

        int absSliderX = pos.left() + SLIDER_X;
        int absSliderY = pos.top() + SLIDER_Y;
        levelSlider = new PetLevelSlider(absSliderX, absSliderY, SLIDER_WIDTH, SLIDER_HEIGHT, petLevel);
        screen.addRecipeWidget(levelSlider);

        return levelSlider;
    }

    // -----------------------------------------------------------------
    // Pet level handling
    // -----------------------------------------------------------------

    private void setPetLevel(int level) {
        if (level == petLevel) return;
        petLevel = level;
        refreshStacks();
        if (screenRef != null) {
            updateSlotItems(screenRef);
        }
    }

    private void refreshStacks() {
        if (inputItem != null) {
            input = rebuildStack(inputItem, petLevel);
        }
        if (outputItem != null) {
            int outputLevel = computeOutputLevel(petLevel);
            output = rebuildStack(outputItem, outputLevel);
        }
        coinStack = buildCoinStack(petLevel);
    }

    private ItemStack rebuildStack(NeuItem neuItem, int level) {
        ItemStack stack = ItemStackBuilder.build(neuItem);
        if (level <= 0 || neuItem == null) {
            return stack;
        }

        String rawName = neuItem.displayName();
        if (rawName != null && !rawName.isEmpty()) {
            String leveledName = LEVEL_100_PATTERN.matcher(rawName)
                    .replaceFirst(String.valueOf(level));
            stack.set(DataComponents.CUSTOM_NAME, LegacyStringParser.parse(leveledName));
        }

        List<String> rawLore = neuItem.lore();
        if (rawLore != null && !rawLore.isEmpty()) {
            List<Component> newLore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                String newLine = LEVEL_100_PATTERN.matcher(line).replaceFirst(String.valueOf(level));
                newLore.add(LegacyStringParser.parse(newLine));
            }
            stack.set(DataComponents.LORE, new ItemLore(newLore));
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, existing -> {
            existing.putInt("petLevel", level);
        });

        return stack;
    }

    private ItemStack buildCoinStack(int level) {
        long discounted = computeDiscountedCoins(level);
        ItemStack stack = new ItemStack(Items.GOLD_NUGGET);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Coins"));

        // Show exact amount in lore so hover tooltip is useful
        String exact = String.format("%,d", discounted);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§e" + exact + " Coins")
        )));

        return stack;
    }

    // -----------------------------------------------------------------
    // Tier / level / coin math
    // -----------------------------------------------------------------

    private void updateSlotItems(RecipeViewScreen screen) {
        RecipeViewMenu menu = screen.getMenu();
        List<ReliableClientRecipe> display = menu.getCurrentDisplay();
        int recipeIndex = -1;
        for (int i = 0; i < display.size(); i++) {
            if (display.get(i) == this) {
                recipeIndex = i;
                break;
            }
        }
        if (recipeIndex < 0) return;

        int slotCount = getType().getSlotCount();
        var container = menu.getViewContainer();
        int base = recipeIndex * slotCount;
        container.setItem(base, input);
        container.setItem(base + 7, output);
        container.setItem(base + COIN_SLOT_INDEX, coinStack);
    }

    private int computeOutputLevel(int inputLevel) {
        int result = inputLevel + RARITY_OFFSETS[inputTier] - RARITY_OFFSETS[outputTier];
        return Math.max(1, Math.min(100, result));
    }

    private long computeDiscountedCoins(int level) {
        int effectiveLevel = Math.min(level, 100);
        double multiplier = 1.0 - (0.003 * effectiveLevel);
        return Math.round(baseCoins * multiplier);
    }

    // -----------------------------------------------------------------
    // Slider widget
    // -----------------------------------------------------------------

    private class PetLevelSlider extends AbstractSliderButton {

        PetLevelSlider(int x, int y, int width, int height, int initialLevel) {
            super(x, y, width, height, Component.empty(), initialLevel / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int level = (int) Math.round(value * 100);
            setMessage(Component.literal("Level: " + level));
        }

        @Override
        protected void applyValue() {
            int newLevel = (int) Math.round(value * 100);
            setPetLevel(newLevel);
        }
    }
}
