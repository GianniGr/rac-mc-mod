package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.EffectBankTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.effect.EffectSignal.*;

class EffectPlaybackSessionTest {
    private static final Point ZERO = new Point(0, 0, 0);
    private static EffectPlaybackSession session() {
        var session = new EffectPlaybackSession(EffectBankTest.SHA1, EffectClock.Profile.NTSC, 2048);
        session.beginWorld("minecraft:overworld", 1); return session;
    }
    private static EffectSignal shot(UUID id, Semantic semantic) {
        return new EffectSignal(semantic, "minecraft:overworld", id, semantic == Semantic.BLASTER_SHOT ? 0 : 1,
                100, 7, Op.START, ZERO, semantic == Semantic.BLASTER_IMPACT
                ? new Impact(new Point(1, 0, 0), new Point(-1, 0, 0)) : null);
    }
    private static EffectPlaybackSession.Source source(UUID id) {
        return new EffectPlaybackSession.Source(id, ZERO, new Point(1, 0, 0), 1);
    }
    @Test void authoritativeSpawnAndShotPacketShareOneVisual() {
        var session = session(); UUID id = UUID.randomUUID();
        assertTrue(session.receive(shot(id, Semantic.BLASTER_SHOT), 100, ZERO));
        session.advance(5, false, 100, List.of(source(id)));
        session.advance(5 + 1.0 / 60, false, 100, List.of(source(id)));
        assertEquals(18, session.runtime().size());
        assertTrue(session.ownsProjectile(id));
        assertFalse(session.receive(shot(id, Semantic.BLASTER_SHOT), 100, ZERO));
        assertEquals(18, session.runtime().size());
    }
    @Test void firstEligibleEntityFrameHasNonzeroImportedDrawsBeforeClockAdvance() {
        var session = session(); UUID id = UUID.randomUUID();
        assertTrue(session.admitProjectile(source(id)));
        var draws = session.runtime().snapshot(ZERO, 1);
        assertEquals(18, draws.size(), "No fixed-update delay or vanilla fallback");
        assertEquals(17, draws.stream().filter(d -> d.asset().sourceId() == 28).count());
        assertEquals(1, draws.stream().filter(d -> d.asset().sourceId() == 32).count());
        assertTrue(draws.stream().allMatch(d -> d.size() > 0));
        assertTrue(session.admitProjectile(source(id)));
        assertEquals(draws, session.runtime().snapshot(ZERO, 1), "Repeat preparation cannot jitter/reallocate");
    }
    @Test void actualCapacityFailureIsInvisibleAndCannotInventPartialGraphics() {
        var session = new EffectPlaybackSession(EffectBankTest.SHA1, EffectClock.Profile.NTSC, 17);
        session.beginWorld("minecraft:overworld", 1);
        assertFalse(session.admitProjectile(source(UUID.randomUUID())));
        assertTrue(session.runtime().snapshot(ZERO, 1).isEmpty());
    }
    @Test void clockDiscontinuityDoesNotBlankCurrentlyTrackedImportedProjectiles() {
        var session = session(); UUID id = UUID.randomUUID();
        session.advance(5, false, 100, List.of(source(id)));
        var resumed = session.advance(6, false, 120, List.of(source(id)));
        assertTrue(resumed.discontinuity());
        assertEquals(18, session.runtime().snapshot(ZERO, resumed.interpolation()).size());
        assertTrue(session.ownsProjectile(id));
    }
    @Test void impactBeforeSpawnAndLaterEndNeverResurrectOrDoubleEmit() {
        var session = session(); UUID id = UUID.randomUUID();
        assertTrue(session.receive(shot(id, Semantic.BLASTER_IMPACT), 100, ZERO));
        int count = session.runtime().size();
        assertEquals(8, count);
        session.receive(shot(id, Semantic.BLASTER_SHOT), 100, ZERO);
        assertFalse(session.receive(shot(id, Semantic.BLASTER_END), 100, ZERO));
        session.advance(5, false, 100, List.of(source(id)));
        assertEquals(count, session.runtime().size());
        assertEquals(0, session.runtime().snapshot(ZERO, 1).stream()
                .filter(draw -> draw.asset().sourceId() == 28).count());
    }
    @Test void untrackingAndDimensionSwitchAreNotImpacts() {
        var session = session(); UUID id = UUID.randomUUID();
        session.advance(5, false, 100, List.of(source(id)));
        assertEquals(18, session.runtime().size());
        session.advance(5 + 1.0 / 60, false, 100, List.of());
        assertEquals(0, session.runtime().size());
        session.beginWorld("minecraft:the_nether", 2);
        assertFalse(session.receive(shot(id, Semantic.BLASTER_IMPACT), 100, ZERO));
        assertEquals(0, session.runtime().size());
    }
    @Test void pausedClockDoesNotEmitFixedStepChildrenAndStallClears() {
        var session = session(); int[] steps = {0};
        session.advance(5, false, 100, List.of(), () -> steps[0]++);
        session.advance(25, true, 100, List.of(), () -> steps[0]++);
        assertEquals(0, steps[0]);
        session.advance(25 + 3.0 / 60, false, 100, List.of(), () -> steps[0]++);
        assertEquals(3, steps[0]);
        session.receive(new EffectSignal(Semantic.CRATE_TNT_BLAST, "minecraft:overworld", UUID.randomUUID(),
                0, 100, 1, Op.START, ZERO), 100, ZERO);
        session.advance(30, false, 100, List.of());
        assertEquals(0, session.runtime().size());
    }
    @Test void lateOneShotDoesNotReplayButLateTerminalStillStopsItsTrail() {
        var session = session(); UUID id = UUID.randomUUID();
        session.advance(5.5, false, 110, List.of(source(id)));
        assertEquals(18, session.runtime().size());
        assertTrue(session.receive(shot(id, Semantic.BLASTER_IMPACT), 110, ZERO));
        assertEquals(0, session.runtime().size());
        assertFalse(session.receive(new EffectSignal(Semantic.CRATE_TNT_BLAST, "minecraft:overworld",
                UUID.randomUUID(), 0, 100, 1, Op.START, ZERO), 110, ZERO));
        assertEquals(0, session.runtime().size());
    }
    @Test void terminalEventSeedIsIndependentOfTrackingAndTrailRandomConsumption() {
        var tracked = session(); var beforeSpawn = session(); UUID id = UUID.randomUUID();
        tracked.advance(5, false, 100, List.of(source(id)));
        tracked.advance(5 + 3.0 / 60, false, 101, List.of(source(id)));
        var impact = shot(id, Semantic.BLASTER_IMPACT);
        tracked.receive(impact, 101, ZERO);
        beforeSpawn.receive(impact, 101, ZERO);
        assertEquals(tracked.runtime().snapshot(ZERO, 1), beforeSpawn.runtime().snapshot(ZERO, 1));
    }
    @Test void explicitStopCancelsDetachedOneShotsWithoutReplayingThem() {
        var session = session(); UUID id = UUID.randomUUID();
        session.receive(new EffectSignal(Semantic.CRATE_TNT_BLAST, "minecraft:overworld", id,
                0, 100, 1, Op.START, ZERO), 100, ZERO);
        assertEquals(8, session.runtime().size());
        assertTrue(session.receive(new EffectSignal(Semantic.CRATE_TNT_BLAST, "minecraft:overworld", id,
                1, 100, 1, Op.STOP, ZERO), 100, ZERO));
        assertEquals(0, session.runtime().size());
    }
}
