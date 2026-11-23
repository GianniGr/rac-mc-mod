package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.ShipEngineBeat.Kind.*;
import static com.github.giannigr.racmc.ship.ShipEngineState.*;
import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShipEngineBeatTest {
    @Test
    void onlyActualMotionAcrossVerifiedSupportCanEmitABeat() {
        assertEquals(LIFTOFF, ShipEnginePhysics.transition(SUPPORTED, AIRBORNE, new Vec3(0, .08, 0)));
        assertEquals(TOUCHDOWN, ShipEnginePhysics.transition(AIRBORNE, SUPPORTED, new Vec3(0, -.1, 0)));
        assertNull(ShipEnginePhysics.transition(SUPPORTED, AIRBORNE, Vec3.ZERO));
        assertNull(ShipEnginePhysics.transition(SUPPORTED, AIRBORNE, new Vec3(.55, 0, 0)));
        assertNull(ShipEnginePhysics.transition(SUPPORTED, SUPPORTED, new Vec3(0, .01, 0)));
        assertNull(ShipEnginePhysics.transition(AIRBORNE, SUPPORTED, Vec3.ZERO));
        assertNull(ShipEnginePhysics.transition(AIRBORNE, UNVERIFIED, new Vec3(0, -.11, 0)));
        assertNull(ShipEnginePhysics.transition(AIRBORNE, AIRBORNE, new Vec3(0, -.11, 0)));
    }

    @Test
    void wireIsTypedAndInboxDropsReplaysStaleSamplesAndTheWrongShip() {
        var uuid = UUID.fromString("dbec54ed-9ab4-4039-95e7-4fb2c21579b0");
        var up = new ShipEngineBeat(83, uuid, LIFTOFF, 1, 304);
        var down = new ShipEngineBeat(83, uuid, TOUCHDOWN, 2, 398);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ShipEngineBeat.CODEC.encode(buf, up);
            assertTrue(buf.readableBytes() < 35);
            assertEquals(up, ShipEngineBeat.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }

        var inbox = new ShipEngineBeat.Inbox();
        assertFalse(inbox.offer(up, 84, uuid, 10));
        assertFalse(inbox.offer(up, 83, UUID.randomUUID(), 10));
        assertTrue(inbox.offer(up, 83, uuid, 10));
        assertEquals(up, inbox.sample(10));
        assertEquals(up, inbox.sample(11));
        assertNull(inbox.sample(12));
        assertFalse(inbox.offer(up, 83, uuid, 12));
        assertTrue(inbox.offer(down, 83, uuid, 15));
        assertFalse(inbox.offer(new ShipEngineBeat(83, uuid, LIFTOFF, 3, 390), 83, uuid, 16));
        inbox.clearSample();
        assertNull(inbox.sample(16));
        assertFalse(inbox.offer(down, 83, uuid, 17));
        assertNull(new ShipEngineBeat.Inbox().sample(17), "A late tracker must start without a past beat");
    }
}
