package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockRarity;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.render.mob.MobPreviewRenderer;
import com.github.kdgaming0.skyrecipes.core.render.mob.PlayerSkinRenderer;
import com.github.kdgaming0.skyrecipes.core.util.RarityExtractor;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockReforgeRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Client-side reforge recipe card for one specific rarity.
 *
 * <p>Displays the stat boosts and ability this reforge gives to an item of the
 * recipe's fixed rarity. Uses {@code reforge_item.png} for stone reforges and
 * {@code reforge_npc.png} for blacksmith reforges.</p>
 *
 * <p>{@link #redirectsAsResult} filters by rarity + item type, so clicking a
 * reforgable item shows only matching reforge recipes for that item's rarity.</p>
 */
public class SkyblockReforgeClientRecipe extends AbstractSkyblockClientRecipe {

    private static final Identifier TEXTURE_ITEM =
            Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/reforge_item.png");
    private static final Identifier TEXTURE_NPC =
            Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/reforge_npc.png");

    // Blacksmith NPC render area: 24×36 at (5,5)
    private static final int NPC_X = 4;
    private static final int NPC_Y = 7;
    private static final int NPC_W = 24;
    private static final int NPC_H = 30;

    // Text layout (relative to recipe card)
    private static final int TEXT_LEFT = 32;
    private static final int NAME_Y = 6;
    private static final int LINE_HEIGHT = 10;

    private final ItemStack stoneStack;
    private final ItemStack npcSkinStack;
    private final boolean isBlacksmith;
    private final String reforgeName;
    private final String rarity;
    private final List<String> resultInternalNames;
    private final Map<String, Number> stats;
    private final String ability;
    private final int cost;

    @Nullable
    private SlotContent cachedResults;

    // Cached raw text strings
    @Nullable
    private String cachedName;
    @Nullable
    private String cachedSubtitle;
    @Nullable
    private List<String> cachedStatStrings;

    public SkyblockReforgeClientRecipe(
            Identifier id,
            ItemStack stoneStack,
            ItemStack npcSkinStack,
            boolean isBlacksmith,
            String reforgeName,
            String rarity,
            List<String> resultInternalNames,
            Map<String, Number> stats,
            String ability,
            int cost,
            List<String> wikiUrls) {
        super(id, wikiUrls);
        this.stoneStack = stoneStack != null ? stoneStack : ItemStack.EMPTY;
        this.npcSkinStack = npcSkinStack != null ? npcSkinStack : ItemStack.EMPTY;
        this.isBlacksmith = isBlacksmith;
        this.reforgeName = reforgeName != null ? reforgeName : "";
        this.rarity = rarity != null ? rarity : "COMMON";
        this.resultInternalNames = resultInternalNames != null ? List.copyOf(resultInternalNames) : List.of();
        this.stats = stats != null ? stats : Map.of();
        this.ability = ability != null ? ability : "";
        this.cost = cost;
    }

    // ── ReliableClientRecipe core ──────────────────────────────────────────────

    /**
     * Splits {@code text} into lines that each fit within {@code maxWidth} pixels.
     * Prefers word boundaries; falls back to mid-word splits. Preserves leading
     * § colour/formatting codes across line breaks.
     */
    private static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        String remaining = text;
        String activeCodes = "";

        while (!remaining.isEmpty()) {
            // Absorb leading codes on this segment
            String leading = extractLeadingCodes(remaining);
            if (!leading.isEmpty()) {
                activeCodes = updateActiveCodes(activeCodes, leading);
            }
            String content = remaining.substring(leading.length());

            String prefix = activeCodes;
            int prefixVis = font.width(prefix); // should be 0, but safe
            int fit = fitLength(font, prefix + content, maxWidth);
            int contentFit = Math.max(1, fit - prefix.length());

            int splitAt = contentFit;
            if (contentFit < content.length()) {
                while (splitAt > 0 && content.charAt(splitAt - 1) != ' ') {
                    splitAt--;
                }
                if (splitAt == 0) {
                    splitAt = contentFit; // force mid-word
                }
            }

            String line = (prefix + content.substring(0, splitAt)).stripTrailing();
            if (!line.isEmpty()) {
                lines.add(line);
            }

            activeCodes = updateActiveCodes(activeCodes, content.substring(0, splitAt));
            remaining = content.substring(splitAt).stripLeading();
        }

        return lines;
    }

    /**
     * Returns the §-code prefix at the start of {@code text}, if any.
     */
    private static String extractLeadingCodes(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i + 1 < text.length() && text.charAt(i) == '§') {
            sb.append(text.charAt(i));
            sb.append(text.charAt(i + 1));
            i += 2;
        }
        return sb.toString();
    }

    /**
     * Recomputes the active colour/formatting codes after consuming {@code text}.
     */
    private static String updateActiveCodes(String current, String text) {
        String combined = current + text;
        String color = null;
        boolean bold = false, italic = false, under = false, strike = false, obf = false;

        for (int i = 0; i + 1 < combined.length(); i++) {
            if (combined.charAt(i) == '§') {
                char c = combined.charAt(i + 1);
                switch (c) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                         'a', 'b', 'c', 'd', 'e', 'f',
                         'A', 'B', 'C', 'D', 'E', 'F' -> color = String.valueOf(c);
                    case 'k', 'K' -> obf = true;
                    case 'l', 'L' -> bold = true;
                    case 'm', 'M' -> strike = true;
                    case 'n', 'N' -> under = true;
                    case 'o', 'O' -> italic = true;
                    case 'r', 'R' -> {
                        color = null;
                        bold = italic = under = strike = obf = false;
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        if (color != null) sb.append('§').append(color);
        if (obf) sb.append("§k");
        if (bold) sb.append("§l");
        if (strike) sb.append("§m");
        if (under) sb.append("§n");
        if (italic) sb.append("§o");
        return sb.toString();
    }

    /**
     * Binary-search the number of leading characters of {@code text} that fit.
     */
    private static int fitLength(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.length();
        }
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(text.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static String formatStatName(String key) {
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String part = parts[i];
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static String formatStatValue(double value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockReforgeRecipeType.INSTANCE;
    }

    // ── Cached text builders ───────────────────────────────────────────────────

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (!isBlacksmith && !stoneStack.isEmpty()) {
            ctx.bindSlot(0, SlotContent.of(stoneStack));
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        if (!isBlacksmith && !stoneStack.isEmpty()) {
            return List.of(SlotContent.of(stoneStack));
        }
        return List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        if (cachedResults == null) {
            cachedResults = buildResults();
        }
        return cachedResults.isEmpty() ? List.of() : List.of(cachedResults);
    }

    // ── Text wrapping ──────────────────────────────────────────────────────────

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        String itemId = SkyblockIdExtractor.extract(stack);
        if (itemId == null || !resultInternalNames.contains(itemId)) {
            return false;
        }
        SkyblockRarity stackRarity = RarityExtractor.extract(stack);
        return this.rarity.equals(stackRarity.name());
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        String itemId = SkyblockIdExtractor.extract(stack);
        if (itemId == null) return false;

        if (!isBlacksmith && itemId.equals(getStoneInternalName())) {
            return true;
        }
        if (resultInternalNames.contains(itemId)) {
            SkyblockRarity stackRarity = RarityExtractor.extract(stack);
            return this.rarity.equals(stackRarity.name());
        }
        return false;
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // Background texture
        Identifier bg = isBlacksmith ? TEXTURE_NPC : TEXTURE_ITEM;
        graphics.blit(RenderPipelines.GUI_TEXTURED, bg,
                0, 0, 0, 0,
                pos.width(), pos.height(),
                pos.width(), pos.height());

        // Blacksmith NPC skin preview
        if (isBlacksmith && !npcSkinStack.isEmpty()) {
            ResolvableProfile profile = npcSkinStack.get(DataComponents.PROFILE);
            if (profile != null) {
                PlayerSkinRenderCache cache = Minecraft.getInstance().playerSkinRenderCache();
                PlayerSkinRenderCache.RenderInfo renderInfo = cache.getOrDefault(profile);
                if (renderInfo != null) {
                    Identifier texture = renderInfo.playerSkin().body().texturePath();
                    if (texture != null) {
                        PlayerSkinRenderer.render(graphics, texture,
                                pos.left() + NPC_X, pos.top() + NPC_Y,
                                NPC_W, NPC_H,
                                MobPreviewRenderer.getRotationAngle());
                    }
                }
            }
        }

        Font font = Minecraft.getInstance().font;
        int y = NAME_Y;

        // Name (wrapped) — right side, does not overlap with NPC/slot area
        String nameStr = getNameString();
        int nameMaxWidth = pos.width() - TEXT_LEFT - 5;
        for (String line : wrapText(font, nameStr, nameMaxWidth)) {
            graphics.text(font, Component.literal(line), TEXT_LEFT, y, RecipeUiHelper.TEXT_WHITE, true);
            y += LINE_HEIGHT;
        }

        // Subtitle (wrapped)
        String subStr = getSubtitleString();
        if (!subStr.isEmpty()) {
            for (String line : wrapText(font, subStr, nameMaxWidth)) {
                graphics.text(font, Component.literal(line), TEXT_LEFT, y, 0xFFAAAAAA, true);
                y += LINE_HEIGHT;
            }
        }

        y += 10; // gap before body

        // Ensure body text doesn't overlap with blacksmith/slot render area
        int bodyMinY = isBlacksmith ? (NPC_Y + NPC_H + 3) : 35;
        if (y < bodyMinY) {
            y = bodyMinY;
        }

        int bodyMaxWidth = pos.width() - 10;

        // Ability (wrapped)
        if (!ability.isEmpty()) {
            for (String line : wrapText(font, ability, bodyMaxWidth)) {
                graphics.text(font, Component.literal(line), 5, y, RecipeUiHelper.TEXT_WHITE, true);
                y += LINE_HEIGHT;
            }
        }

        // Stats (wrapped)
        for (String statStr : getStatStrings()) {
            for (String line : wrapText(font, statStr, bodyMaxWidth)) {
                graphics.text(font, Component.literal(line), 5, y, RecipeUiHelper.TEXT_WHITE, true);
                y += LINE_HEIGHT;
            }
        }

        maintainButtons(screen, pos);
    }

    private String getNameString() {
        if (cachedName == null) {
            cachedName = "§e" + reforgeName + " §7- " + RecipeUiHelper.rarityColorCode(rarity) + rarity;
        }
        return cachedName;
    }

    // ── Result building ────────────────────────────────────────────────────────

    private String getSubtitleString() {
        if (cachedSubtitle == null) {
            if (isBlacksmith) {
                cachedSubtitle = "§7Blacksmith";
            } else if (cost > 0) {
                cachedSubtitle = "§7Cost: §6" + RecipeUiHelper.formatCompactNumber(cost) + " coins";
            } else {
                cachedSubtitle = "";
            }
        }
        return cachedSubtitle;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<String> getStatStrings() {
        if (cachedStatStrings == null) {
            List<String> lines = new ArrayList<>(stats.size());
            for (Map.Entry<String, Number> entry : stats.entrySet()) {
                double value = entry.getValue().doubleValue();
                String prefix = value >= 0 ? "§a+" : "§c";
                lines.add("§7" + formatStatName(entry.getKey()) + ": " + prefix + formatStatValue(value));
            }
            cachedStatStrings = List.copyOf(lines);
        }
        return cachedStatStrings;
    }

    private SlotContent buildResults() {
        if (resultInternalNames.isEmpty()) {
            return SlotContent.of();
        }

        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) {
            return SlotContent.of();
        }

        List<ItemStack> stacks = new ArrayList<>();
        Set<net.minecraft.world.item.Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int limit = 0;
        for (String name : resultInternalNames) {
            if (limit++ > 32) break;
            var opt = registry.getByInternalName(name);
            if (opt.isEmpty()) continue;
            ItemStack stack = ItemStackBuilder.build(opt.get());
            if (!stack.isEmpty() && seen.add(stack.getItem())) {
                stacks.add(stack);
            }
        }
        return stacks.isEmpty() ? SlotContent.of() : SlotContent.of(stacks);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public List<String> getResultInternalNames() {
        return resultInternalNames;
    }

    public String getStoneInternalName() {
        if (isBlacksmith) return "";
        String id = SkyblockIdExtractor.extract(stoneStack);
        return id != null ? id : "";
    }
}
