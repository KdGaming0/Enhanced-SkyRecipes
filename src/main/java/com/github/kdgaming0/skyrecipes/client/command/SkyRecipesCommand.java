package com.github.kdgaming0.skyrecipes.client.command;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.data.CacheLayout;
import com.github.kdgaming0.skyrecipes.core.data.PipelineStatus;
import com.github.kdgaming0.skyrecipes.core.data.RuntimeDataManager;
import com.github.kdgaming0.skyrecipes.core.data.RuntimeUpdateService;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.mojang.brigadier.context.CommandContext;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class SkyRecipesCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("skyrecipes")
                            .then(ClientCommands.literal("status")
                                    .executes(SkyRecipesCommand::executeStatus)
                            )
                            .then(ClientCommands.literal("refresh")
                                    .then(ClientCommands.literal("repoData")
                                            .executes(SkyRecipesCommand::executeRefresh)
                                    )
                            )
                            .then(ClientCommands.literal("import")
                                    .executes(SkyRecipesCommand::executeImport)
                            )
                            .then(literal("config")
                                    .executes(SkyRecipesCommand::executeOpenConfig))
            );
        });
    }

    private static int executeStatus(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        PipelineStatus.Snapshot snap = PipelineStatus.snapshot();
        long now = System.currentTimeMillis();

        // Headline: state + what it means for the user
        String stateColor = switch (snap.state()) {
            case READY -> "§a";
            case DEGRADED -> "§e";
            case FAILED -> "§c";
            default -> "§b";
        };
        String meaning = switch (snap.state()) {
            case READY -> "everything is working";
            case DEGRADED -> "items and recipes work, but the last update hit a problem";
            case FAILED -> "no data loaded — the item list is empty";
            case STARTING -> "initializing";
            case DOWNLOADING -> "downloading SkyBlock data";
            case COMPILING -> "building the data cache";
            case LOADING -> "loading data";
            case GENERATING -> "generating recipes";
            case INJECTING -> "registering recipes";
        };
        StringBuilder header = new StringBuilder("§7SkyRecipes: ")
                .append(stateColor).append(snap.state()).append(" §8— §7").append(meaning);
        if (snap.refreshInProgress()) header.append(" §8(refresh running in background)");
        if (snap.providerOnlyMode()) header.append(" §6[provider-only mode]");
        src.sendFeedback(Component.literal(header.toString()));

        // Data: count, age, source
        if (snap.itemCount() > 0) {
            String source = snap.etag() != null && snap.etag().startsWith("manual-import-")
                    ? "manual import"
                    : "GitHub";
            src.sendFeedback(Component.literal(String.format(
                    "§7Data: §f%,d§7 items §8· §7built %s §8· §7from %s",
                    snap.itemCount(), agoOrNever(snap.dataBuildTimestamp(), now), source)));
        } else {
            src.sendFeedback(Component.literal("§7Data: §cnot loaded"));
        }

        // Recipes/stacks: failure counts only when there is something to act on
        if (snap.recipeCount() > 0) {
            StringBuilder recipes = new StringBuilder(String.format(
                    "§7Recipes: §f%,d§7 injected §8· §7Stacks: §f%,d",
                    snap.injectedRecipes(), snap.stackCount()));
            if (snap.skippedRecipes() > 0) recipes.append(String.format(" §8· §c%,d skipped", snap.skippedRecipes()));
            if (snap.recipeFailures() > 0) recipes.append(String.format(" §8· §c%,d parse failures", snap.recipeFailures()));
            if (snap.stackFailures() > 0) recipes.append(String.format(" §8· §c%,d stack failures", snap.stackFailures()));
            src.sendFeedback(Component.literal(recipes.toString()));
        }

        // Update schedule: retry countdown wins over the regular interval estimate
        StringBuilder checks = new StringBuilder("§7Updates: last check ")
                .append(agoOrNever(snap.lastCheckTime(), now));
        if (snap.nextRetryTime() > now) {
            checks.append(" §8· §7retrying in §f").append(formatDuration(snap.nextRetryTime() - now));
        } else if (snap.lastCheckTime() > 0) {
            long nextCheck = snap.lastCheckTime() + SkyRecipesConfig.dataRefreshIntervalMinutes * 60_000L;
            if (nextCheck > now) {
                checks.append(" §8· §7next check in ~§f").append(formatDuration(nextCheck - now));
            }
        }
        src.sendFeedback(Component.literal(checks.toString()));

        if (snap.lastErrorMessage() != null) {
            src.sendFeedback(Component.literal(String.format(
                    "§7Last problem §8(%s, %s)§7: §c%s",
                    snap.lastErrorStage(), agoOrNever(snap.lastErrorTime(), now), snap.lastErrorMessage())));
        }

        sendActionHint(src, snap);

        if (!snap.stageDurationsMs().isEmpty()) {
            StringBuilder stages = new StringBuilder("§8Last build: ");
            long total = 0;
            boolean first = true;
            for (Map.Entry<String, Long> e : snap.stageDurationsMs().entrySet()) {
                if (!first) stages.append(" §8· ");
                stages.append("§7").append(e.getKey()).append(" §f").append(formatMs(e.getValue()));
                total += e.getValue();
                first = false;
            }
            stages.append(" §8· §7total §f").append(formatMs(total));
            src.sendFeedback(Component.literal(stages.toString()));
        }
        return 1;
    }

    /** One state-specific "what to do next" line, with clickable commands. */
    private static void sendActionHint(FabricClientCommandSource src, PipelineStatus.Snapshot snap) {
        switch (snap.state()) {
            case FAILED -> {
                src.sendFeedback(Component.literal("§7Fix: ")
                        .append(clickToRun("§b§nretry the download", "/skyrecipes refresh repoData",
                                "Click to run /skyrecipes refresh repoData"))
                        .append(Component.literal("§7, or drop the data ZIP into §fskyrecipes/import/§7 and "))
                        .append(clickToRun("§b§nimport it", "/skyrecipes import",
                                "Click to run /skyrecipes import"))
                        .append(Component.literal("§7.")));
            }
            case DEGRADED -> {
                src.sendFeedback(Component.literal("§7Fix: current data keeps working — ")
                        .append(clickToRun("§b§nretry with a clean refresh", "/skyrecipes refresh repoData",
                                "Click to run /skyrecipes refresh repoData"))
                        .append(Component.literal("§7 whenever you want.")));
            }
            case STARTING, DOWNLOADING, COMPILING, LOADING, GENERATING, INJECTING ->
                    src.sendFeedback(Component.literal("§7Setup is running — items appear when it finishes."));
            default -> {
            }
        }
    }

    private static Component clickToRun(String label, String command, String hover) {
        return Component.literal(label).setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static String formatMs(long ms) {
        return ms < 1000 ? ms + "ms" : String.format("%.1fs", ms / 1000.0);
    }

    private static int executeRefresh(CommandContext<FabricClientCommandSource> ctx) {
        RuntimeDataManager dm = SkyRecipes.getDataManager();
        if (dm == null) {
            ctx.getSource().sendError(Component.literal("SkyRecipes data manager not ready."));
            return 0;
        }

        ctx.getSource().sendFeedback(Component.literal(
                "§bSkyRecipes: Starting full refresh — re-downloading and rebuilding from scratch. "
                        + "Current data keeps working until the new data is ready."));

        dm.forceRefreshNow(SkyRecipesCommand::sendChat,
                () -> sendChat(String.format("§aSkyRecipes: Refresh complete — §f%,d§a items loaded.",
                        loadedItemCount(dm))),
                () -> sendChat("§cSkyRecipes: Refresh failed. Run §f/skyrecipes status§c for details."));
        return 1;
    }

    private static int executeImport(CommandContext<FabricClientCommandSource> ctx) {
        RuntimeDataManager dm = SkyRecipes.getDataManager();
        CacheLayout layout = SkyRecipes.getCacheLayout();
        if (dm == null || layout == null) {
            ctx.getSource().sendError(Component.literal("SkyRecipes data manager not ready."));
            return 0;
        }

        Path zip = layout.findNewestImportZip();
        if (zip == null) {
            ctx.getSource().sendError(Component.literal(
                    "§cSkyRecipes: no ZIP found in §f" + layout.importDir() + "§c."));
            Component link = Component.literal("§b§nclick here to download it")
                    .setStyle(Style.EMPTY.withClickEvent(
                            new ClickEvent.OpenUrl(URI.create(RuntimeUpdateService.NEU_REPO_URL))));
            ctx.getSource().sendFeedback(Component.literal("§7Get the SkyBlock data ZIP (")
                    .append(link)
                    .append(Component.literal("§7 or copy it from a friend), drop it in that folder, "
                            + "then run §f/skyrecipes import§7 again.")));
            return 0;
        }

        ctx.getSource().sendFeedback(Component.literal(
                "§bSkyRecipes: Importing §f" + zip.getFileName() + "§b..."));

        dm.importFromZip(zip, SkyRecipesCommand::sendChat,
                () -> sendChat(String.format("§aSkyRecipes: Import complete — §f%,d§a items loaded.",
                        loadedItemCount(dm))),
                () -> sendChat("§cSkyRecipes: Import failed. Run §f/skyrecipes status§c for details."));
        return 1;
    }

    /**
     * Chat delivery for pipeline callbacks, which fire on the update-service
     * thread; the player reference is resolved at fire time on the render thread.
     */
    private static void sendChat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        //noinspection ConstantValue
        if (mc != null) mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
        });
    }

    private static int loadedItemCount(RuntimeDataManager dm) {
        ItemRegistry reg = dm.getItemRegistry();
        return reg != null ? reg.size() : 0;
    }

    private static String agoOrNever(long epochMs, long now) {
        if (epochMs <= 0) return "never";
        return formatDuration(Math.max(0, now - epochMs)) + " ago";
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        if (hours < 48) return hours + "h " + (minutes % 60) + "m";
        return (hours / 24) + "d " + (hours % 24) + "h";
    }

    private static int executeOpenConfig(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null) {
            ctx.getSource().sendError(
                    Component.literal("§c[SkyRecipes] You must be in-game to open the config menu."));
            return 0;
        }

        client.schedule(() -> {
            try {
                client.setScreen(MidnightConfig.getScreen(client.screen, SkyRecipes.MOD_ID));
            } catch (Exception e) {
                SkyRecipes.LOGGER.error("Failed to open config menu", e);
            }
        });

        ctx.getSource().sendFeedback(Component.literal("§a[SkyRecipes] Opening configuration menu..."));
        return 1;
    }
}
