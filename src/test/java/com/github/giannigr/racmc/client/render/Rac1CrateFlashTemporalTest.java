package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.block.Rac1ExplosiveFuse;
import com.github.giannigr.racmc.block.Rac1CrateFlashClock;
import com.github.giannigr.racmc.ModSource;
import org.junit.jupiter.api.Test;

class Rac1CrateFlashTemporalTest {
    @Test
    void delayedCounterUpdatesMustNotReplayRedAfterEachOffTransition() {
        int legacyWindows = 0, observedWindows = 0;
        int originalWindows = 0;
        boolean previous = false, previousOriginal = false, previousLegacy = false;
        Rac1CrateFlashClock clock = new Rac1CrateFlashClock();
        // 120 FPS, 20 client TPS. Updates arrive 1/4 client tick after each tick boundary.
        // This is normal scheduling/packet phase, not a changing world light.
        for (int frame = 0; frame < 360; frame++) {
            double clientTime = 0.25 + frame / 6.0;
            long tick = (long) Math.floor(clientTime);
            float partial = (float) (clientTime - tick);
            int authoritativeElapsed = (int) Math.floor(clientTime - 0.25 + 1e-9);
            int remaining = 60 - authoritativeElapsed;
            boolean legacy = Rac1ExplosiveFuse.red(remaining, partial);
            boolean observed = clock.red(remaining, authoritativeElapsed, tick, partial);
            boolean original = Rac1ExplosiveFuse.redAtElapsed(clientTime);
            if (legacy && !previousLegacy) legacyWindows++;
            if (observed && !previous) observedWindows++;
            if (original && !previousOriginal) originalWindows++;
            previous = observed;
            previousOriginal = original;
            previousLegacy = legacy;
        }
        System.out.println("Fixed-light temporal sweep: originalWindows=" + originalWindows
                + ", legacyWindows=" + legacyWindows + ", observedWindows=" + observedWindows);
        assertEquals(4, originalWindows);
        assertEquals(7, legacyWindows, "Measured old after-pulse reproducer");
        assertEquals(originalWindows, observedWindows,
                "No extra red windows after intended off edges");
    }

    @Test
    void frameRateAndPacketPhaseSweepNeverReplaysAnOffWindow() {
        for (int fps : new int[]{30, 60, 120, 144, 240}) {
            for (double delay : new double[]{0, 0.125, 0.25, 0.5, 0.875}) {
                Rac1CrateFlashClock clock = new Rac1CrateFlashClock();
                int windows = 0;
                boolean prior = false;
                double priorPhase = -1;
                for (int frame = 0; frame < fps * 3; frame++) {
                    double time = delay + frame * 20.0 / fps;
                    long tick = (long) Math.floor(time);
                    int stamp = (int) Math.floor(time - delay + 1e-9);
                    boolean red = clock.red(60 - stamp, 10000 + stamp, 10000 + tick,
                            (float) (time - tick));
                    assertTrue(clock.elapsedTicks() >= priorPhase, "Never rewind visual phase");
                    if (red && !prior) windows++;
                    prior = red;
                    priorPhase = clock.elapsedTicks();
                }
                assertEquals(4, windows, "fps=" + fps + ", packet phase=" + delay);
            }
        }
    }

    @Test
    void partialTickRolloverAndStalePacketsCannotReenterTheFinishedRedInterval() {
        Rac1CrateFlashClock clock = new Rac1CrateFlashClock();
        assertTrue(clock.red(54, 6, 6, 0.25F));
        assertFalse(clock.red(54, 6, 6, 0.75F));
        assertFalse(clock.red(54, 6, 7, 0)); // Old code sampled counter161 and re-entered red.
        assertFalse(clock.red(55, 5, 7, 0.25F)); // Larger stale counter.
        assertFalse(clock.red(54, 6, 5, 0)); // Client clock correction.
        assertTrue(clock.red(40, 20, 20, 0)); // Next original interval, not suppression of all red.
    }

    @Test
    void packetGapPredictsOnlyOneTickAndNeverChangesTheAuthoritativeFuse() {
        Rac1ExplosiveFuse fuse = new Rac1ExplosiveFuse();
        fuse.restore(54, 6);
        Rac1CrateFlashClock clock = new Rac1CrateFlashClock();
        clock.red(fuse.remaining(), fuse.lastTick(), 6, 0.25F);
        clock.red(fuse.remaining(), fuse.lastTick(), 50000, 0.9F);
        assertEquals(7, clock.elapsedTicks());
        assertEquals(54, fuse.remaining());
        assertEquals(6, fuse.lastTick());
        assertFalse(clock.red(Rac1ExplosiveFuse.TERMINAL, 50000, 50000, 0));
        assertTrue(Double.isNaN(clock.elapsedTicks()));
    }

    @Test
    void groundFallLandAndLateTrackingUseTheSameAuthoritativeCounterTickPair() {
        Rac1CrateFlashClock ground = new Rac1CrateFlashClock();
        assertFalse(ground.red(54, 106, 106, 0.75F));
        Rac1CrateFlashClock falling = new Rac1CrateFlashClock();
        assertFalse(falling.red(54, 106, 106, 0.8F));
        Rac1CrateFlashClock landed = new Rac1CrateFlashClock();
        assertFalse(landed.red(53, 107, 107, 0.2F));
        assertEquals(7.2, landed.elapsedTicks(), 1e-6);
        Rac1CrateFlashClock late = new Rac1CrateFlashClock();
        assertEquals(Rac1ExplosiveFuse.redAtElapsed(40.5), late.red(20, 140, 140, 0.5F));
        assertFalse(late.red(Rac1ExplosiveFuse.IDLE, Long.MIN_VALUE, 140, 0.9F));
        String air = ModSource.of("com/github/giannigr/racmc/block/Rac1FallingExplosive.java");
        assertTrue(air.contains("builder.define(FUSE_TICK, Long.MIN_VALUE)"));
        assertTrue(air.contains("entityData.set(FUSE_TICK, fuse.lastTick())"));
    }

    @Test
    void originalCounterEdgesRemainExactAndInvalidInputsDoNotAddPulses() {
        assertTrue(Rac1ExplosiveFuse.redAtElapsed(6.3));
        assertFalse(Rac1ExplosiveFuse.redAtElapsed(6.4));
        assertFalse(Rac1ExplosiveFuse.redAtElapsed(19.9));
        assertTrue(Rac1ExplosiveFuse.redAtElapsed(20));
        assertFalse(Rac1ExplosiveFuse.redAtElapsed(54.9));
        assertTrue(Rac1ExplosiveFuse.redAtElapsed(55));
        assertFalse(Rac1ExplosiveFuse.redAtElapsed(Double.NaN));
        assertFalse(Rac1ExplosiveFuse.redAtElapsed(-1));
        Rac1CrateFlashClock clock = new Rac1CrateFlashClock();
        assertFalse(clock.red(61, 0, 0, 0));
        clock.red(54, Long.MIN_VALUE, 90000, Float.NaN);
        assertEquals(6, clock.elapsedTicks(), "Unknown timestamp does not extrapolate");
    }
}
