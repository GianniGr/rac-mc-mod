package com.github.giannigr.racmc.block;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

class Rac1VendorCollisionTest {
    @Test
    void collisionAndSelectionUseGroundedBodyCubeNotAntennasOrLogo() {
        var block = RacMcBlocks.VENDOR.get();
        var state = block.defaultBlockState();
        var expected = new AABB(0, 0, 0, 1, 1, 1);
        assertEquals(expected, state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                CollisionContext.empty()).bounds());
        assertEquals(expected, state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                CollisionContext.empty()).bounds());
    }

    @Test
    void worldEnlargementDoesNotChangeProximityCalibration() {
        assertEquals(2.0f, Rac1VendorFit.WORLD_ENLARGEMENT);
        assertEquals(16 / 2.303, Rac1VendorVisualState.ENTER_RADIUS, 1e-5);
        assertEquals(18 / 2.303, Rac1VendorVisualState.EXIT_RADIUS, 1e-5);
        assertEquals(8 / 2.303, Rac1VendorVisualState.ENTER_HEIGHT, 1e-5);
        assertEquals(10 / 2.303, Rac1VendorVisualState.EXIT_HEIGHT, 1e-5);
    }
}
