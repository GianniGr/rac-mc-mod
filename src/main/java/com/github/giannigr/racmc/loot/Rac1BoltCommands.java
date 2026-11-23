package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.RacMcConstants;
import com.mojang.brigadier.arguments.LongArgumentType;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only grants for testing and administration. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1BoltCommands {
    private Rac1BoltCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bolts")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes(context -> grant(context.getSource(), LongArgumentType.getLong(context, "amount"),
                                List.of(context.getSource().getPlayerOrException()))))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> grant(context.getSource(),
                                        LongArgumentType.getLong(context, "amount"),
                                        EntityArgument.getPlayers(context, "targets"))))));
    }

    private static int grant(CommandSourceStack source, long amount, Collection<ServerPlayer> targets) {
        int credited = 0;
        for (var player : targets) {
            var ledger = Rac1BoltLedger.get(player.serverLevel());
            if (!ledger.credit(player.getUUID(), amount)) {
                source.sendFailure(Component.translatable("commands.racmc.bolts.overflow",
                        player.getDisplayName()));
                continue;
            }
            Rac1BoltNetwork.send(player, ledger, false);
            long total = ledger.balance(player.getUUID());
            source.sendSuccess(() -> Component.translatable("commands.racmc.bolts.added",
                    amount, player.getDisplayName(), total), false);
            credited++;
        }
        return credited;
    }
}
