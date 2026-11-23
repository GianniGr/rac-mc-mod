package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.ModSource;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveActionEvent.Kind;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static com.github.giannigr.racmc.effect.EffectSignal.*;
import static org.junit.jupiter.api.Assertions.*;

class BombGloveDryEffectsTest {
    private static final String SHA1 = "1".repeat(40), WORLD = "minecraft:overworld";
    private static final Point ANCHOR = new Point(4, 70, 5), FAR = ANCHOR.add(0, 0, 10);

    @Test void onlyAcceptedDryBlastCanReachTheBurstTransport() {
        for (var event : Kind.values()) {
            assertEquals(event == Kind.BLAST, BombGloveDryEffects.accepts(event, false));
            assertFalse(BombGloveDryEffects.accepts(event, true));
        }
    }

    @Test void hookFollowsTheSealedServerTransitionAndUsesTheIndependentChannel() {
        String bomb = ModSource.of("com/github/giannigr/racmc/item/weapon/bomb/Rac1Bomb.java");
        int burst = bomb.indexOf("private void beginBurst");
        int seal = bomb.indexOf("phase(deep ? Phase.DEEP_BURST : Phase.BLAST)", burst);
        int position = bomb.indexOf("setPos(at)", burst);
        int wet = bomb.indexOf("Kind.DEEP_BURST", burst);
        int wave = bomb.indexOf("wave(level)", burst);
        int action = bomb.indexOf("Kind.BLAST", burst);
        assertTrue(burst >= 0 && burst < seal && seal < position && position < wet && wet < wave && wave < action);
        assertEquals(action, bomb.lastIndexOf("Kind.BLAST"));
        String network = ModSource.of("com/github/giannigr/racmc/effect/Rac1EffectNetwork.java");
        assertTrue(network.contains("hasChannel(DryBurstPacket.TYPE)"));
        assertNotEquals(Rac1EffectNetwork.Packet.TYPE.id(), Rac1EffectNetwork.DryBurstPacket.TYPE.id());
    }

    @Test void sameBombOneShotReachesIndependentObserversWithoutDoubleDraw() throws Exception {
        UUID bomb = UUID.randomUUID();
        var signal = signal(bomb, 22);
        EffectSignal wire;
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            Rac1EffectNetwork.DryBurstPacket.CODEC.encode(buffer, new Rac1EffectNetwork.DryBurstPacket(signal));
            wire = Rac1EffectNetwork.DryBurstPacket.CODEC.decode(buffer).signal();
        } finally {
            buffer.release();
        }
        assertEquals(signal, wire);
        var first = session(true, EffectClock.Profile.NTSC);
        var second = session(true, EffectClock.Profile.NTSC);
        assertTrue(first.receive(wire, 22, ANCHOR));
        assertTrue(second.receive(wire, 22, FAR));
        assertEquals(3, first.runtime().size());
        assertEquals(5, second.runtime().size());
        assertFalse(first.receive(wire, 23, ANCHOR));
        assertEquals(3, first.runtime().size());
        assertThrows(IllegalArgumentException.class, () -> new EffectSignal(Semantic.BOMB_GLOVE_DRY_BURST,
                WORLD, bomb, 1, 22, 7, Op.START, ANCHOR));
        assertThrows(IllegalArgumentException.class, () -> new EffectSignal(Semantic.BOMB_GLOVE_DRY_BURST,
                WORLD, bomb, 0, 22, 7, Op.STOP, ANCHOR));
    }

    @Test void missingOriginalArtOrHistoricalBurstDoesNotCreateReplacementParticles() {
        var missing = session(false, EffectClock.Profile.NTSC);
        assertFalse(missing.receive(signal(UUID.randomUUID(), 22), 22, ANCHOR));
        assertEquals(0, missing.runtime().size());
        var ready = session(true, EffectClock.Profile.NTSC);
        assertFalse(ready.receive(signal(UUID.randomUUID(), 22), 29, ANCHOR));
        assertEquals(0, ready.runtime().size());
    }

    @Test void originalShellLayerGrowsSpinsAndFadesWithoutAHeadStart() {
        for (var profile : EffectClock.Profile.values()) {
            var session = session(true, profile);
            assertTrue(session.receive(signal(UUID.randomUUID(), 22), 22, ANCHOR));
            var initial = session.runtime().snapshot(ANCHOR, 1);
            assertTrue(initial.stream().allMatch(d -> d.asset().sourceId() == 1192
                    && d.size() == 0 && d.position().equals(ANCHOR) && d.material() == EffectMaterial.NORMAL_REPEAT));
            session.advance(0, false, 22, List.of());
            int half = profile.ticks(20) / 2;
            for (int i = 1; i <= half; i++) session.advance(i / (double) profile.hz(), false, 22, List.of());
            var middle = session.runtime().snapshot(ANCHOR, 1).getFirst();
            assertEquals(4f * half / profile.ticks(20), middle.size(), 1e-6);
            assertEquals(initial.getFirst().rgba(), middle.rgba());
            assertNotEquals(initial.getFirst().rotation(), middle.rotation());
            for (int i = half + 1; i < profile.ticks(20); i++) {
                session.advance(i / (double) profile.hz(), false, 22, List.of());
            }
            assertTrue((session.runtime().snapshot(ANCHOR, 1).getFirst().rgba() & 255) < (middle.rgba() & 255));
        }
    }

    private static EffectPlaybackSession session(boolean ready, EffectClock.Profile profile) {
        var session = new EffectPlaybackSession(SHA1, profile, 20);
        session.bombFlashReady(ready);
        session.beginWorld(WORLD, 1);
        return session;
    }

    private static EffectSignal signal(UUID id, long tick) {
        return new EffectSignal(Semantic.BOMB_GLOVE_DRY_BURST, WORLD, id, 0, tick, 7, Op.START, ANCHOR);
    }
}
