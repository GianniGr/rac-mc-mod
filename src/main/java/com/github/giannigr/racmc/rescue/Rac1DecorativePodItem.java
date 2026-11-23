package com.github.giannigr.racmc.rescue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.Shapes;

/** Installs only an empty hull. Stack block-state and block-entity data are never applied. */
final class Rac1DecorativePodItem extends Item {
    Rac1DecorativePodItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext use) {
        var context = new BlockPlaceContext(use);
        var player = context.getPlayer();
        // Multi-cell placement is available to builders, not via a single-cell Adventure override.
        if (player == null || !player.getAbilities().mayBuild || player.isSpectator()) return InteractionResult.FAIL;
        var level = context.getLevel();
        var anchor = context.getClickedPos();
        if (!Rac1PodFootprint.sameChunk(anchor) || !Level.isInSpawnableBounds(anchor)) return InteractionResult.FAIL;
        var local = Rac1PodFootprint.loadedView(level, anchor);
        if (local == null || level.isOutsideBuildHeight(anchor) || level.isOutsideBuildHeight(anchor.below())
                || !local.getBlockState(anchor.below()).isFaceSturdy(local, anchor.below(), Direction.UP)) {
            return InteractionResult.FAIL;
        }
        var cells = Rac1PodFootprint.CELLS;
        var prior = new BlockState[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            var pos = cells.get(i).at(anchor);
            prior[i] = local.getBlockState(pos);
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                    || !prior[i].isAir() || !prior[i].getFluidState().isEmpty()
                    || !level.isUnobstructed(null, Shapes.block().move(pos.getX(), pos.getY(), pos.getZ()))) {
                return InteractionResult.FAIL;
            }
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!server.getServer().isSameThread()) return InteractionResult.FAIL;

        // ItemStack.useOn captures these writes for NeoForge's multi-block placement veto/rollback.
        int flags = Block.UPDATE_ALL;
        int i = 1;
        for (; i < cells.size(); i++) {
            var state = Rac1PodRegistration.PART.get().defaultBlockState().setValue(Rac1PodPartBlock.PART, i);
            if (!level.setBlock(cells.get(i).at(anchor), state, flags)) break;
        }
        var parent = Rac1PodRegistration.BLOCK.get().defaultBlockState().setValue(Rac1PodBlock.FULL, true);
        if (i == cells.size() && level.setBlock(anchor, parent, flags)) {
            var marker = Rac1PodFootprint.markerAt(local, anchor);
            if (marker != null) {
                marker.markDecorative();
                var sound = SoundType.METAL;
                level.playSound(null, anchor, sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1) / 2, sound.getPitch() * .8F);
                level.gameEvent(GameEvent.BLOCK_PLACE, anchor, GameEvent.Context.of(player, parent));
                context.getItemInHand().consume(1, player);
                return InteractionResult.CONSUME;
            }
            level.setBlock(anchor, prior[0], flags);
        }
        while (--i > 0) level.setBlock(cells.get(i).at(anchor), prior[i], flags);
        return InteractionResult.FAIL;
    }
}
