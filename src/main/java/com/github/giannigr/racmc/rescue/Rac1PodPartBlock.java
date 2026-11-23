package com.github.giannigr.racmc.rescue;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Structural hull segment, never an independent rescue source. */
public final class Rac1PodPartBlock extends Block {
    static final IntegerProperty PART = IntegerProperty.create("part", 1, 8);
    private static final MapCodec<Rac1PodPartBlock> CODEC = simpleCodec(Rac1PodPartBlock::new);

    Rac1PodPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 1));
    }

    @Override protected MapCodec<? extends Rac1PodPartBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        // Lighting can run off-thread before the natural marker is available. Never resolve it here.
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int part = state.getValue(PART);
        BlockPos anchor = Rac1PodFootprint.partAnchor(pos, part);
        if (anchor == null) return Shapes.empty();
        // Vanilla workers can ask for shapes while the same chunk is still promoting.
        // Only the game thread may establish the loaded anchor; workers use the local box.
        if (level instanceof ServerLevel server && !server.getServer().isSameThread()) {
            return Rac1PodFootprint.shape(true, part);
        }
        var local = Rac1PodFootprint.loadedView(level, anchor);
        if (local == null || !Rac1PodFootprint.full(local.getBlockState(anchor))) return Shapes.empty();
        var marker = Rac1PodFootprint.markerAt(local, anchor);
        if (marker == null || (!marker.isNatural() && !marker.isDecorative())) return Shapes.empty();
        return Rac1PodFootprint.shape(true, part);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return Rac1PodInteraction.use(state, level, pos, player, hit, state.getValue(PART));
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return Rac1PodWreck.breakable(level, pos, state.getValue(PART))
                ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        return Rac1PodWreck.remove(level, pos, state, player, state.getValue(PART));
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity entity, ItemStack tool) {
        Rac1PodWreck.drop(level, player, pos, state, tool);
    }
}
