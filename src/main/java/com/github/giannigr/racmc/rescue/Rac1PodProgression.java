package com.github.giannigr.racmc.rescue;

import com.github.giannigr.racmc.gear.Rac1GearServer;
import net.minecraft.server.level.ServerPlayer;

/** Called only after native pod admission; durable authority stays with Gear. */
final class Rac1PodProgression {
    enum Result { NEWLY_UNLOCKED, ALREADY_UNLOCKED, DENIED }

    private Rac1PodProgression() {}

    static Result rescue(ServerPlayer player, Runnable onCommit) {
        return switch (Rac1GearServer.rescueClank(player, onCommit)) {
            case NEWLY_UNLOCKED -> Result.NEWLY_UNLOCKED;
            case ALREADY_UNLOCKED -> Result.ALREADY_UNLOCKED;
            case DENIED -> Result.DENIED;
        };
    }
}
