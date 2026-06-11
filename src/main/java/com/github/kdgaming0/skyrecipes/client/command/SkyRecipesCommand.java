package com.github.kdgaming0.skyrecipes.client.command;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
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
                src.sendFeedback(Component.literal("§aSkyRecipes: Hypixel data refresh requested."));
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

