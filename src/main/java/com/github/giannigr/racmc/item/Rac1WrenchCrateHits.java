package com.github.giannigr.racmc.item;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Single ordinary-crate destruction seam, reached only from the native validated harvest path.
 * No rewards are rolled here. Consolidation installs the genuine P24 transaction once.
 */
public final class Rac1WrenchCrateHits {
    @FunctionalInterface
    public interface Handler {
        /** Return whether this exact expected crate was removed; never also call vanilla. */
        boolean destroy(ServerPlayer player, BlockPos pos, BlockState expectedState);
    }

    private static Handler handler;

    private Rac1WrenchCrateHits() { }

    /** Bootstrap-only integration, not a per-hit listener list. */
    public static void install(Handler value) {
        if (handler != null) {
            throw new IllegalStateException("The wrench crate handler is already installed.");
        }
        handler = Objects.requireNonNull(value);
    }

    public static boolean destroy(Level level, BlockPos pos, BlockState expectedState,
                                  net.minecraft.world.entity.player.Player player,
                                  BooleanSupplier vanilla) {
        if (level.isClientSide) {
            return vanilla.getAsBoolean(); // native prediction only, never reward
        }
        if (!com.github.giannigr.racmc.actions.WrenchActions.validHarvestLifecycle(level, pos, player)) {
            return false;
        }
        if (handler == null) {
            return vanilla.getAsBoolean(); // standalone crate base: no rewards
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        boolean removed = level.getBlockState(pos) == expectedState
                && handler.destroy(serverPlayer, pos, expectedState);
        if (!removed) {
            // Native destroyBlock can acknowledge success even when a block's removal hook
            // returns false. Undo predicted air when the integration transaction denies/fails.
            serverPlayer.connection.send(new ClientboundBlockUpdatePacket(level, pos));
            var entity = level.getBlockEntity(pos);
            if (entity != null && entity.getUpdatePacket() != null) {
                serverPlayer.connection.send(entity.getUpdatePacket());
            }
        }
        return removed;
    }
}
