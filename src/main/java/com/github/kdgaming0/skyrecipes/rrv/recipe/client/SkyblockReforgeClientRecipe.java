package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockRarity;
import com.github.kdgaming0.skyrecipes.core.render.mob.MobPreviewRenderer;
import com.github.kdgaming0.skyrecipes.core.render.mob.PlayerSkinRenderer;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.core.util.RarityExtractor;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockIdMatchingRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockReforgeRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import com.github.kdgaming0.skyrecipes.rrv.recipe.widget.ReforgeRarityTableWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
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
 * Client-side reforge recipe card — one card per reforge (blacksmith or stone),
 * holding every rarity's stats/ability/cost.
 *
 * <p>Redirects match on item <em>type</em> only ({@link #resultInternalNames} or the
 * stone id), never on rarity, so a reforgable item is never wrongly filtered out of
 * its reforge tab. The rarity is resolved at <em>render</em> time from the clicked
 * stack ({@link RecipeViewMenu#getOrigin()}): the card shows the stats for that item's
 * rarity, falling back to the highest tier the data covers when the item out-ranks it
 * (e.g. a DIVINE item on data that stops at MYTHIC). When the origin is the stone
 * itself, or absent (browsing the reforge category), all rarities are shown as a
 * compact table.</p>
 *
 * <p>{@code seedStacks} exist only so RRV's item-keyed recipe cache surfaces this
 * recipe as a candidate for every applicable vanilla item — the redirect checks then
 * do the exact SkyBlock-id matching. They are never rendered. SkyBlock-ID lookups
 * ({@code SkyblockRecipeCache}) bypass the seeds entirely and resolve this card via
 * {@link SkyblockIdMatchingRecipe}, since one seed per vanilla item cannot represent
 * every applicable SkyBlock item.</p>
 */
public class SkyblockReforgeClientRecipe extends AbstractSkyblockClientRecipe
        implements SkyblockIdMatchingRecipe {

    private static final Identifier TEXTURE_ITEM =
            IdentifierUtil.skyRecipes("textures/gui/type/reforge_item.png");
    private static final Identifier TEXTURE_NPC =
            IdentifierUtil.skyRecipes("textures/gui/type/reforge_npc.png");

    // Blacksmith NPC render area: 24×36 at (5,5)
    private static final int NPC_X = 4;
    private static final int NPC_Y = 7;
    private static final int NPC_W = 24;
    private static final int NPC_H = 30;

    // Text layout (relative to recipe card)
    private static final int TEXT_LEFT = 32;
    private static final int NAME_Y = 6;
    private static final int LINE_HEIGHT = 10;

    // Generic ability key used by NEU when a reforge shares one ability across rarities.
    private static final String GENERIC_ABILITY_KEY = "ability";

    private final ItemStack stoneStack;
    private final String stoneInternalName;
    private final ItemStack npcSkinStack;
    private final boolean isBlacksmith;
    private final String reforgeName;
    private final Set<String> resultInternalNames;
    private final List<ItemStack> seedStacks;

    /** Available tiers in ascending order; drives clamping and the all-rarities table. */
    private final List<SkyblockRarity> orderedRarities;
    private final Map<String, Map<String, Number>> statsByRarity;
    private final Map<String, String> abilityByRarity;
    private final Map<String, Integer> costByRarity;

    // SlotContent is mutated by RRV's menu, so each recipe needs its own instances.
    @Nullable
    private List<SlotContent> cachedResults;
    @Nullable
    private List<SlotContent> cachedIngredients;

    // Lazy per-rarity text caches (rendered content depends on the clicked stack's rarity).
    private final Map<String, List<String>> statStringsByRarity = new HashMap<>();
    @Nullable
    private List<String> cachedTableLines;

    // Scrollable table for the all-rarities view; recreated per placement.
    @Nullable
    private ReforgeRarityTableWidget tableWidget;

    /**
     * @param resultInternalNames all SkyBlock ids this reforge applies to
     * @param seedStacks          one stack per distinct vanilla item among the results
     * @param requiredRarities    the rarities the data defines for this reforge
     * @param statsByRarity       rarity name → stat name → value
     * @param abilityByRarity     rarity name → ability text (may hold a generic {@code "ability"} key)
     * @param costByRarity        rarity name → coin cost
     */
    public SkyblockReforgeClientRecipe(
            Identifier id,
            ItemStack stoneStack,
            ItemStack npcSkinStack,
            boolean isBlacksmith,
            String reforgeName,
            Set<String> resultInternalNames,
            List<ItemStack> seedStacks,
            List<String> requiredRarities,
            Map<String, Map<String, Number>> statsByRarity,
            Map<String, String> abilityByRarity,
            Map<String, Integer> costByRarity,
            List<String> wikiUrls) {
        super(id, wikiUrls);
        this.stoneStack = stoneStack != null ? stoneStack : ItemStack.EMPTY;
        String stoneId = isBlacksmith ? null : SkyblockIdExtractor.extract(this.stoneStack);
        this.stoneInternalName = stoneId != null ? stoneId : "";
        this.npcSkinStack = npcSkinStack != null ? npcSkinStack : ItemStack.EMPTY;
        this.isBlacksmith = isBlacksmith;
        this.reforgeName = reforgeName != null ? reforgeName : "";
        this.resultInternalNames = resultInternalNames != null ? resultInternalNames : Set.of();
        this.seedStacks = seedStacks != null ? seedStacks : List.of();
        this.statsByRarity = statsByRarity != null ? statsByRarity : Map.of();
        this.abilityByRarity = abilityByRarity != null ? abilityByRarity : Map.of();
        this.costByRarity = costByRarity != null ? costByRarity : Map.of();
        this.orderedRarities = toOrderedRarities(requiredRarities);
    }

    private static List<SkyblockRarity> toOrderedRarities(@Nullable List<String> rarities) {
        if (rarities == null || rarities.isEmpty()) {
            return List.of();
        }
        List<SkyblockRarity> parsed = new ArrayList<>(rarities.size());
        for (String r : rarities) {
            try {
                parsed.add(SkyblockRarity.valueOf(r));
            } catch (IllegalArgumentException ignored) {
                // Unknown rarity string in the data — skip it rather than fail the recipe.
            }
        }
        parsed.sort(Comparator.comparingInt(Enum::ordinal));
        return List.copyOf(parsed);
    }

    // ── ReliableClientRecipe core ──────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockReforgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (!isBlacksmith && !stoneStack.isEmpty()) {
            ctx.bindSlot(0, SlotContent.of(stoneStack));
        }
    }

    /**
     * Stone (when present) plus the seed stacks, so RRV also surfaces this recipe
     * when pressing "uses" on a reforgable item.
     */
    @Override
    public List<SlotContent> getIngredients() {
        if (cachedIngredients == null) {
            List<SlotContent> contents = new ArrayList<>(2);
            if (!isBlacksmith && !stoneStack.isEmpty()) {
                contents.add(SlotContent.of(stoneStack));
            }
            if (!seedStacks.isEmpty()) {
                contents.add(SlotContent.of(seedStacks));
            }
            cachedIngredients = List.copyOf(contents);
        }
        return cachedIngredients;
    }

    /**
     * Seed stacks plus the stone itself, so clicking a stone surfaces its reforge.
     * Never rendered — this card draws its own content.
     */
    @Override
    public List<SlotContent> getResults() {
        if (cachedResults == null) {
            List<ItemStack> stacks = new ArrayList<>(seedStacks);
            if (!isBlacksmith && !stoneStack.isEmpty()) {
                stacks.add(stoneStack);
            }
            cachedResults = stacks.isEmpty() ? List.of() : List.of(SlotContent.of(stacks));
        }
        return cachedResults;
    }

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        return matchesItem(stack);
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        return matchesItem(stack);
    }

    /**
     * Type-only match: the reforge applies to the stack, or the stack is the stone
     * itself. Rarity is never checked here — it only decides what the card renders.
     */
    private boolean matchesItem(ItemStack stack) {
        String itemId = SkyblockIdExtractor.extract(stack);
        return itemId != null && matchesSkyblockId(itemId);
    }

    @Override
    public boolean matchesSkyblockId(String skyblockId) {
        if (!isBlacksmith && skyblockId.equals(stoneInternalName)) {
            return true;
        }
        return resultInternalNames.contains(skyblockId);
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

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
                //noinspection ConstantValue
                if (renderInfo != null) {
                    Identifier texture = renderInfo.playerSkin().body().texturePath();
                    //noinspection ConstantValue
                    if (texture != null) {
                        PlayerSkinRenderer.render(graphics, texture,
                                pos.left() + NPC_X, pos.top() + NPC_Y,
                                NPC_W, NPC_H,
                                MobPreviewRenderer.getRotationAngle());
                    }
                }
            }
        }

        SkyblockRarity display = resolveDisplayRarity(screen.getMenu().getOrigin());
        Font font = Minecraft.getInstance().font;

        if (display != null) {
            renderSingleRarity(graphics, font, pos, display);
        } else {
            renderAllRarities(graphics, font, pos);
        }

        maintainButtons(screen, pos);
    }

    /**
     * Resolves which rarity to display for the clicked stack, or {@code null} to show
     * every rarity (stone clicked, empty origin, or an unexpected non-matching origin).
     */
    @Nullable
    private SkyblockRarity resolveDisplayRarity(@Nullable ItemStack origin) {
        if (orderedRarities.isEmpty() || origin == null || origin.isEmpty()) {
            return null;
        }
        String id = SkyblockIdExtractor.extract(origin);
        if (id == null || (!isBlacksmith && id.equals(stoneInternalName)) || !resultInternalNames.contains(id)) {
            return null;
        }
        SkyblockRarity itemRarity = RarityExtractor.extract(origin);
        return SkyblockRarity.highestAtMost(orderedRarities, itemRarity);
    }

    private void renderSingleRarity(GuiGraphicsExtractor graphics, Font font, RecipePosition pos,
                                    SkyblockRarity rarity) {
        String rarityName = rarity.name();
        int y = NAME_Y;

        // Name + rarity — right of the slot/NPC area
        String nameStr = "§e" + reforgeName + " §7- " + RecipeUiHelper.rarityColorCode(rarityName) + rarityName;
        int nameMaxWidth = pos.width() - TEXT_LEFT - 5;
        for (String line : RecipeUiHelper.wrapText(font, nameStr, nameMaxWidth)) {
            graphics.text(font, Component.literal(line), TEXT_LEFT, y, RecipeUiHelper.TEXT_WHITE, true);
            y += LINE_HEIGHT;
        }

        // Subtitle
        String subStr = subtitleFor(rarityName);
        if (!subStr.isEmpty()) {
            for (String line : RecipeUiHelper.wrapText(font, subStr, nameMaxWidth)) {
                graphics.text(font, Component.literal(line), TEXT_LEFT, y, 0xFFAAAAAA, true);
                y += LINE_HEIGHT;
            }
        }

        y += 10; // gap before body
        y = Math.max(y, bodyMinY());
        int bodyMaxWidth = pos.width() - 10;

        // Ability
        String ability = abilityFor(rarityName);
        if (!ability.isEmpty()) {
            for (String line : RecipeUiHelper.wrapText(font, ability, bodyMaxWidth)) {
                graphics.text(font, Component.literal(line), 5, y, RecipeUiHelper.TEXT_WHITE, true);
                y += LINE_HEIGHT;
            }
        }

        // Stats
        for (String statStr : statStringsFor(rarityName)) {
            for (String line : RecipeUiHelper.wrapText(font, statStr, bodyMaxWidth)) {
                graphics.text(font, Component.literal(line), 5, y, RecipeUiHelper.TEXT_WHITE, true);
                y += LINE_HEIGHT;
            }
        }
    }

    /**
     * Header only — the per-rarity table below it is drawn by
     * {@link ReforgeRarityTableWidget}, placed in {@link #placeButtons}.
     */
    private void renderAllRarities(GuiGraphicsExtractor graphics, Font font, RecipePosition pos) {
        int y = NAME_Y;

        // Name — right of the slot/NPC area
        int nameMaxWidth = pos.width() - TEXT_LEFT - 5;
        for (String line : RecipeUiHelper.wrapText(font, "§e" + reforgeName, nameMaxWidth)) {
            graphics.text(font, Component.literal(line), TEXT_LEFT, y, RecipeUiHelper.TEXT_WHITE, true);
            y += LINE_HEIGHT;
        }
        String subStr = isBlacksmith ? "§7Blacksmith" : "§7Reforge Stone";
        graphics.text(font, Component.literal(subStr), TEXT_LEFT, y, 0xFFAAAAAA, true);
    }

    /** Y (card-relative) where the all-rarities table starts, below the wrapped header. */
    private int allRaritiesBodyTop(Font font, RecipePosition pos) {
        int nameMaxWidth = pos.width() - TEXT_LEFT - 5;
        int nameLines = Math.max(1, RecipeUiHelper.wrapText(font, "§e" + reforgeName, nameMaxWidth).size());
        int y = NAME_Y + (nameLines + 1) * LINE_HEIGHT + 6; // +1 for the subtitle line
        return Math.max(y, bodyMinY());
    }

    /**
     * In the all-rarities view, adds the scrollable table widget (plus the inherited
     * wiki button, when present). Recreated on each placement so geometry follows the
     * current card position; scroll offset carries over when RRV drops and re-adds it.
     */
    @Override
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        AbstractWidget inherited = super.placeButtons(screen, pos);
        if (resolveDisplayRarity(screen.getMenu().getOrigin()) != null) {
            return inherited; // single-rarity view: no table widget
        }
        Font font = Minecraft.getInstance().font;
        int bodyTop = allRaritiesBodyTop(font, pos);
        double previousScroll = tableWidget != null ? tableWidget.scrollAmount() : 0.0;
        // x/y offsets cancel the widget's 4px inner padding so text lands at the
        // same coordinates the direct-drawn body used (5 from the card edge).
        tableWidget = new ReforgeRarityTableWidget(
                pos.left() + 1, pos.top() + bodyTop - 4,
                pos.width() - 12, pos.height() - bodyTop - 1,
                font, tableLines());
        tableWidget.setScrollAmount(previousScroll);
        screen.addRecipeWidget(tableWidget);
        return tableWidget;
    }

    @Override
    public void fadeRecipe() {
        super.fadeRecipe();
        tableWidget = null;
    }

    private int bodyMinY() {
        return isBlacksmith ? (NPC_Y + NPC_H + 3) : 35;
    }

    // ── Cached text builders ───────────────────────────────────────────────────

    private String subtitleFor(String rarityName) {
        if (isBlacksmith) {
            return "§7Blacksmith";
        }
        int cost = costByRarity.getOrDefault(rarityName, 0);
        return cost > 0 ? "§7Cost: §6" + RecipeUiHelper.formatCompactNumber(cost) + " coins" : "";
    }

    private String abilityFor(String rarityName) {
        if (abilityByRarity.isEmpty()) {
            return "";
        }
        String perRarity = abilityByRarity.get(rarityName);
        if (perRarity != null && !perRarity.isEmpty()) {
            return perRarity;
        }
        String generic = abilityByRarity.get(GENERIC_ABILITY_KEY);
        return generic != null ? generic : "";
    }

    private List<String> statStringsFor(String rarityName) {
        return statStringsByRarity.computeIfAbsent(rarityName, r -> {
            Map<String, Number> stats = statsByRarity.getOrDefault(r, Map.of());
            List<String> lines = new ArrayList<>(stats.size());
            for (Map.Entry<String, Number> entry : stats.entrySet()) {
                lines.add("§7" + formatStatName(entry.getKey()) + ": " + formatStatValue(entry.getValue().doubleValue()));
            }
            return List.copyOf(lines);
        });
    }

    /**
     * One condensed line per available rarity: the coloured tier label followed by
     * its stats inline. Built once; the origin cannot change which rarities exist.
     */
    private List<String> tableLines() {
        if (cachedTableLines == null) {
            List<String> lines = new ArrayList<>(orderedRarities.size());
            for (SkyblockRarity rarity : orderedRarities) {
                String rarityName = rarity.name();
                Map<String, Number> stats = statsByRarity.getOrDefault(rarityName, Map.of());
                StringBuilder sb = new StringBuilder(RecipeUiHelper.rarityColorCode(rarityName));
                sb.append(rarityName).append("§7:");
                for (Map.Entry<String, Number> entry : stats.entrySet()) {
                    sb.append(' ').append(formatStatValue(entry.getValue().doubleValue()))
                            .append(' ').append(formatStatName(entry.getKey()));
                }
                if (stats.isEmpty() && !isBlacksmith) {
                    int cost = costByRarity.getOrDefault(rarityName, 0);
                    if (cost > 0) {
                        sb.append(" §6").append(RecipeUiHelper.formatCompactNumber(cost)).append(" coins");
                    }
                }
                lines.add(sb.toString());
            }
            cachedTableLines = List.copyOf(lines);
        }
        return cachedTableLines;
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
        String prefix = value >= 0 ? "§a+" : "§c";
        String number = value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
        return prefix + number;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public Set<String> getResultInternalNames() {
        return resultInternalNames;
    }

    public String getStoneInternalName() {
        return stoneInternalName;
    }
}
