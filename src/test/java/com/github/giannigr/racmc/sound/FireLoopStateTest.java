package com.github.giannigr.racmc.sound;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireLoopStateTest {
    @Test void acceptedRefreshStopAndReorderedOldShotsCannotRestartLoop() {
        var state = new FireLoopState(); var emitter = UUID.randomUUID(); var session = UUID.randomUUID();
        assertTrue(state.accept(emitter, session, 1, 100, true, 100));
        assertTrue(state.active(emitter, session, 103));
        assertFalse(state.active(emitter, session, 104));
        assertTrue(state.accept(emitter, session, 2, 102, true, 102));
        assertTrue(state.active(emitter, session, 104));
        assertTrue(state.accept(emitter, session, 3, 103, false, 103));
        assertFalse(state.active(emitter, session, 103));
        assertFalse(state.accept(emitter, session, 2, 102, true, 104));
        assertFalse(state.active(emitter, session, 104));
    }
    @Test void newSessionSameTickUsesMonotonicRevisionAndWorldClearDropsOldGeneration() {
        var state = new FireLoopState(); var emitter = UUID.randomUUID();
        var old = UUID.randomUUID(); var next = UUID.randomUUID();
        assertTrue(state.accept(emitter, old, 10, 100, true, 100));
        assertTrue(state.accept(emitter, next, 12, 100, true, 100));
        assertFalse(state.accept(emitter, old, 11, 100, false, 100));
        assertFalse(state.active(emitter, old, 100)); assertTrue(state.active(emitter, next, 100));
        state.clear(); assertFalse(state.active(emitter, next, 100));
        assertFalse(state.accept(emitter, old, 13, 90, true, 100));
    }
    @Test void boundedEmitterTombstonesAndExpiry() {
        var state = new FireLoopState(); var session = UUID.randomUUID();
        for (int i = 0; i < 128; i++)
            assertTrue(state.accept(new UUID(0, i), session, i, 100, false, 100));
        assertFalse(state.accept(UUID.randomUUID(), session, 200, 100, true, 100));
        assertTrue(state.accept(UUID.randomUUID(), session, 201, 301, true, 301));
    }
    @Test void boundedSemanticPacketRoundTripAndInvalidPositionRejection() {
        var dimension = net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld");
        var signal = new RangedFireSignal(UUID.randomUUID(), UUID.randomUUID(), 3, 100,
                true, dimension, 1.25, 64, -2.5);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            RangedFireSignal.CODEC.encode(buffer, signal);
            assertTrue(buffer.readableBytes() < 128);
            assertEquals(signal, RangedFireSignal.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new RangedFireSignal(
                signal.emitter(), signal.session(), 3, 100, true, dimension, Double.NaN, 0, 0));
        assertEquals(168, Rac1AudioEvent.BLASTER_FIRE_LOOP.classId());
        assertEquals(1, Rac1AudioEvent.BLASTER_FIRE_LOOP.slot());
    }
}
