package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Provisional operator-only session fixture. Not progression, an item or persistent ownership. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1ClankTestCommands {
    private Rac1ClankTestCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var target = Commands.argument("player", EntityArgument.player());
        for (var module : Rac1ClankEquipment.Module.values()) {
            String name = module == Rac1ClankEquipment.Module.NONE ? "unequip"
                    : module.name().toLowerCase(java.util.Locale.ROOT);
            target.then(Commands.literal(name).executes(context -> {
                var player = EntityArgument.getPlayer(context, "player");
                if (!Rac1ClankServer.configureTest(context.getSource(), player, module)) {
                    context.getSource().sendFailure(Component.literal("Clank test setup denied."));
                    return 0;
                }
                context.getSource().sendSuccess(() -> Component.literal("Session-only Clank: "
                        + name + " for " + player.getGameProfile().getName()
                        + ". Cleared on death/logout/server stop; not permanent progression."), true);
                return 1;
            }));
        }
        event.getDispatcher().register(Commands.literal("clanktest")
                .requires(source -> source.hasPermission(2)).then(target));
    }
}
