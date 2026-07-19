package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.LegacyStringParser;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockInfoRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.ClientCommandSender;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SkyBlock info recipe card showing the item icon, a title, info lines,
 * a wiki button, and optionally a navigate button for NPCs.
 */
public class SkyblockInfoClientRecipe extends AbstractSkyblockClientRecipe {

    private static final boolean SKYHANNI_PRESENT =
            FabricLoader.getInstance().isModLoaded("skyhanni");

    private static final int TITLE_Y = 22;
    private static final int INFO_START_Y = 36;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_MAX_WIDTH = 112;
    private static final int INFO_TEXT_X = 6;
    /** Wrap width for info lines: card width minus the left indent on both sides. */
    private static final int INFO_WRAP_WIDTH = 108;
    /** Total rendered (post-wrap) lines that fit between INFO_START_Y and the buttons. */
    private static final int MAX_RENDERED_LINES = 6;

    private static final int NAV_BUTTON_WIDTH = 56;
    private static final int NAV_BUTTON_HEIGHT = 12;
    private static final int BUTTON_GAP = 4;

    private final NeuItem neuItem;
    private final String rawDisplayName;
    private final List<Component> infoLines;
    private final boolean isNpc;
    private final String npcDisplayName;
    /** Search text for the in-game /bz command; empty hides the Bazaar button. */
    private final String bazaarSearch;
    private volatile SlotContent lazyDisplayItem;
    private volatile Component lazyTitle;

    public SkyblockInfoClientRecipe(Identifier id, NeuItem neuItem,
                                    String displayName, List<Component> infoLines,
                                    List<String> wikiUrls) {
        this(id, neuItem, displayName, infoLines, wikiUrls, false, "", "");
    }

    public SkyblockInfoClientRecipe(Identifier id, NeuItem neuItem,
                                    String displayName, List<Component> infoLines,
                                    List<String> wikiUrls, boolean isNpc,
                                    String npcDisplayName) {
        this(id, neuItem, displayName, infoLines, wikiUrls, isNpc, npcDisplayName, "");
    }

    public SkyblockInfoClientRecipe(Identifier id, NeuItem neuItem,
                                    String displayName, List<Component> infoLines,
                                    List<String> wikiUrls, boolean isNpc,
                                    String npcDisplayName, String bazaarSearch) {
        super(id, wikiUrls);
        this.neuItem = neuItem;
        this.rawDisplayName = displayName != null ? displayName : "";
        this.infoLines = infoLines != null ? List.copyOf(infoLines) : List.of();
        this.isNpc = isNpc;
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
        this.bazaarSearch = bazaarSearch != null ? bazaarSearch : "";
    }

    private static String stripFormatting(String raw) {
        String clean = TextUtil.stripColorCodes(raw);
        if (clean.endsWith(" (NPC)")) {
            clean = clean.substring(0, clean.length() - 6);
        }
        return clean.trim();
    }

    /**
     * Truncates a component to fit within {@link #TEXT_MAX_WIDTH} using a trailing ellipsis.
     */
    private static Component ellipsizeTitle(Component component) {
        var font = Minecraft.getInstance().font;
        if (font.width(component) <= TEXT_MAX_WIDTH) {
            return component;
        }
        return RecipeUiHelper.ellipsize(font, component.getString(), TEXT_MAX_WIDTH, true);
    }

    /**
     * Lazily computes the ellipsized title on first access.
     * <p>Must only be called from the render thread.</p>
     */
    private Component getTitle() {
        if (lazyTitle == null) {
            lazyTitle = ellipsizeTitle(LegacyStringParser.parse(rawDisplayName));
        }
        return lazyTitle;
    }

