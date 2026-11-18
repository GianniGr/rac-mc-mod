package com.github.giannigr.racmc.effect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectProjectionTest {
    @Test void originalDepthBiasDiamondAndVerticalCompensationAreExplicit() {
        var quad = EffectSpriteProjection.project(210000, 0, 10, 512.0 / 416, 1, 128);
        assertNotNull(quad);
        assertEquals(10 / 10.5, quad.xy()[2], 1.0e-6); // a.x, not size/420000 square half-width
        assertEquals(-1.0625 * 10 / 10.5, quad.xy()[1], 1.0e-6);
        assertArrayEquals(new float[]{1, 0, 0, 0, 0, 1, 1, 1}, quad.uv());
        assertEquals(1, quad.alpha(), 1.0e-9);
    }
    @Test void sourcePixelEquationMatchesCurrentPerspectiveWithoutFakeMeshExtraction() {
        for (double tangent : new double[]{.4, .63, 1})
            for (double depth : new double[]{2, 10, 40}) {
                var quad = EffectSpriteProjection.project(200000, 0, depth, 16.0 / 9, 1, 128);
                double projected = quad.xy()[2] / (depth * tangent);
                double sourceEquation = 200000 / (210000 * tangent * (depth + .5));
                assertEquals(sourceEquation, projected, 1.0e-6);
            }
    }
    @Test void fixed12NearAndFarFadeAndInvalidCamerasAreBounded() {
        assertNull(EffectSpriteProjection.project(200000, 0, 1, 1, 1, 128));
        assertNull(EffectSpriteProjection.project(200000, 0, -2, 1, 1, 128));
        assertNull(EffectSpriteProjection.project(200000, 0, 128, 1, 1, 128));
        assertEquals(.5, EffectSpriteProjection.project(200000, 0, 1.5, 1, 1, 128).alpha(), 1.0e-9);
        assertEquals(.5, EffectSpriteProjection.project(200000, 0, 120, 1, 1, 128).alpha(), 1.0e-9);
        assertNull(EffectSpriteProjection.project(200000, Double.NaN, 10, 1, 1, 128));
        assertNull(EffectSpriteProjection.project(200000, 0, 10, 0, 1, 128));
    }
}
