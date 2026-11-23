package com.github.giannigr.racmc.rescue;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

class Rac1CrashSiteTest {
    @Test
    void oneSeededAttemptPerRegionInAllQuadrantsAndNoAdjacentRegionClumping() {
        for (long seed : new long[] {0, -27, 7439, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (int rx = -4; rx <= 4; rx++) {
                for (int rz = -4; rz <= 4; rz++) {
                    int sites = 0;
                    for (int x = 0; x < Rac1CrashSiteFeature.SPACING; x++) {
                        for (int z = 0; z < Rac1CrashSiteFeature.SPACING; z++) {
                            if (!Rac1CrashSiteFeature.isCandidate(seed,
                                    rx * Rac1CrashSiteFeature.SPACING + x,
                                    rz * Rac1CrashSiteFeature.SPACING + z)) continue;
                            sites++;
                            assertTrue(x >= Rac1CrashSiteFeature.REGION_MARGIN);
                            assertTrue(x < Rac1CrashSiteFeature.SPACING - Rac1CrashSiteFeature.REGION_MARGIN);
                            assertTrue(z >= Rac1CrashSiteFeature.REGION_MARGIN);
                            assertTrue(z < Rac1CrashSiteFeature.SPACING - Rac1CrashSiteFeature.REGION_MARGIN);
                        }
                    }
                    assertEquals(1, sites);
                }
            }
        }
    }

    @Test
    void footprintsAreWhollyInOwnedChunkAndChoiceChangesWithSeed() {
        var chunk = new ChunkPos(-7, -2);
        int radius = Rac1CrashSiteFeature.SITE_RADIUS;
        var tried = new ArrayList<BlockPos>();
        assertNull(Rac1CrashSiteFeature.findAnchor(chunk, (x, z) -> 70, 63, 300, anchor -> {
            for (int dx : new int[] {-radius, radius}) for (int dz : new int[] {-radius, radius}) {
                assertEquals(chunk, new ChunkPos(anchor.offset(dx, 0, dz)));
            }
            tried.add(anchor);
            return false;
        }));
        assertEquals(36, tried.size());
        assertEquals(36, Set.copyOf(tried).size());
        var center = new BlockPos(chunk.getMiddleBlockX(), 70, chunk.getMiddleBlockZ());
        assertEquals(center, tried.getFirst());
        assertEquals(center, Rac1CrashSiteFeature.findAnchor(chunk, (x, z) -> 70, 63, 300, pos -> true));
        var offCenter = tried.get(4);
        assertEquals(offCenter, Rac1CrashSiteFeature.findAnchor(chunk, (x, z) -> 70, 63, 300, offCenter::equals));
        assertNull(Rac1CrashSiteFeature.findAnchor(chunk, (x, z) -> 62, 63, 300, pos -> fail("Below sea level")));
        assertNull(Rac1CrashSiteFeature.findAnchor(chunk, (x, z) -> 300, 63, 300, pos -> fail("No headroom")));
        int differences = 0;
        for (int x = -40; x < 40; x++) {
            for (int z = -40; z < 40; z++) {
                if (Rac1CrashSiteFeature.isCandidate(19, x, z)
                        != Rac1CrashSiteFeature.isCandidate(71, x, z)) differences++;
            }
        }
        assertTrue(differences >= 10);
    }

    @Test
    void provenanceDefaultsToFalseAndSurvivesOnlyAtOriginalAnchor() {
        var block = Rac1PodRegistration.BLOCK.get();
        var state = block.defaultBlockState();
        var pos = new BlockPos(-55, 64, 20);
        var generated = new Rac1PodMarker(pos, state);
        assertFalse(generated.isNatural());
        assertTrue(generated.getUpdateTag(RegistryAccess.EMPTY).isEmpty());
        generated.markNatural();
        var tag = generated.getUpdateTag(RegistryAccess.EMPTY);
        assertEquals(1, tag.size()); // No viewer identity or unlock is stored or broadcast.
        var sameAnchor = new Rac1PodMarker(pos, state);
        sameAnchor.loadAdditional(tag, RegistryAccess.EMPTY);
        assertTrue(sameAnchor.isNatural());
        sameAnchor.loadAdditional(new CompoundTag(), RegistryAccess.EMPTY);
        assertFalse(sameAnchor.isNatural());
        var cloned = new Rac1PodMarker(pos.offset(16, 0, 0), state);
        cloned.loadAdditional(tag, RegistryAccess.EMPTY);
        assertFalse(cloned.isNatural());
        assertTrue(new Rac1PodMarker(BlockPos.ZERO, state).getUpdateTag(RegistryAccess.EMPTY).isEmpty());
    }

    @Test
    void physicalShapeIsGroundedAndThereIsNoBlockItem() {
        var block = Rac1PodRegistration.BLOCK.get();
        var state = block.defaultBlockState();
        assertEquals(new AABB(1 / 16.0, 0, 1 / 16.0, 15 / 16.0, .5, 15 / 16.0),
                state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).bounds());
        assertEquals(net.minecraft.world.item.Items.AIR, block.asItem());
    }

