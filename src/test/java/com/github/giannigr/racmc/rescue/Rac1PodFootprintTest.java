package com.github.giannigr.racmc.rescue;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

class Rac1PodFootprintTest {
    private static final BlockPos ANCHOR = new BlockPos(-104, 72, 456);

    @Test
    void everyFullCellHasLocalCollisionAndRoutesToOneInChunkAnchor() {
        var ground = new HullFixture(true, true);
        assertTrue(Rac1PodFootprint.complete(ground, ANCHOR));
        assertTrue(Rac1PodFootprint.naturalAt(ground, ANCHOR));
        for (int i = 0; i < 9; i++) {
            var pos = Rac1PodFootprint.CELLS.get(i).at(ANCHOR);
            if (i != 0) assertEquals(ANCHOR, Rac1PodFootprint.partAnchor(pos, i));
            var state = ground.getBlockState(pos);
            var outline = state.getShape(ground, pos);
            assertFalse(outline.isEmpty());
            assertEquals(outline.bounds(), state.getCollisionShape(ground, pos, CollisionContext.empty()).bounds());
            var bounds = outline.bounds();
            assertTrue(bounds.minX >= 0 && bounds.maxX <= 1 && bounds.minY >= 0 && bounds.maxY <= 1
                    && bounds.minZ >= 0 && bounds.maxZ <= 1);
            assertSame(net.minecraft.world.item.Items.AIR, state.getBlock().asItem());
        }
        var wing = ANCHOR.south();
        ground.states.remove(wing);
        assertFalse(Rac1PodFootprint.complete(ground, ANCHOR));
        ground.states.remove(ANCHOR);
        assertTrue(ground.getBlockState(ANCHOR.east()).getShape(ground, ANCHOR.east()).isEmpty());
        assertNull(Rac1PodFootprint.partAnchor(new BlockPos(15, 72, 120), 1));
    }

    @Test
    void vanillaOutlineFindsLegacyAndFullOuterCellFromKnownWestApproach() {
        var eye = new Vec3(-105.5, 73.62, 456.5);
        var aim = new Vec3(-103.5, 72.25, 456.5);
        for (boolean full : new boolean[]{false, true}) {
            var ground = new HullFixture(full, true);
            var expected = full ? ANCHOR.west() : ANCHOR;
            var hit = ground.clip(new ClipContext(eye, aim, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            assertEquals(HitResult.Type.BLOCK, hit.getType());
            assertEquals(expected, hit.getBlockPos());
            var end = Rac1PodInteraction.rayEnd(eye, expected, hit);
            assertNotNull(end);
            assertEquals(expected, ground.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, CollisionContext.empty())).getBlockPos());
        }
    }

    @Test
    void unprovenancedPartsHaveNoShapeAndRayRejectsMalformedOrFarHits() {
        var ground = new HullFixture(true, false);
        assertFalse(Rac1PodFootprint.naturalAt(ground, ANCHOR));
        assertTrue(ground.getBlockState(ANCHOR.west()).getShape(ground, ANCHOR.west()).isEmpty());
        var point = Vec3.atLowerCornerWithOffset(ANCHOR, .5, .25, .5);
        var good = new BlockHitResult(point, Direction.WEST, ANCHOR, false);
        assertNull(Rac1PodInteraction.rayEnd(point.add(-7, 0, 0), ANCHOR, good));
        assertNull(Rac1PodInteraction.rayEnd(point.add(-1, 0, 0), ANCHOR.east(), good));
        assertNull(Rac1PodInteraction.rayEnd(point.add(-1, 0, 0), ANCHOR,
                new BlockHitResult(point.add(2, 0, 0), Direction.WEST, ANCHOR, false)));
    }

    @Test
    void oldSerializedBlockStateDecodesToLegacyWithoutWorldChanges() {
        var old = new CompoundTag();
        old.putString("Name", "racmc:crashed_pod");
        var blocks = BuiltInRegistries.BLOCK.asLookup();
        var state = NbtUtils.readBlockState(blocks, old);
        assertSame(Rac1PodRegistration.BLOCK.get().defaultBlockState(), state);
        assertFalse(Rac1PodFootprint.full(state));
        var newTag = NbtUtils.writeBlockState(state.setValue(Rac1PodBlock.FULL, true));
        assertTrue(Rac1PodFootprint.full(NbtUtils.readBlockState(blocks, newTag)));
    }

    @Test
    void lightingNeverResolvesTheAnchor() {
        var part = Rac1PodRegistration.PART.get();
        // A lighting query can run while the anchor chunk is still being promoted.
        assertTrue(part.propagatesSkylightDown(part.defaultBlockState(), null, ANCHOR.east()));
    }

