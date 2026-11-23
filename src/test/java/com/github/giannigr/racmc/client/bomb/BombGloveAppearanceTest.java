package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.client.bomb.BombGloveAppearance.Modulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BombGloveAppearanceTest {
    @Test
    void heldPulseUsesSignedTruncationAndSourceChannels() {
        assertEquals(new Modulation(159, 159, 159), BombGloveAppearance.held(0));
        assertEquals(new Modulation(254, 254, 254), BombGloveAppearance.held(23));
        assertEquals(new Modulation(64, 64, 64), BombGloveAppearance.held(69));
    }

    @Test
    void flightColorStartsNeutralAndThenWarmsToRed() {
        assertEquals(new Modulation(128, 128, 128), BombGloveAppearance.flight(1));
        assertEquals(new Modulation(191, 96, 96), BombGloveAppearance.flight(.5f));
        assertEquals(new Modulation(255, 64, 64), BombGloveAppearance.flight(0));
        assertEquals(BombGloveAppearance.flight(0), BombGloveAppearance.flight(-1));
        assertThrows(IllegalArgumentException.class, () -> BombGloveAppearance.flight(Float.NaN));
    }
}
