package com.github.kdgaming0.skyrecipes.rrv.recipe.util;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.client.hypixel.HypixelLocationTracker;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIslandNames;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Optional, dependency-free integration with SkyHanni's {@code /shnav} command.
 *
 * <p>SkyHanni stores navigation names in its live island graph; they are not derived from
 * NEU internal names. Rather than link against SkyHanni's private graph implementation, this
 * class asks Fabric's public client-command dispatcher for {@code /shnav}'s suggestions. That
 * keeps the integration compatible with SkyHanni repository updates and makes absence or an
 * incompatible command fail closed instead of navigating to a substring-matched wrong NPC.</p>
 */
public final class SkyHanniNpcNavigator {
    private static final String SKYHANNI_MOD_ID = "skyhanni";
    private static final String SHORT_COMMAND = "shnav";
    private static final String LONG_COMMAND = "shnavigate";

    private static final Pattern NPC_SUFFIX = Pattern.compile(
            "\\s*\\((?:rift\\s+)?npc\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_QUALIFIER = Pattern.compile("\\s*\\([^()]+\\)\\s*$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Confirmed NEU-to-SkyHanni naming differences. An override is only used when the value is
     * present in SkyHanni's live suggestions, so stale entries cannot send an unverified command.
     */
    private static final Map<String, String> VERIFIED_ALIASES = Map.ofEntries(
            Map.entry("ALCINA_NPC", "Alicina"),
            Map.entry("APPRENTICE_NPC", "Carpenter Apprentice"),
            Map.entry("ARGOFAY_THREEBROTHER_1_RIFT_NPC", "Argofay Threebrother #3"),
            Map.entry("ARGOFAY_THREEBROTHER_2_RIFT_NPC", "Argofay Threebrother #2"),
            Map.entry("ARGOFAY_THREEBROTHER_3_RIFT_NPC", "Argofay Threebrother #1"),
            Map.entry("ARGOFAY_TRAFFICKER_1_RIFT_NPC", "Argofay Trafficker/Leggings"),
            Map.entry("ARGOFAY_TRAFFICKER_2_RIFT_NPC", "Argofay Trafficker/Boots"),
            Map.entry("ARGOFAY_TRAFFICKER_3_RIFT_NPC", "Argofay Trafficker/Chestplate"),
            Map.entry("ARGOFAY_TRAFFICKER_4_RIFT_NPC", "Argofay Trafficker/Helmet"),
            Map.entry("BRIGETTE_NPC", "Britette"),
            Map.entry("DR_PHEAR_RIFT_NPC", "Dr Phear"),
            Map.entry("FAIRYLOSOPHER_NPC", "Fairylosopher at the Top"),
            Map.entry("MINE_MERCHANT_NPC", "Mining Merchant"),
            Map.entry("QUEEN_NYX_NPC", "Queen Nyx (Mage)"),
            Map.entry("RORNORA_NPC", "Tornora"),
            Map.entry("SERAPHINE_RIFT_NPC", "Transfigured Seraphine")
    );

    private SkyHanniNpcNavigator() {
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(SKYHANNI_MOD_ID);
    }

    /**
     * Resolves the NPC against SkyHanni's current live suggestions, then sends an exact name.
     * Resolution is asynchronous because Brigadier suggestion providers return a future.
     */
    public static void navigate(NeuItem item, String displayName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isAvailable() || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }

        String requestedName = normalizeNeuName(displayName);
        if (requestedName.isEmpty()) {
            showFailure(minecraft, "This NPC has no usable name.");
            return;
        }

        CommandDispatcher<FabricClientCommandSource> dispatcher = ClientCommands.getActiveDispatcher();
        if (dispatcher == null) {
            showFailure(minecraft, "SkyHanni's navigation command is not ready.");
            return;
        }

        String command = dispatcher.getRoot().getChild(SHORT_COMMAND) != null
                ? SHORT_COMMAND
                : dispatcher.getRoot().getChild(LONG_COMMAND) != null ? LONG_COMMAND : null;
        if (command == null) {
            showFailure(minecraft, "SkyHanni's navigation command was not found.");
            return;
        }

        Object suggestionProvider = minecraft.getConnection().getSuggestionsProvider();
        if (!(suggestionProvider instanceof FabricClientCommandSource source)) {
            showFailure(minecraft, "SkyHanni navigation suggestions are unavailable.");
            return;
        }

        ParseResults<FabricClientCommandSource> parsed = dispatcher.parse(command + " ", source);
        dispatcher.getCompletionSuggestions(parsed).whenComplete((suggestions, error) ->
                minecraft.execute(() -> {
                    if (error != null) {
                        SkyRecipes.LOGGER.debug("Could not read SkyHanni /{} suggestions", command, error);
                        showFailure(minecraft, "Could not read SkyHanni's NPC locations.");
                        return;
                    }

                    Map<String, String> liveNames = new LinkedHashMap<>();
                    for (Suggestion suggestion : suggestions.getList()) {
                        String name = suggestion.getText().trim();
                        if (!name.isEmpty()) {
                            liveNames.putIfAbsent(key(name), name);
                        }
                    }

                    String internalName = item != null ? item.internalName() : "";
                    String resolved = resolveLiveName(internalName, requestedName, liveNames);
                    if (resolved == null) {
                        showFallback(minecraft, item, requestedName, command);
                        return;
                    }
                    ClientCommandSender.send(command + " " + resolved);
                }));
    }

