package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;

class EffectProceduralTest {
    private static final Point ZERO = new Point(0, 0, 0);
    private static Rac1BlastTables synthetic() {
        return new Rac1BlastTables(new float[12], new float[12],
                new int[]{0x7f103040, 0x7f103040, 0x7f103040, 0x7f103040, 0x7f103040, 0x7f103040},
                new int[]{0x7f081020, 0x7f081020, 0x7f081020, 0x7f081020, 0x7f081020, 0x7f081020});
    }
    @Test void crateType11SplitsWithinCapacityAndEventuallyReleasesEverything() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 2048);
        var procedural = new Rac1ProceduralEffects(runtime, EffectBankTest.SHA1, synthetic());
        procedural.context(new Point(12, 0, 0), Rac1ProceduralEffects.NO_COLLISION);
        procedural.blast(UUID.randomUUID(), ZERO, new Point(12, 0, 0), 5, false);
        assertEquals(10, procedural.sparks());
        assertEquals(8, procedural.debris());
        int max = 0;
        runtime.advance(0, false);
        for (int i = 1; i <= 600; i++) {
            runtime.advance(i / 60.0, false, procedural::step);
            max = Math.max(max, procedural.sparks());
            assertTrue(procedural.sparks() <= 512 && runtime.size() <= 2048);
        }
        assertTrue(max > 10, "Source generation0/1 splitting never happened");
        assertEquals(0, runtime.size());
        assertEquals(0, procedural.sparks());
        assertEquals(0, procedural.debris());
    }
    @Test void quadraticCurveIsNotInventedLinearSizeOrMinecraftSmoke() {
        assertEquals(0, Rac1ProceduralEffects.curve(400000, 20, 20), 1.0e-6);
        assertTrue(Rac1ProceduralEffects.curve(400000, 20, 10) > 0);
        assertTrue(Rac1ProceduralEffects.curve(400000, 20, 1)
                > Rac1ProceduralEffects.curve(400000, 20, 10));
    }
    @Test void type62UsesIntegerAlphaLifetimeRatherThanRequestedLifetime() {
        var runtime = new EffectRuntime(EffectClock.Profile.NTSC, 10);
        runtime.spawn(new EffectRuntime.Spawn(UUID.randomUUID(),
                new EffectAssetRef(EffectBankTest.SHA1, 1, EffectAssetRef.Bank.PARTICLE, 33),
                EffectRuntime.Geometry.BILLBOARD, EffectRuntime.Program.TRAIL62, ZERO, ZERO,
                10000, 1, ZERO, 0, 0x7f7f4040, 22, 6, EffectMaterial.ADDITIVE));
        runtime.advance(0, false);
        for (int i = 1; i <= 21; i++) runtime.advance(i / 60.0, false);
        assertEquals(1, runtime.size());
        runtime.advance(22 / 60.0, false);
        assertEquals(0, runtime.size());
    }
    @Test void intactNanotechAlwaysHasEightRadiusQuarterOrbsWithNoGameplay() {
        for (double updates : new double[]{0, 1, 30, 60, 1234.5}) {
            var orbs = Rac1NanotechVisuals.orbs(ZERO, updates, 17);
            assertEquals(8, orbs.size());
            Point center = new Point(0, .5 + Rac1NanotechVisuals.bob(updates, 17), 0);
            for (Point orb : orbs) assertEquals(.25 * .25, orb.distanceSquared(center), 1.0e-9);
        }
        assertEquals(Rac1NanotechVisuals.orbs(ZERO, 30, 17), Rac1NanotechVisuals.orbs(ZERO, 30, 17));
    }
    @Test void malformedOverlayAndGeometryFailClosed() {
        assertThrows(com.github.giannigr.racmc.asset.AssetFormatException.class, () ->
                Rac1NanotechImporter.parse(new byte[16], com.github.giannigr.racmc.iso.Rac1DiscRevision.PAL_V2_00));
        assertThrows(IllegalArgumentException.class, () ->
                new NanotechTables(new float[1], new float[0], new int[0], new float[0],
                        new int[0], new float[0], new int[0], new float[0]));
    }
}