    /**
     * Lazily builds the display {@link SlotContent} on first access.
     * <p>Safe to call from any thread ({@code SkyblockRecipeCache.rebuild} hits it
     * from worker threads): the field is volatile and unsynchronized, so concurrent
     * callers may at worst build the stack twice, which is harmless.</p>
     */
    private SlotContent getDisplayItem() {
        if (lazyDisplayItem == null) {
            ItemStack stack = ItemStackBuilder.build(neuItem);
            lazyDisplayItem = SlotContent.of(stack);
        }
        return lazyDisplayItem;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockInfoRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(0, getDisplayItem());
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of(getDisplayItem());
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(getDisplayItem());
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        Component title = getTitle();

        // Title — centered, relative to card origin (preserves rarity color, white with shadow)
        int titleWidth = font.width(title);
        int titleX = (pos.width() - titleWidth) / 2;
        graphics.text(font, title, titleX, TITLE_Y, RecipeUiHelper.TEXT_WHITE, true);

        // Info lines — left-aligned with indent, relative to card origin (white with
        // shadow), word-wrapped to the card width up to a total rendered-line budget.
        // When the content overflows the budget, the last slot becomes an "…" line
        // and the full text is available as a tooltip over the info area.
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : infoLines) {
            wrapped.addAll(font.split(line, INFO_WRAP_WIDTH));
        }
        boolean truncated = wrapped.size() > MAX_RENDERED_LINES;
        int shown = truncated ? MAX_RENDERED_LINES - 1 : wrapped.size();

        int y = INFO_START_Y;
        for (int i = 0; i < shown; i++) {
            graphics.text(font, wrapped.get(i), INFO_TEXT_X, y, RecipeUiHelper.TEXT_WHITE, true);
            y += LINE_HEIGHT;
        }
        if (truncated) {
            graphics.text(font, Component.literal("…"), INFO_TEXT_X, y, RecipeUiHelper.TEXT_WHITE, true);
            y += LINE_HEIGHT;
            if (mouseX >= INFO_TEXT_X - 2 && mouseX < INFO_TEXT_X + INFO_WRAP_WIDTH + 4
                    && mouseY >= INFO_START_Y - 2 && mouseY < y + 2) {
                graphics.setComponentTooltipForNextFrame(font, infoLines,
                        pos.left() + mouseX, pos.top() + mouseY);
            }
        }

        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        AbstractButton sentinel = addWikiButton(screen, pos);

        if (isNpc && SKYHANNI_PRESENT && !npcDisplayName.isEmpty()) {
            Button navBtn = placeNavigateButton(screen, pos, sentinel != null);
            if (navBtn != null) {
                sentinel = navBtn;
            }
        }

        // Bazaar and navigate buttons share the same slot; they never co-occur
        // (navigate is NPC-only, bazaar is item-only).
        if (!bazaarSearch.isEmpty()) {
            Button bzBtn = placeBazaarButton(screen, pos, sentinel != null);
            if (bzBtn != null) {
                sentinel = bzBtn;
            }
        }

        return sentinel;
    }

    @Nullable
    private Button placeBazaarButton(RecipeViewScreen screen, RecipePosition pos,
                                     boolean hasWikiButton) {
        int btnY = pos.top() + getType().getDisplayHeight() - NAV_BUTTON_HEIGHT - 4;
        int btnX;
        if (hasWikiButton) {
            btnX = pos.left() + getType().getDisplayWidth() - 16 - BUTTON_GAP - NAV_BUTTON_WIDTH;
        } else {
            btnX = pos.left() + getType().getDisplayWidth() - NAV_BUTTON_WIDTH - 4;
        }

        Button btn = Button.builder(Component.literal("⚖ Bazaar"), _ -> sendBazaarCommand())
                .pos(btnX, btnY)
                .size(NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT)
                .build();
        screen.addRecipeWidget(btn);
        return btn;
    }

    private void sendBazaarCommand() {
        ClientCommandSender.send("bz " + bazaarSearch);
    }

    @SuppressWarnings({"NullableProblems", "ConstantValue"})
    @Nullable
    private Button placeNavigateButton(RecipeViewScreen screen, RecipePosition pos,
                                       boolean hasWikiButton) {
        int btnY = pos.top() + getType().getDisplayHeight() - NAV_BUTTON_HEIGHT - 4;
        int btnX;
        if (hasWikiButton) {
            // Place to the left of the wiki button
            btnX = pos.left() + getType().getDisplayWidth() - 16 - BUTTON_GAP - NAV_BUTTON_WIDTH;
        } else {
            // Place at bottom-right
            btnX = pos.left() + getType().getDisplayWidth() - NAV_BUTTON_WIDTH - 4;
        }

        Button btn = Button.builder(Component.literal("⬈ Navigate"), _ -> sendNavigateCommand())
                .pos(btnX, btnY)
                .size(NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT)
                .build();
        screen.addRecipeWidget(btn);
        return btn;
    }

    private void sendNavigateCommand() {
        ClientCommandSender.send("shnav " + stripFormatting(npcDisplayName));
    }
}
