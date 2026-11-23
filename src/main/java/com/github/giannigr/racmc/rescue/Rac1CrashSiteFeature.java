package com.github.giannigr.racmc.rescue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** One bounded, non-destructive site attempt in the selected chunk of each seeded region. */
final class Rac1CrashSiteFeature extends Feature<NoneFeatureConfiguration> {
    static final int SPACING = 20;
    static final int REGION_MARGIN = 3;
    static final int SITE_RADIUS = 3;
    private static final List<Offset> ANCHORS = candidateOffsets();
    private static final Direction[] APPROACHES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final TagKey<Biome> BIOMES =
            TagKey.create(Registries.BIOME, Rac1PodRegistration.id("clank_crash_site"));
    private static final TagKey<Block> GROUND =
            TagKey.create(Registries.BLOCK, Rac1PodRegistration.id("clank_crash_site_ground"));

    Rac1CrashSiteFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Reject the live-level path used by /place feature (and all non-Overworld decoration).
        if (!(context.level() instanceof WorldGenRegion region)
                || region.getLevel().dimension() != Level.OVERWORLD) return false;
        ChunkPos chunkPos = region.getCenter();
        if (!chunkPos.equals(new ChunkPos(context.origin()))
                || !isCandidate(region.getSeed(), chunkPos.x, chunkPos.z)) return false;
        var chunk = region.getChunk(chunkPos.x, chunkPos.z);
        if (chunk.isUpgrading() || !chunk.getAllReferences().isEmpty()
                || chunk.getAllStarts().values().stream().anyMatch(start -> start.isValid())) return false;
        IntBinaryOperator height = (x, z) -> region.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int minimum = Math.max(context.chunkGenerator().getSeaLevel(), region.getMinBuildHeight() + 2);
        BlockPos pos = findAnchor(chunkPos, height, minimum, region.getMaxBuildHeight() - 2,
                anchor -> region.getBiome(anchor).is(BIOMES)
                        && safeClearing(region, anchor, height, state -> state.is(GROUND)));
        if (pos == null) return false;
        var cells = Rac1PodFootprint.CELLS;
        var prior = new BlockState[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            prior[i] = region.getBlockState(cells.get(i).at(pos));
            if (!prior[i].isAir()) return false;
        }
        // Install the parts first; publish authority last. No cross-chunk/fluid/neighbor updates.
        int i = 1;
        for (; i < cells.size(); i++) {
            var state = Rac1PodRegistration.PART.get().defaultBlockState().setValue(Rac1PodPartBlock.PART, i);
            if (!region.setBlock(cells.get(i).at(pos), state, Block.UPDATE_CLIENTS)) break;
        }
        if (i == cells.size() && region.setBlock(pos, Rac1PodRegistration.BLOCK.get().defaultBlockState()
                .setValue(Rac1PodBlock.FULL, true), Block.UPDATE_CLIENTS)) {
            if (region.getBlockEntity(pos) instanceof Rac1PodMarker marker) {
                marker.markNatural();
                return true;
            }
            region.setBlock(pos, prior[0], Block.UPDATE_CLIENTS);
        }
        while (--i > 0) region.setBlock(cells.get(i).at(pos), prior[i], Block.UPDATE_CLIENTS);
        return false;
    }

    record Offset(int x, int z) {
        int distanceFromCenter() {
            return (x - 8) * (x - 8) + (z - 8) * (z - 8);
        }
    }

    private static List<Offset> candidateOffsets() {
        var offsets = new ArrayList<Offset>(36);
        int[] coordinates = {3, 5, 7, 8, 10, 12};
        for (int x : coordinates) for (int z : coordinates) offsets.add(new Offset(x, z));
        // Center first preserves good original centers; all tie-breakers are stable.
        offsets.sort(Comparator.comparingInt(Offset::distanceFromCenter)
                .thenComparingInt(Offset::x).thenComparingInt(Offset::z));
        return List.copyOf(offsets);
    }

    /** Tests at most 36 anchors, all at least three blocks in from the chunk edge. */
    static BlockPos findAnchor(ChunkPos chunk, IntBinaryOperator height, int minY, int maxYExclusive,
                               Predicate<BlockPos> suitable) {
        for (var offset : ANCHORS) {
            int x = chunk.getMinBlockX() + offset.x();
            int z = chunk.getMinBlockZ() + offset.z();
            // Reject the hard edge without sampling the mutable /worldborder.
            if (Math.abs((long) x) + SITE_RADIUS >= Level.MAX_LEVEL_SIZE
                    || Math.abs((long) z) + SITE_RADIUS >= Level.MAX_LEVEL_SIZE) continue;
            int y = height.applyAsInt(x, z);
            if (y < minY || y >= maxYExclusive) continue;
            BlockPos anchor = new BlockPos(x, y, z);
            if (suitable.test(anchor)) return anchor;
        }
        return null;
    }

    static boolean safeClearing(BlockGetter terrain, BlockPos anchor, IntBinaryOperator height,
                                Predicate<BlockState> naturalGround) {
        for (var cell : Rac1PodFootprint.CELLS) {
            BlockPos foot = cell.at(anchor);
            int ground = height.applyAsInt(foot.getX(), foot.getZ());
            // Land the spine on level ground; a wing may bridge one dry step down, but never
            // hang over a cliff, water or an unsupported floor.
            int maxDrop = cell.z() == 0 ? 0 : 1;
            if (ground > anchor.getY() || ground < anchor.getY() - maxDrop
                    || !walkableColumn(terrain, foot.atY(ground), height, naturalGround)) return false;
            if (!terrain.getBlockState(foot.above()).isAir()) return false;
        }
        // Level 2×2 standing area immediately outside one face. The approach is not written.
        for (Direction direction : APPROACHES) {
            BlockPos near = anchor.relative(direction, SITE_RADIUS - 1);
            BlockPos far = anchor.relative(direction, SITE_RADIUS);
            if (!walkableColumn(terrain, near, height, naturalGround)
                    || !walkableColumn(terrain, far, height, naturalGround)) continue;
            Direction right = direction.getClockWise();
            Direction left = direction.getCounterClockWise();
            if ((walkableColumn(terrain, near.relative(right), height, naturalGround)
                    && walkableColumn(terrain, far.relative(right), height, naturalGround))
                    || (walkableColumn(terrain, near.relative(left), height, naturalGround)
                    && walkableColumn(terrain, far.relative(left), height, naturalGround))) return true;
        }
        return false;
    }

    private static boolean walkableColumn(BlockGetter terrain, BlockPos foot, IntBinaryOperator height,
                                          Predicate<BlockState> naturalGround) {
        if (height.applyAsInt(foot.getX(), foot.getZ()) != foot.getY()) return false;
        BlockPos floor = foot.below();
        BlockPos support = floor.below();
        var surface = terrain.getBlockState(floor);
        var below = terrain.getBlockState(support);
        return naturalGround.test(surface) && surface.getFluidState().isEmpty()
                && surface.isFaceSturdy(terrain, floor, Direction.UP, SupportType.FULL)
                && below.getFluidState().isEmpty()
                && below.isFaceSturdy(terrain, support, Direction.UP, SupportType.FULL)
                && terrain.getBlockState(foot).isAir() && terrain.getBlockState(foot.above()).isAir();
    }

    static boolean isCandidate(long seed, int chunkX, int chunkZ) {
        int rx = Math.floorDiv(chunkX, SPACING);
        int rz = Math.floorDiv(chunkZ, SPACING);
        long mixed = mix(seed ^ mix(rx * 0x9E3779B97F4A7C15L)
                ^ mix(rz * 0xD1B54A32D192ED03L) ^ 0x79DAB81B4A409AA1L);
        int span = SPACING - REGION_MARGIN * 2;
        return Math.floorMod(chunkX, SPACING) == REGION_MARGIN + Math.floorMod(mixed, span)
                && Math.floorMod(chunkZ, SPACING) == REGION_MARGIN + Math.floorMod(mixed >>> 32, span);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