    @Test
    void claimGuardReopensWithoutAnAwardButPersistsEvenIfDeliveryFails() {
        var hull = new HullFixture(true, true);
        var marker = hull.marker;
        assertTrue(marker.beginClaim());
        assertFalse(marker.beginClaim());
        assertFalse(marker.getUpdateTag(RegistryAccess.EMPTY).contains("Claimed"));
        marker.finishClaim(); // Denied/previously unlocked: no Gear commit callback.
        assertTrue(marker.beginClaim());
        assertThrows(IllegalStateException.class, () -> {
            try {
                marker.commitClaim();
                throw new IllegalStateException("simulated post-award failure");
            } finally {
                marker.finishClaim();
            }
        });
        assertFalse(marker.beginClaim());
        var saved = marker.getUpdateTag(RegistryAccess.EMPTY);
        assertEquals(java.util.Set.of("NaturalAnchor", "Claimed"), saved.getAllKeys());
        var reopened = new Rac1PodMarker(ANCHOR, hull.getBlockState(ANCHOR));
        reopened.loadAdditional(saved, RegistryAccess.EMPTY);
        assertTrue(reopened.isClaimed());
        assertFalse(reopened.beginClaim());
        var moved = new Rac1PodMarker(ANCHOR.east(), hull.getBlockState(ANCHOR));
        moved.loadAdditional(saved, RegistryAccess.EMPTY);
        assertFalse(moved.isClaimed());
        assertFalse(moved.beginClaim());
        saved.remove("Claimed");
        reopened.loadAdditional(saved, RegistryAccess.EMPTY);
        assertTrue(reopened.beginClaim()); // Old development marker; no claim inferred.
    }

    @Test
    void intactClaimedHullCanBeDismantledFromEveryCellAndIncompleteHullCannotYieldRecovery() {
        var hull = new HullFixture(true, true);
        assertFalse(Rac1PodWreck.breakable(hull, ANCHOR, 0));
        assertTrue(hull.marker.beginClaim());
        hull.marker.commitClaim();
        hull.marker.finishClaim();
        for (int i = 0; i < Rac1PodFootprint.CELLS.size(); i++) {
            assertTrue(Rac1PodWreck.breakable(hull, Rac1PodFootprint.CELLS.get(i).at(ANCHOR), i));
        }
        assertTrue(hull.marker.beginDismantle());
        assertFalse(hull.marker.beginDismantle());
        hull.marker.finishDismantle();
        hull.states.remove(ANCHOR.south());
        assertFalse(Rac1PodWreck.breakable(hull, ANCHOR, 0));
        assertFalse(Rac1PodWreck.breakable(hull, ANCHOR.west(), 1));
    }

    @Test
    void decorativeHullIsSolidAndRecoverableButCannotClaimEvenAfterReload() {
        var hull = new HullFixture(true, false);
        hull.marker.markDecorative();
        assertFalse(hull.marker.beginClaim());
        assertFalse(hull.marker.isNatural());
        assertFalse(hull.getBlockState(ANCHOR.west()).getShape(hull, ANCHOR.west()).isEmpty());
        assertTrue(Rac1PodWreck.breakable(hull, ANCHOR.west(), 1));
        var saved = hull.marker.getUpdateTag(RegistryAccess.EMPTY);
        assertEquals(java.util.Set.of("DecorativeAnchor"), saved.getAllKeys());
        var reopened = new Rac1PodMarker(ANCHOR, hull.getBlockState(ANCHOR));
        reopened.loadAdditional(saved, RegistryAccess.EMPTY);
        assertTrue(reopened.isDecorative());
        assertFalse(reopened.beginClaim());
        assertFalse(Rac1PodFootprint.sameChunk(new BlockPos(15, 72, 120)));
        assertTrue(Rac1PodFootprint.sameChunk(new BlockPos(14, 72, 120)));
    }

    @Test
    void recoveryIncludesGoldenExceptionAndUsesTheOriginalToolEvenOnItsLastUse() {
        var state = Rac1PodRegistration.BLOCK.get().defaultBlockState();
        for (var item : java.util.List.of(Items.GOLDEN_PICKAXE, Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
            var tool = new ItemStack(item);
            tool.setDamageValue(tool.getMaxDamage() - 1);
            assertTrue(Rac1PodWreck.recovers(state, tool));
        }
        for (var item : java.util.List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE,
                Items.GOLDEN_SHOVEL, Items.DIAMOND_AXE, Items.NETHERITE_SWORD)) {
            assertFalse(Rac1PodWreck.recovers(state, new ItemStack(item)));
        }
        assertFalse(Rac1PodWreck.recovers(state, ItemStack.EMPTY));
    }

    private static final class HullFixture implements BlockGetter {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Rac1PodMarker marker;
        HullFixture(boolean full, boolean natural) {
            var state = Rac1PodRegistration.BLOCK.get().defaultBlockState().setValue(Rac1PodBlock.FULL, full);
            states.put(ANCHOR, state);
            marker = new Rac1PodMarker(ANCHOR, state);
            if (natural) marker.markNatural();
            if (full) for (int i = 1; i < 9; i++) states.put(Rac1PodFootprint.CELLS.get(i).at(ANCHOR),
                    Rac1PodRegistration.PART.get().defaultBlockState().setValue(Rac1PodPartBlock.PART, i));
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return ANCHOR.equals(pos) ? marker : null; }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
