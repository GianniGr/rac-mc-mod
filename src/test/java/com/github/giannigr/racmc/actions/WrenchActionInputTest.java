package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.client.actions.WrenchActionClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.InputEvent;
import org.junit.jupiter.api.Test;

class WrenchActionInputTest {
    @Test void normalJumpSlamsForRealMissEntityAndDownwardGroundBlockHits() {
        HitResult[] hits = {
            BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO),
            new EntityHitResult(null, Vec3.ZERO),
            new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false)
        };
        assertTrue(hits[0] instanceof BlockHitResult); // The actual cd07bdf regression.
        double y = 0, velocity = 0.42;
        for (int airTick = 1; airTick <= 4; airTick++) {
            y += velocity;
            velocity = (velocity - 0.08) * 0.98;
        }
        for (HitResult hit : hits) {
            var route = WrenchActionInput.route(true, false, false, 4, velocity < 0, y,
                    WrenchActionInput.Target.valueOf(hit.getType().name()));
            assertEquals(WrenchActionInput.Route.SLAM, route, hit.getType().toString());
            var input = new InputEvent.InteractionKeyMappingTriggered(0, null, InteractionHand.MAIN_HAND);
            assertTrue(WrenchActionClient.consume(input, route, false));
            assertTrue(input.isCanceled(), "No simultaneous vanilla entity hit or instant block mine");
            assertFalse(input.shouldSwingHand(), "No stock swing/crit stacked on accepted slam");
        }
    }

    @Test void groundedBlockMiningAndMissingDataStayNativeButCrouchKeepsPriority() {
        for (var target : WrenchActionInput.Target.values()) {
            assertEquals(WrenchActionInput.Route.NATIVE,
                    WrenchActionInput.route(true, true, false, 0, false, 0, target));
            assertEquals(WrenchActionInput.Route.THROW,
                    WrenchActionInput.route(true, false, true, 4, false, 1, target));
            assertEquals(WrenchActionInput.Route.NATIVE,
                    WrenchActionInput.route(false, false, false, 4, false, 1, target));
        }
    }
}
