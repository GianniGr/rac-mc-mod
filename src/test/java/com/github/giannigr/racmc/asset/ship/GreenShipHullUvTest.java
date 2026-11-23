package com.github.giannigr.racmc.asset.ship;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GreenShipHullUvTest {
    @Test
    void movesOneEntireAtlasTileWithoutWrappingIndividualVerticesOrTouchingV() {
        float[] raw = {-1.99f, .20f, -1.003f, .90f, -1.4f, .30f};
        float[] fit = GreenShipHullPose.canonicalU(raw);
        assertEquals(-1.99f, raw[0]);
        for (int i = 0; i < raw.length; i += 2) {
            assertEquals(raw[i] + 2, fit[i], 1e-7);
            assertEquals(raw[i + 1], fit[i + 1]);
        }
        assertEquals(raw[2] - raw[0], fit[2] - fit[0], 1e-7);
        float[] mixed = {-1.9f, 0, -.08f, 1};
        assertSame(mixed, GreenShipHullPose.canonicalU(mixed));
    }
}