    @Test
    void clearingKeepsDrySupportAndHeadroomAlongAWholeApproachButDoesNotRequireOtherSides() {
        var anchor = new BlockPos(8, 72, 8);
        var ground = new TestGround(anchor.getY());
        var onlyApproach = new java.util.HashSet<>(Set.of(anchor.offset(2, 0, 0), anchor.offset(3, 0, 0),
                anchor.offset(2, 0, 1), anchor.offset(3, 0, 1)));
        for (var cell : Rac1PodFootprint.CELLS) onlyApproach.add(cell.at(anchor));
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            var foot = anchor.offset(dx, 0, dz);
            if (!onlyApproach.contains(foot)) ground.replacements.put(foot, Blocks.OAK_LEAVES.defaultBlockState());
        }
        var edge = anchor.offset(3, 0, 1);
        assertTrue(safe(ground, anchor));
        assertFalse(Rac1CrashSiteFeature.safeClearing(ground, anchor,
                (a, b) -> a == edge.getX() && b == edge.getZ() ? anchor.getY() + 1 : anchor.getY(),
                state -> state.is(Blocks.STONE)));
        for (var obstructor : Map.of(edge.below(2), Blocks.AIR, edge.below(), Blocks.WATER,
                edge, Blocks.LAVA, edge.above(), Blocks.OAK_LEAVES,
                anchor.below(2), Blocks.AIR, anchor, Blocks.TALL_GRASS).entrySet()) {
            ground.replacements.put(obstructor.getKey(), obstructor.getValue().defaultBlockState());
            assertFalse(safe(ground, anchor), obstructor.getKey().toString());
            ground.replacements.remove(obstructor.getKey());
        }
        assertTrue(safe(ground, anchor));
        var wing = anchor.offset(0, 0, 1);
        var spine = anchor.offset(1, 0, 0);
        ground.replacements.put(wing.below(), Blocks.AIR.defaultBlockState());
        assertTrue(Rac1CrashSiteFeature.safeClearing(ground, anchor,
                (x, z) -> x == wing.getX() && z == wing.getZ() ? 71 : 72, state -> state.is(Blocks.STONE)));
        ground.replacements.put(spine.below(), Blocks.AIR.defaultBlockState());
        assertFalse(Rac1CrashSiteFeature.safeClearing(ground, anchor,
                (x, z) -> x == spine.getX() && z == spine.getZ() ? 71 : 72, state -> state.is(Blocks.STONE)));
    }

    private static boolean safe(TestGround ground, BlockPos pos) {
        return Rac1CrashSiteFeature.safeClearing(ground, pos, (x, z) -> pos.getY(), state -> state.is(Blocks.STONE));
    }

    private static final class TestGround implements BlockGetter {
        private final int surface;
        private final Map<BlockPos, BlockState> replacements = new HashMap<>();
        private TestGround(int surface) { this.surface = surface; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) {
            if (pos.getX() < 5 || pos.getX() > 11 || pos.getZ() < 5 || pos.getZ() > 11) {
                throw new AssertionError("Access outside crash site footprint: " + pos);
            }
            return replacements.getOrDefault(pos, (pos.getY() < surface ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
