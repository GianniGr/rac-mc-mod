package com.github.giannigr.racmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of a crate of the game.
 *
 * <p>The block entity holds no state of its own: the crate of the game is a picture, and the
 * picture is drawn out of the mesh that is in the cache of the assets. The block entity exists
 * because the renderer of the block entities of the game of blocks is the place where a crate can
 * be drawn.
 */
public class Rac1CrateBlockEntity extends BlockEntity {

    /**
     * Creates the block entity of a crate.
     *
     * @param pos the place of the block
     * @param state the state of the block
     */
    public Rac1CrateBlockEntity(BlockPos pos, BlockState state) {
        super(RacMcBlockEntities.CRATE.get(), pos, state);
    }
}
