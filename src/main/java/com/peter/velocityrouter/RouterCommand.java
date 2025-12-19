package com.peter.velocityrouter;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.Component;

public class RouterCommand {

    public static BrigadierCommand create(VelocityRouter router) {
        LiteralCommandNode<CommandSource> routerCommand = LiteralArgumentBuilder
                .<CommandSource>literal("velocityrouter")
                .then(BrigadierCommand.literalArgumentBuilder("reload").executes(context -> {
                    router.reloadConfig();
                    context.getSource().sendMessage(Component.text("Reloading config . . ."));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(BrigadierCommand.literalArgumentBuilder("last")
                        .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                                    router.proxyServer.getAllPlayers().forEach(player -> builder.suggest(
                                player.getUsername()
                            ));
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                                    String playerId = context.getArgument("player", String.class);
                                    router.proxyServer.getPlayer(playerId).ifPresent(player -> {
                                        if (router.routingTable == null) {
                                            context.getSource().sendMessage(Component.text("No routing table"));
                                            return;
                                        }
                                        String serverId = router.routingTable.getServerForPlayer(player);
                                        if (serverId.equals("")) {
                                            context.getSource().sendMessage(Component.text("No routing data for player "+playerId));
                                            return;
                                        }
                                        context.getSource().sendMessage(Component.text(String.format("Last server for player %s was %s", playerId, serverId)));
                                    });
                        return Command.SINGLE_SUCCESS;
                })))
                .build();
        return new BrigadierCommand(routerCommand);
    }
}