    @Nullable
    static String resolveLiveName(String internalName, String requestedName, Map<String, String> liveNames) {
        String exact = liveNames.get(key(requestedName));
        if (exact != null) {
            return exact;
        }

        String alias = VERIFIED_ALIASES.get(internalName);
        if (alias != null) {
            String liveAlias = liveNames.get(key(alias));
            if (liveAlias != null) {
                return liveAlias;
            }
        }

        // SkyHanni sometimes adds a unique disambiguator such as "(Mage)". Accept it only
        // when exactly one live suggestion reduces to the requested name.
        String qualifiedMatch = null;
        for (String liveName : liveNames.values()) {
            String withoutQualifier = TRAILING_QUALIFIER.matcher(liveName).replaceFirst("").trim();
            if (!key(withoutQualifier).equals(key(requestedName))) {
                continue;
            }
            if (qualifiedMatch != null && !qualifiedMatch.equalsIgnoreCase(liveName)) {
                return null;
            }
            qualifiedMatch = liveName;
        }
        return qualifiedMatch;
    }

    static String normalizeNeuName(String raw) {
        String clean = TextUtil.stripColorCodes(raw);
        clean = NPC_SUFFIX.matcher(clean).replaceFirst("");
        return WHITESPACE.matcher(clean.trim()).replaceAll(" ");
    }

    private static String key(String value) {
        return WHITESPACE.matcher(TextUtil.stripColorCodes(value).trim())
                .replaceAll(" ")
                .toLowerCase(Locale.ROOT);
    }

    private static void showFallback(Minecraft minecraft, NeuItem item, String npcName, String command) {
        String expectedIsland = item != null ? normalizeIsland(item.island()) : "";
        String currentIsland = HypixelLocationTracker.currentIslandCode();

        if (!expectedIsland.isEmpty()
                && currentIsland != null
                && !expectedIsland.equals(currentIsland)) {
            showFailure(minecraft, npcName + " is on " + SkyblockIslandNames.displayName(expectedIsland)
                    + ", but you are currently on " + SkyblockIslandNames.displayName(currentIsland)
                    + ". Travel there and try again.");
            return;
        }

        if (currentIsland == null || expectedIsland.isEmpty()) {
            String destination = expectedIsland.isEmpty()
                    ? "the NPC's island"
                    : SkyblockIslandNames.displayName(expectedIsland);
            showFailure(minecraft, "SkyHanni has no exact location for " + npcName
                    + ". SkyRecipes could not confirm your current island; travel to "
                    + destination + " and try again.");
            return;
        }

        if (item.x() == 0 && item.y() == 0 && item.z() == 0) {
            showFailure(minecraft, "SkyHanni has no exact location for " + npcName
                    + ", and NEU has no fallback coordinates.");
            return;
        }

        String coordinateCommand = "/" + command + " " + item.x() + " " + item.y() + " " + item.z();
        Component fallback = Component.literal("[SkyRecipes] ").withStyle(ChatFormatting.RED)
                .append(Component.literal("SkyHanni has no exact location for " + npcName + ". ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("[Navigate using coordinates]").setStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(coordinateCommand))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Run " + coordinateCommand)))));
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(fallback);
        }
    }

    private static String normalizeIsland(String island) {
        return island == null ? "" : island.trim().toLowerCase(Locale.ROOT);
    }

    private static void showFailure(Minecraft minecraft, String message) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                    Component.literal("[SkyRecipes] ").withStyle(ChatFormatting.RED)
                            .append(Component.literal(message).withStyle(ChatFormatting.YELLOW))
            );
        }
    }
}
