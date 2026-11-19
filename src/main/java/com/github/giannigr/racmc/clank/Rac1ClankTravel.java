package com.github.giannigr.racmc.clank;

import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;

/** Common-side bridge: no client class in a dedicated-server constant pool. */
public final class Rac1ClankTravel {
    private static Predicate<Player> prediction=player->false;
    private Rac1ClankTravel() {}
    public static void prediction(Predicate<Player> delegate) {prediction=java.util.Objects.requireNonNull(delegate);}
    public static boolean replace(Player player) {
        return player.level().isClientSide() ? prediction.test(player)
                : player instanceof net.minecraft.server.level.ServerPlayer server
                && Rac1ClankRuntime.ownsTravel(server);
    }
}
