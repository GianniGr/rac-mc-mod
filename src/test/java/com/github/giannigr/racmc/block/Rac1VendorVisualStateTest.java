package com.github.giannigr.racmc.block;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Rac1VendorVisualStateTest {
    @Test
    void enterAndExitHaveInclusiveSeparateCylinderBounds() {
        Rac1VendorVisualState state = new Rac1VendorVisualState();
        assertTrue(state.contains(Math.pow(Rac1VendorVisualState.ENTER_RADIUS, 2),
                Rac1VendorVisualState.ENTER_HEIGHT));
        assertFalse(state.contains(Math.pow(Rac1VendorVisualState.ENTER_RADIUS + .001, 2), 0));
        assertFalse(state.contains(0, Rac1VendorVisualState.ENTER_HEIGHT + .001));
        state.tick(true);
        assertTrue(state.near());
        assertTrue(state.contains(Math.pow(Rac1VendorVisualState.EXIT_RADIUS, 2),
                -Rac1VendorVisualState.EXIT_HEIGHT));
        assertFalse(state.contains(Math.pow(Rac1VendorVisualState.EXIT_RADIUS + .001, 2), 0));
        assertFalse(state.contains(0, -Rac1VendorVisualState.EXIT_HEIGHT - .001));
    }

    @Test
    void growsShrinksAndPollsAtRetailCadenceNotRenderFramerate() {
        Rac1VendorVisualState state = new Rac1VendorVisualState();
        assertFalse(state.near());
        assertEquals(0, state.growth(1));
        state.tick(true);
        assertEquals(.15f, state.growth(.5f), 1e-6);
        state.tick(false);
        state.tick(false);
        assertTrue(state.near(), "8 retail frames have not elapsed at the prior polls");
        state.tick(false);
        assertFalse(state.near());
        assertEquals(.6f, state.growth(1), 1e-6);
        for (int i = 0; i < 20; i++) {
            state.tick(false);
        }
        assertEquals(0, state.growth(1));
        for (int i = 0; i < 20; i++) {
            state.tick(true);
        }
        assertEquals(1, state.growth(1));
    }

    @Test
    void missingOrInvalidPlayerNeverTriggers() {
        Rac1VendorVisualState state = new Rac1VendorVisualState();
        assertFalse(state.contains(Double.POSITIVE_INFINITY, 0));
        assertFalse(state.contains(Double.NaN, 0));
        assertFalse(state.contains(-1, 0));
        assertFalse(state.contains(0, Double.NaN));
        state.tick(false);
        assertFalse(state.near());
    }

    @Test
    void nearestEligiblePlayerDoesNotIncludeDeadSpectatorsOrWrongHeight() {
        Rac1VendorVisualState state = new Rac1VendorVisualState();
        assertEquals(Double.POSITIVE_INFINITY, state.playerDistanceSquared(false, false, 0, 0, 0));
        assertEquals(Double.POSITIVE_INFINITY, state.playerDistanceSquared(true, true, 0, 0, 0));
        assertEquals(Double.POSITIVE_INFINITY, state.playerDistanceSquared(true, false, 0, 100, 0));
        assertEquals(Double.POSITIVE_INFINITY,
                state.playerDistanceSquared(true, false, Double.NaN, 0, 0));
        double nearest = Math.min(state.playerDistanceSquared(true, true, 0, 0, 0),
                state.playerDistanceSquared(true, false, 2, 0, 2));
        assertEquals(8, nearest);
        assertTrue(state.contains(nearest, 0));
        state.tick(true);
        double high = (Rac1VendorVisualState.ENTER_HEIGHT + Rac1VendorVisualState.EXIT_HEIGHT) / 2;
        assertEquals(1, state.playerDistanceSquared(true, false, 1, high, 0),
                "Near state keeps players inside the vertical hysteresis band");
    }
}
