package com.github.giannigr.racmc.rescue;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stable shared collision; native right-click only, independent of local art. */
public final class Rac1PodBlock extends BaseEntityBlock {
    static final BooleanProperty FULL = BooleanProperty.create("full");
    private static final MapCodec<Rac1PodBlock> CODEC = simpleCodec(Rac1PodBlock::new);

    Rac1PodBlock(Properties properties) {
        super(properties);
        // Old saves have no "full" property and are intentionally left at the old footprint.
        registerDefaultState(stateDefinition.any().setValue(FULL, false));
    }

    @Override protected MapCodec<? extends Rac1PodBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FULL);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Rac1PodMarker(pos, state); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Rac1PodFootprint.shape(Rac1PodFootprint.full(state), 0);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return Rac1PodInteraction.use(state, level, pos, player, hit, 0);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return Rac1PodWreck.breakable(level, pos, 0) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        return Rac1PodWreck.remove(level, pos, state, player, 0);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity entity, ItemStack tool) {
        Rac1PodWreck.drop(level, player, pos, state, tool);
    }
}
