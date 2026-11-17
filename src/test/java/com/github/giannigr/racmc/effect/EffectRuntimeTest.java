package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;

class EffectRuntimeTest {
    private static final Point ZERO = new Point(0, 0, 0);
    @Test void invalidLifetimesAreRefusedAndNormalSortingIsStable() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 64);
        var ref = new EffectAssetRef(EffectBankTest.SHA1, 1, EffectAssetRef.Bank.PARTICLE, 0);
        UUID owner = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new EffectRuntime.Spawn(owner, ref,
                EffectRuntime.Geometry.BILLBOARD, EffectRuntime.Program.SPARK27, ZERO, ZERO,
                10000, 1, ZERO, 0, 0x7f808080, 0, 0, EffectMaterial.NORMAL));
        for (int x : new int[]{1, 3, 2})
            runtime.spawn(new EffectRuntime.Spawn(owner, ref, EffectRuntime.Geometry.BILLBOARD,
                    EffectRuntime.Program.SPARK27, new Point(x, 0, 0), ZERO, 10000, 1, ZERO,
                    0, 0x7f808080, 20, 0, EffectMaterial.NORMAL));
        assertEquals(List.of(3.0, 2.0, 1.0), runtime.snapshot(ZERO, 1).stream()
                .map(draw -> draw.position().x()).toList());
    }
    @Test void flashStartsWithFourUpdateHeadStartAndExpiresWithoutTripleDraws() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 64);
        Rac1BlastVisuals.emit(runtime, EffectBankTest.SHA1, UUID.randomUUID(), ZERO, ZERO, 1, false);
        var shells = runtime.snapshot(ZERO, 1).stream()
                .filter(draw -> draw.geometry() == EffectRuntime.Geometry.MESH).toList();
        assertEquals(3.5 * 4 / 27, shells.get(0).size(), 1.0e-6);
        runtime.advance(0, false);
        for (int frame=1; frame<=25; frame++) runtime.advance(frame / 60.0, false);
        assertEquals(0, runtime.snapshot(ZERO, 1).stream()
                .filter(draw -> draw.geometry() == EffectRuntime.Geometry.MESH).count());
        assertEquals(6, runtime.size()); // Dust is not truncated to the blast collision window.
    }
    @Test void clockIsFrameRateIndependentAndRegional() {
        for (EffectClock.Profile profile : EffectClock.Profile.values())
            for (int fps : new int[]{30, 60, 144}) {
                var clock = new EffectClock(profile);
                clock.advance(0, false);
                int steps = 0;
                for (int frame = 1; frame <= fps; frame++) steps += clock.advance(frame / (double) fps, false).steps();
                assertEquals(profile.hz(), steps);
            }
    }
    @Test void pauseAndLongStallDoNotReplayBursts() {
        var clock = new EffectClock(EffectClock.Profile.NTSC);
        clock.advance(0, false);
        assertEquals(0, clock.advance(20, true).steps());
        assertEquals(1, clock.advance(20 + 1.0 / 60, false).steps());
        assertTrue(clock.advance(25, false).discontinuity());
        assertThrows(IllegalArgumentException.class, () -> clock.advance(Double.NaN, false));
    }
    @Test void blastHasSixDustAndTwoShellsNotTripleRendered() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 64);
        assertEquals(8, Rac1BlastVisuals.emit(runtime, EffectBankTest.SHA1, UUID.randomUUID(),
                ZERO, ZERO, 1, true));
        var draws = runtime.snapshot(ZERO, 1);
        assertEquals(2, draws.stream().filter(draw -> draw.geometry() == EffectRuntime.Geometry.MESH).count());
        assertEquals(6, draws.stream().filter(draw -> draw.geometry() == EffectRuntime.Geometry.BILLBOARD).count());
        assertTrue(draws.stream().allMatch(draw -> draw.material().depthTest() && !draw.material().depthWrite()));
    }
    @Test void blastExtraShellsRespectDistanceAndSuppression() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 64);
        assertEquals(11, Rac1BlastVisuals.emit(runtime, EffectBankTest.SHA1, UUID.randomUUID(),
                ZERO, new Point(10, 0, 0), 1, true));
        runtime.clear();
        assertEquals(8, Rac1BlastVisuals.emit(runtime, EffectBankTest.SHA1, UUID.randomUUID(),
                ZERO, new Point(10, 0, 0), 1, false));
    }
    @Test void dustLivesFortyOriginalUpdatesAndPoolIsBounded() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 3);
        assertEquals(3, Rac1BlastVisuals.emit(runtime, EffectBankTest.SHA1, UUID.randomUUID(), ZERO, ZERO, 1, false));
        runtime.advance(0, false);
        for (int frame = 1; frame <= 39; frame++) runtime.advance(frame / 60.0, false);
        assertEquals(3, runtime.size());
        runtime.advance(40 / 60.0, false);
        assertEquals(0, runtime.size());
    }
    @Test void deterministicLocalSeedAndRLowBlueArePreserved() {
        var a = new EffectRuntime(EffectClock.Profile.NTSC, 64);
        var b = new EffectRuntime(EffectClock.Profile.NTSC, 64);
        UUID owner = UUID.randomUUID();
        Rac1BlastVisuals.emit(a, EffectBankTest.SHA1, owner, ZERO, ZERO, 7, true);
        Rac1BlastVisuals.emit(b, EffectBankTest.SHA1, owner, ZERO, ZERO, 7, true);
        assertEquals(a.snapshot(ZERO, 1), b.snapshot(ZERO, 1));
        int colour = EffectMaterial.rgba(0x7f7f4040);
        assertTrue((colour >>> 8 & 255) > (colour >>> 24));
    }
    @Test void generationStopAndDiscontinuityReleaseAllInstances() {
        var runtime = new EffectRuntime(EffectClock.Profile.PAL, 64);
        UUID owner = UUID.randomUUID();
        Rac1BlastVisuals.emit(runtime, EffectBankTest.SHA1, owner, ZERO, ZERO, 1, false);
        runtime.stopOwner(owner);
        assertEquals(0, runtime.size());
        Rac1BlastVisuals.emit(runtime, EffectBankTest.SHA1, owner, ZERO, ZERO, 1, false);
        runtime.advance(0, false);
        runtime.advance(1, false);
        assertEquals(0, runtime.size());
    }
    @Test void blasterOwnsGlowAndSeventeenTrailSpritesAndClosesExactlyOnce() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 64);
        var adapter = new Rac1BlasterVisuals(runtime, EffectBankTest.SHA1, UUID.randomUUID(), ZERO, 1);
        assertEquals(18, runtime.size());
        adapter.update(new Point(1, 0, 0), new Point(1, 0, 0), 13);
        assertTrue(runtime.snapshot(ZERO, 1).stream().anyMatch(draw -> draw.position().x() < 1));
        adapter.impact(ZERO, new Point(1, 0, 0), new Point(-1, 0, 0));
        assertEquals(8, runtime.size()); // 5 sparks + 3 smoke; original trail gone.
        adapter.impact(ZERO, new Point(1, 0, 0), new Point(-1, 0, 0));
        adapter.end(ZERO);
        assertEquals(8, runtime.size());
        runtime.clear();
        assertEquals(0, runtime.size());
    }
    @Test void blasterCapacityFailureIsAtomicAndUntrackingDoesNotInventImpact() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 17);
        try (var adapter = new Rac1BlasterVisuals(runtime, EffectBankTest.SHA1, UUID.randomUUID(), ZERO, 1)) {
            assertFalse(adapter.active());
            assertEquals(0, runtime.size());
        }
        var full = new EffectRuntime(EffectClock.Profile.NTSC, 64);
        new Rac1BlasterVisuals(full, EffectBankTest.SHA1, UUID.randomUUID(), ZERO, 1).close();
        assertEquals(0, full.size()); // No smoke/sparks on plain close/untrack.
    }
    @Test void staleCatalogCompletionIsClosedAndCannotReplaceNewGeneration() throws Exception {
        int[] clears = {0}, closed = {0};
        var generation = new EffectCatalogGeneration<AutoCloseable>(() -> clears[0]++);
        long old = generation.generation();
        long next = generation.invalidate();
        assertFalse(generation.attach(old, () -> closed[0]++));
        assertTrue(generation.attach(next, () -> closed[0]++));
        generation.close();
        generation.close();
        assertEquals(2, closed[0]);
        assertEquals(3, clears[0]);
    }
}
