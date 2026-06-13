package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.LegacyStringParser;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockInfoRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
    private static final int MAX_INFO_LINES = 4;

    private static final int NAV_BUTTON_WIDTH = 56;
    private static final int NAV_BUTTON_HEIGHT = 12;
    private static final int BUTTON_GAP = 4;

    private final NeuItem neuItem;
    private final String rawDisplayName;
    private final List<Component> infoLines;
    private final boolean isNpc;
    private final String npcDisplayName;
    private volatile SlotContent lazyDisplayItem;
    private volatile Component lazyTitle;

    public SkyblockInfoClientRecipe(Identifier id, NeuItem neuItem,
                                    String displayName, List<Component> infoLines,
                                    List<String> wikiUrls) {
        this(id, neuItem, displayName, infoLines, wikiUrls, false, "");
    }

    public SkyblockInfoClientRecipe(Identifier id, NeuItem neuItem,
                                    String displayName, List<Component> infoLines,
                                    List<String> wikiUrls, boolean isNpc,
                                    String npcDisplayName) {
        super(id, wikiUrls);
        this.neuItem = neuItem;
        this.rawDisplayName = displayName != null ? displayName : "";
        this.infoLines = infoLines != null ? List.copyOf(infoLines) : List.of();
        this.isNpc = isNpc;
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
    }

    private static String stripFormatting(String raw) {
        String clean = raw.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
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
        String ellipsis = "…";
        int avail = TEXT_MAX_WIDTH - font.width(Component.literal(ellipsis));
        String raw = component.getString();
        int lo = 0, hi = raw.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String sub = raw.substring(0, mid);
            Component test = LegacyStringParser.parse(sub);
            if (font.width(test) <= avail) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return LegacyStringParser.parse(raw.substring(0, lo) + "§r" + ellipsis);
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
     * <p>Must only be called from the render thread, since {@link ItemStackBuilder}
     * may touch RenderSystem for skull textures.</p>
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

        // Info lines — left-aligned with indent, relative to card origin (white with shadow)
        int y = INFO_START_Y;
        int lineCount = Math.min(infoLines.size(), MAX_INFO_LINES);
        for (int i = 0; i < lineCount; i++) {
            Component line = infoLines.get(i);
            graphics.text(font, line, 6, y, RecipeUiHelper.TEXT_WHITE, true);
            y += LINE_HEIGHT;
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

        return sentinel;
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand("shnav " + stripFormatting(npcDisplayName));
        }
    }
}
