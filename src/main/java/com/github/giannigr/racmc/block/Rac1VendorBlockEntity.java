package com.github.giannigr.racmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Unsaved, per-client visual state; the server never imports assets or references client types. */
public final class Rac1VendorBlockEntity extends BlockEntity {
    private final Rac1VendorVisualState visual = new Rac1VendorVisualState();

    public Rac1VendorBlockEntity(BlockPos pos, BlockState state) {
        super(RacMcBlockEntities.VENDOR.get(), pos, state);
    }

    public Rac1VendorVisualState visual() {
        return visual;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  Rac1VendorBlockEntity entity) {
        if (!level.isClientSide) {
            return;
        }
        // Multiplayer default: nearest alive non-spectator within the vertical envelope.
        // An overhead spectator/dead player must not suppress another eligible player.
        double nearest = Double.POSITIVE_INFINITY;
        for (Player player : level.players()) {
            double x = player.getX() - (pos.getX() + 0.5);
            double z = player.getZ() - (pos.getZ() + 0.5);
            nearest = Math.min(nearest, entity.visual.playerDistanceSquared(player.isAlive(),
                    player.isSpectator(), x, player.getY() - pos.getY(), z));
        }
        entity.visual.tick(entity.visual.contains(nearest, 0));
    }

}
