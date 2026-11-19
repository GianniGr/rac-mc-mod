package com.github.giannigr.racmc.item.weapon;

import org.junit.jupiter.api.Test;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;
import static org.junit.jupiter.api.Assertions.*;

class AnimatedMuzzleAnchorTest {
    @Test void everyAnimationPhaseAndBlendKeepsRingCenteredWithoutChangingShapeOrScale() {
        var anchor = new AnimatedMuzzleAnchor(new int[]{0, 1, 2, 3});
        var target = new Vector(.2305, -.03, .6335);
        for (float phase : new float[]{0, .1f, .2f, .3f, .4f})
            for (float blend : new float[]{0, .5f, 1}) {
                float x = .2305f + phase * blend, z = .6335f + .2f * blend * phase;
                float[] points = {x - .1f, -.03f, z, x + .1f, -.03f, z,
                        x, -.13f, z, x, .07f, z};
                float[] before = points.clone();
                var correction = anchor.correction(points, target);
                double cx = 0, cy = 0, cz = 0;
                for (int i = 0; i < points.length; i += 3) {
                    cx += points[i] + correction.x(); cy += points[i + 1] + correction.y();
                    cz += points[i + 2] + correction.z();
                }
                assertEquals(target.x(), cx / 4, 1e-9);
                assertEquals(target.y(), cy / 4, 1e-9);
                assertEquals(target.z(), cz / 4, 1e-9);
                assertArrayEquals(before, points, "Sampling cannot rewrite original animation geometry");
            }
    }
}
