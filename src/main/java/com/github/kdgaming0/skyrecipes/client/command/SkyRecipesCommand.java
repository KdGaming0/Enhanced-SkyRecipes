package com.github.kdgaming0.skyrecipes.client.command;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.data.PipelineStatus;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public class SkyRecipesCommand {
    public static void register() {
        SuggestionProvider<FabricClientCommandSource> TARGET_SUGGESTER =
                (CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) -> {
                    builder.suggest("repoData");
                    builder.suggest("hypixel");
                    builder.suggest("all");
                    return builder.buildFuture();
                };

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("skyrecipes")
                            .then(ClientCommands.literal("status")
                                    .executes(SkyRecipesCommand::executeStatus)
                            )
                            .then(ClientCommands.literal("refresh")
                                    .then(ClientCommands.argument("target", StringArgumentType.word())
                                            .suggests(TARGET_SUGGESTER)
                                            .executes((CommandContext<FabricClientCommandSource> ctx) -> executeRefresh(ctx, 0))
                                            .then(ClientCommands.argument("timeoutSeconds", IntegerArgumentType.integer(0))
                                                    .executes((CommandContext<FabricClientCommandSource> ctx) -> {
                                                        int timeout = IntegerArgumentType.getInteger(ctx, "timeoutSeconds");
                                                        return executeRefresh(ctx, timeout);
                                                    })
                                            )
                                    )
                            )
            );
        });
    }

    private static int executeStatus(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        PipelineStatus.Snapshot snap = PipelineStatus.snapshot();
        long now = System.currentTimeMillis();

        String stateColor = switch (snap.state()) {
            case READY -> "§a";
            case DEGRADED -> "§e";
            case FAILED -> "§c";
            default -> "§b";
        };
        StringBuilder header = new StringBuilder("§7SkyRecipes: ").append(stateColor).append(snap.state());
        if (snap.refreshInProgress()) header.append(" §7(refreshing in background)");
        if (snap.providerOnlyMode()) header.append(" §6[provider-only mode]");
        src.sendFeedback(Component.literal(header.toString()));

        if (snap.itemCount() > 0) {
            String etag = snap.etag() != null && snap.etag().length() > 12
                    ? snap.etag().substring(0, 12) + "…" : String.valueOf(snap.etag());
            src.sendFeedback(Component.literal(String.format(
                    "§7Data: §f%,d items§7, built %s (etag %s)",
                    snap.itemCount(), agoOrNever(snap.dataBuildTimestamp(), now), etag)));
        } else {
            src.sendFeedback(Component.literal("§7Data: §cnot loaded"));
        }

        if (snap.recipeCount() > 0) {
            src.sendFeedback(Component.literal(String.format(
                    "§7Recipes: §f%,d§7 injected (%d skipped, %d parse failures) | Stacks: §f%,d§7 (%d build failures)",
                    snap.injectedRecipes(), snap.skippedRecipes(), snap.recipeFailures(),
                    snap.stackCount(), snap.stackFailures())));
        }

        StringBuilder checks = new StringBuilder("§7Last update check: ")
                .append(agoOrNever(snap.lastCheckTime(), now));
        if (snap.nextRetryTime() > now) {
            checks.append(" §7| Next retry: in ").append(formatDuration(snap.nextRetryTime() - now));
        }
        src.sendFeedback(Component.literal(checks.toString()));

        if (snap.lastErrorMessage() != null) {
            src.sendFeedback(Component.literal(String.format(
                    "§7Last error: §c[%s, %s] %s",
                    snap.lastErrorStage(), agoOrNever(snap.lastErrorTime(), now), snap.lastErrorMessage())));
        } else {
            src.sendFeedback(Component.literal("§7Last error: §anone"));
        }

        if (!snap.stageDurationsMs().isEmpty()) {
            src.sendFeedback(Component.literal("§7Stages (ms): §f" + snap.stageDurationsMs()));
        }
        return 1;
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

    private static int executeRefresh(CommandContext<FabricClientCommandSource> ctx, int timeoutSeconds) {
        FabricClientCommandSource src = ctx.getSource();

        if (SkyRecipes.getDataManager() == null) {
            src.sendError(Component.literal("SkyRecipes data manager not ready."));
            return 0;
        }

        String target = StringArgumentType.getString(ctx, "target");

        switch (target.toLowerCase()) {
            case "repodata" -> {
                SkyRecipes.getDataManager().getUpdateService().checkNow();
                src.sendFeedback(Component.literal("§aSkyRecipes: Repo data refresh queued."));
            }
            case "hypixel" -> {
                src.sendFeedback(Component.literal("§eSkyRecipes: Hypixel refresh is not implemented yet. "
                        + "Hypixel stats refresh automatically every 24 h."));
            }
            case "all" -> {
                SkyRecipes.getDataManager().getUpdateService().checkNow();
                src.sendFeedback(Component.literal("§aSkyRecipes: Full refresh queued."));
            }
            default -> {
                src.sendError(Component.literal("Unknown refresh target: " + target));
                return 0;
            }
        }

        if (timeoutSeconds > 0) {
            src.sendFeedback(Component.literal("§eSkyRecipes: Action scheduled in " + timeoutSeconds + "s (not implemented)."));
        }

        return 1;
    }
}

