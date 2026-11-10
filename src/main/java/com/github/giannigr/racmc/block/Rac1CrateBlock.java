package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.asset.Rac1CrateDefinition;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

/**
 * A crate of the game as a block of the game of blocks.
 *
 * <p>The block holds no geometry of its own: the crate of the game is drawn by the renderer of
 * the block entity, out of the mesh that was imported out of the disc. The block itself is
 * invisible and holds the space of one block.
 *
 * <p>The block is a solid block: it fills its space, it can be built on and it can be broken. It
 * drops nothing, because the crates of the mod are a picture of the game and not a part of the
 * game of blocks yet. The bolts, the health and the ammunition of the game are not part of this
 * block either.
 */
public class Rac1CrateBlock extends Block implements EntityBlock {

    /** The description of the crate the block shows. */
    private final Rac1CrateDefinition definition;

    /**
     * Creates the block of a crate.
     *
     * @param definition the description of the crate the block shows
     * @param properties the properties of the block
     */
    public Rac1CrateBlock(Rac1CrateDefinition definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    /**
     * The description of the crate the block shows.
     *
     * @return the description
     */
    public Rac1CrateDefinition definition() {
        return definition;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // The crate of the game is drawn by the renderer of the block entity, so the block
        // itself draws nothing.
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Rac1CrateBlockEntity(pos, state);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // The crates of the mod drop nothing: they are a picture of the game and not a part of
        // the game of blocks yet.
        return List.of();
    }
}
