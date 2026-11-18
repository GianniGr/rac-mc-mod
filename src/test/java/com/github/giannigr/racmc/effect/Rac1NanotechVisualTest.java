package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.loot.Rac1NanotechRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1NanotechVisualTest {
    private final EffectSignal.Point base = new EffectSignal.Point(0, 0, 0);
    @Test void freeBreathingRetainsEightCosmeticOrbs() {
        for (int t = 0; t <= 180; t++) {
            var points = Rac1NanotechVisuals.freeOrbs(base, t, 42);
            var center = base.add(0, .5 + Rac1NanotechVisuals.bob(t, 42), 0);
            assertEquals(8, points.size());
            for (var p : points) {
                double r = Math.sqrt(p.distanceSquared(center));
                assertTrue(r >= .099999 && r <= .400001);
            }
        }
    }
    @Test void arrivalsRetireOnlyTheirOwnVisualAndMovingTargetUpdatesPaths() {
        var target = new EffectSignal.Point(3, 1, 0);
        var moved = new EffectSignal.Point(4, 2, 1);
        long seed = 42;
        var first = Rac1NanotechVisuals.attracting(base, target, 0, seed);
        assertEquals(8, first.stream().filter(java.util.Objects::nonNull).count());
        assertNotEquals(Rac1NanotechVisuals.attracting(base, target, 10, seed),
                Rac1NanotechVisuals.attracting(base, moved, 10, seed));
        for (int t = 0; t <= 81; t++) {
            var points = Rac1NanotechVisuals.attracting(base, target, t, seed);
            for (int i = 0; i < 8; i++)
                assertEquals(t >= Rac1NanotechRules.duration(seed, i), points.get(i) == null);
        }
    }
}
