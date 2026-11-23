package com.github.giannigr.racmc.rescue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Nine independent native cells; the center owns the only block entity and rescue authority. */
public final class Rac1PodFootprint {
    public static final List<Cell> CELLS = List.of(new Cell(0, 0), new Cell(-1, 0), new Cell(1, 0),
            new Cell(-1, -1), new Cell(0, -1), new Cell(1, -1),
            new Cell(-1, 1), new Cell(0, 1), new Cell(1, 1));

    public record Cell(int x, int z) {
        public BlockPos at(BlockPos anchor) { return anchor.offset(x, 0, z); }
        BlockPos anchorOf(BlockPos part) { return part.offset(-x, 0, -z); }
    }

    private static final VoxelShape LEGACY = Block.box(1, 0, 1, 15, 8, 15);
    private static final List<VoxelShape> SHAPES = List.of(Block.box(0, 0, 0, 16, 16, 16),
            Block.box(0, 0, 0, 16, 14, 16), Block.box(0, 0, 0, 16, 14, 16),
            Block.box(3, 0, 6, 16, 10, 16), Block.box(0, 0, 0, 16, 10, 16),
            Block.box(0, 0, 7, 11, 8, 16),
            Block.box(3, 0, 0, 16, 10, 10), Block.box(0, 0, 0, 16, 10, 16),
            Block.box(0, 0, 0, 11, 8, 9));

    private Rac1PodFootprint() {}

    public static VoxelShape shape(boolean full, int index) {
        return full ? SHAPES.get(index) : LEGACY;
    }

    public static boolean full(BlockState state) {
        return state.getBlock() instanceof Rac1PodBlock && state.getValue(Rac1PodBlock.FULL);
    }

    public static boolean naturalAt(BlockGetter level, BlockPos anchor) {
        var marker = markerAt(level, anchor);
        return marker != null && marker.isNatural();
    }

    static Rac1PodMarker markerAt(BlockGetter level, BlockPos anchor) {
        return level.getBlockEntity(anchor) instanceof Rac1PodMarker marker && !marker.isRemoved() ? marker : null;
    }

    static BlockGetter loadedView(BlockGetter view, BlockPos pos) {
        var chunk = new ChunkPos(pos);
        if (view instanceof ServerLevel server) return server.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (view instanceof Level level) return level.getChunkSource().getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false);
        return view;
    }

    // Use read-only block access. Call only after verifying the layout is wholly in a loaded chunk.
    public static boolean complete(BlockGetter level, BlockPos anchor) {
        if (!sameChunk(anchor) || !full(level.getBlockState(anchor))) return false;
        for (int i = 1; i < CELLS.size(); i++) {
            if (!matchesPart(level.getBlockState(CELLS.get(i).at(anchor)), i)) return false;
        }
        return true;
    }

    static boolean sameChunk(BlockPos anchor) {
        int x = Math.floorMod(anchor.getX(), 16), z = Math.floorMod(anchor.getZ(), 16);
        return x > 0 && x < 15 && z > 0 && z < 15;
    }

    static boolean matchesPart(BlockState state, int part) {
        return state.getBlock() instanceof Rac1PodPartBlock && state.getValue(Rac1PodPartBlock.PART) == part;
    }

    public static boolean isPresentPart(BlockGetter level, BlockPos pos, int part) {
        return partAnchor(pos, part) != null && matchesPart(level.getBlockState(pos), part);
    }

    static BlockPos partAnchor(BlockPos pos, int part) {
        var anchor = CELLS.get(part).anchorOf(pos);
        // Command-created cells can be badly aligned. Never check neighbor chunks for these.
        return sameChunk(anchor) && new ChunkPos(anchor).equals(new ChunkPos(pos)) ? anchor : null;
    }
}
