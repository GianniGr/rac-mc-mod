package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.ShipFlight.*;
import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShipFlightTest {
    @Test
    void nativeInputCodecHasEighteenBytesAndNoTransformFields() {
        var packet = new ShipInputPayload(42, 8, 16, 3, ACTIVE | FORWARD | BOOST_HELD | BOOST_PRESS);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ShipInputPayload.CODEC.encode(buffer, packet);
            assertEquals(18, buffer.readableBytes());
            assertEquals(packet, ShipInputPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> ShipInputPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void turboTuningAndToggleOffRespectAllCapsWithoutChangingYawOrBrake() {
        assertEquals(Vec3.ZERO, step(0, 0, Vec3.ZERO, false, false).velocity());
        assertEquals(Vec3.ZERO, step(FORWARD | SNEAK, 0, Vec3.ZERO, true, true).velocity());
        assertEquals(Vec3.ZERO, step(UP | SNEAK, 0, Vec3.ZERO, false, true).velocity());
        assertEquals(step(UP | RIGHT, 0, Vec3.ZERO, false, false).yaw(),
                step(UP | RIGHT, 0, Vec3.ZERO, false, true).yaw());
        assertEquals(step(SNEAK | LEFT, 0, Vec3.ZERO, false, false).yaw(),
                step(SNEAK | LEFT, 0, Vec3.ZERO, false, true).yaw());
        assertEquals(step(FORWARD | BRAKE, 0, Vec3.ZERO, false, false),
                step(FORWARD | BRAKE, 0, Vec3.ZERO, false, true));
        assertEquals(3.5, step(RIGHT, 0, Vec3.ZERO, false, true).yaw(), 0.0001);
        assertTrue(step(RIGHT | FORWARD, 0, Vec3.ZERO, false, true).velocity().x < 0);

        Vec3 velocity = Vec3.ZERO;
        for (int i = 0; i < 100; i++) velocity = step(FORWARD, 0, velocity, false, false).velocity();
        assertEquals(.40, velocity.horizontalDistance(), 1e-8);
        for (int i = 0; i < 100; i++) velocity = step(FORWARD, 0, velocity, false, true).velocity();
        assertEquals(1.50, velocity.horizontalDistance(), 1e-8);
        assertEquals(.525, step(FORWARD | BRAKE, 0, velocity, false, true).velocity().horizontalDistance(), 1e-8);
        assertEquals(.40, step(FORWARD, 0, velocity, false, false).velocity().horizontalDistance(), 1e-8);
        for (int i = 0; i < 100; i++) velocity = step(FORWARD, 0, velocity, false, false).velocity();
        assertEquals(.40, velocity.horizontalDistance(), 1e-8);
        for (int i = 0; i < 150; i++) {
            var turned = step(FORWARD | RIGHT | UP, i * 3.5F, velocity, false, true);
            velocity = turned.velocity();
            assertTrue(velocity.horizontalDistance() <= 1.500001 && velocity.y <= .600001);
            assertTrue(turned.yaw() >= -180 && turned.yaw() < 180);
        }
        for (boolean turbo : new boolean[]{false, true}) {
            var up = Vec3.ZERO;
            var down = Vec3.ZERO;
            for (int i = 0; i < 100; i++) {
                up = step(UP, 0, up, false, turbo).velocity();
                down = step(SNEAK, 0, down, false, turbo).velocity();
            }
            assertEquals(turbo ? .60 : .18, up.y, 1e-8);
            assertEquals(turbo ? -.50 : -.14, down.y, 1e-8);
            assertEquals(.18, step(UP, 0, up, false, false).velocity().y, 1e-8);
            assertEquals(-.14, step(SNEAK, 0, down, false, false).velocity().y, 1e-8);
        }
    }

    @Test
    void presentationAxesDescribeOnlyMatchingActualMovement() {
        var climb = step(FORWARD | UP, 0, Vec3.ZERO, false, true);
        assertEquals(THRUST_CLIMB | THRUST_FORWARD, climb.actualBoostThrust(new Vec3(0, .2, 1)));
        assertEquals(THRUST_CLIMB, climb.actualBoostThrust(new Vec3(0, .2, 0)));
        assertEquals(THRUST_FORWARD, climb.actualBoostThrust(new Vec3(0, 0, 1)));
        assertEquals(0, climb.actualBoostThrust(new Vec3(0, -.2, -1)));
        assertEquals(THRUST_DESCEND, step(SNEAK, 0, Vec3.ZERO, false, true)
                .actualBoostThrust(new Vec3(0, -.2, 0)));
        assertEquals(0, step(FORWARD | BRAKE, 0, new Vec3(0, 0, 1.5), false, true)
                .actualBoostThrust(new Vec3(0, 0, .525)));
    }

    @Test
    void airborneAndDelayedPressCannotBecomeGroundedExit() {
        var input = new Input();
        assertTrue(input.accept(1, 0, 0, ACTIVE, 1));
        assertTrue(input.accept(2, 0, 0, ACTIVE | SNEAK, 2));
        assertFalse(input.consumeExit(2, 0));
        assertTrue(input.accept(3, 0, 9, ACTIVE | SNEAK | EXIT_PRESS, 3));
        assertTrue(input.accept(4, 9, 9, ACTIVE | SNEAK, 4));
        assertFalse(input.consumeExit(4, 9));
        assertTrue(input.accept(5, 9, 9, ACTIVE, 5));
        assertTrue(input.accept(6, 9, 9, ACTIVE | SNEAK | EXIT_PRESS, 6));
        assertTrue(input.consumeExit(6, 9));
        assertFalse(input.consumeExit(6, 9));
        assertTrue(input.accept(7, 9, 9, ACTIVE | SNEAK | EXIT_PRESS, 7));
        assertFalse(input.consumeExit(7, 9));
    }

    @Test
    void missedReorderedStaleAndFocusFramesFailClosedForExitAndBoost() {
        var input = new Input();
        assertFalse(input.accept(1, 4, 4, 2048, 1));
        assertFalse(input.accept(1, 4, 4, ACTIVE | BOOST_PRESS, 1));
        assertTrue(input.accept(1, 4, 4, ACTIVE, 1));
        assertTrue(input.accept(3, 4, 4, ACTIVE | SNEAK | EXIT_PRESS | BOOST_HELD | BOOST_PRESS, 3));
        assertFalse(input.consumeExit(3, 4));
        assertFalse(input.consumeBoost(3));
        assertFalse(input.accept(2, 4, 4, ACTIVE, 4));
        assertTrue(input.accept(4, 4, 4, ACTIVE, 4));
        assertFalse(input.accept(5, 4, 4, ACTIVE, 4));
        assertTrue(input.accept(5, 4, 4, ACTIVE | BOOST_HELD | BOOST_PRESS, 5));
        assertTrue(input.consumeBoost(5));
        assertFalse(input.consumeBoost(5));
        assertTrue(input.accept(6, 4, 4, ACTIVE | BOOST_HELD | BOOST_PRESS, 6));
        assertFalse(input.consumeBoost(6));
        assertTrue(input.accept(7, 4, 4, ACTIVE, 7));
        assertEquals(ACTIVE, input.fresh(11));
        assertEquals(0, input.fresh(12));
        assertTrue(input.accept(8, 4, 4, ACTIVE | SNEAK | EXIT_PRESS | BOOST_HELD | BOOST_PRESS, 12));
        assertFalse(input.consumeExit(12, 4));
        assertFalse(input.consumeBoost(12));
        assertTrue(input.accept(9, 0, 4, 0, 13));
        assertTrue(input.accept(10, 4, 4, ACTIVE | BOOST_HELD | SNEAK, 14));
        assertFalse(input.consumeBoost(14));
        assertFalse(input.consumeExit(14, 4));
    }

    @Test
    void newerNeutralMovementCannotRefreshAnOldPendingEdge() {
        var input = new Input();
        assertTrue(input.accept(1, 4, 4, ACTIVE, 1));
        assertTrue(input.accept(2, 4, 4, ACTIVE | SNEAK | EXIT_PRESS | BOOST_HELD | BOOST_PRESS, 2));
        assertTrue(input.accept(3, 4, 4, ACTIVE, 12));
        assertFalse(input.consumeExit(12, 4));
        assertFalse(input.consumeBoost(12));
    }
}
