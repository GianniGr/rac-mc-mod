package com.github.giannigr.racmc.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltHudAnimationTest {
    @Test void panelThenTextClosesInsideFourSecondsAndRepeatedPickupDoesNotCollapse() {
        var state = new Rac1BoltHudAnimation();
        long start = 1_000_000_000L, step = 10_000_000;
        assertEquals(new Rac1BoltHudAnimation.Phase(0, 0), state.update(start, start, 8, 8, step));
        assertEquals(new Rac1BoltHudAnimation.Phase(1, 0), state.update(start + step * 8, start, 8, 8, step));
        assertEquals(new Rac1BoltHudAnimation.Phase(1, 1), state.update(start + step * 16, start, 8, 8, step));
        long second = start + 3_000_000_000L;
        assertEquals(new Rac1BoltHudAnimation.Phase(1, 1), state.update(second, second, 8, 8, step));
        assertEquals(new Rac1BoltHudAnimation.Phase(1, 1), state.update(start + 4_000_000_000L, second, 8, 8, step));
        var closing = state.update(second + 3_920_000_000L, second, 8, 8, step);
        assertEquals(1, closing.panel()); assertEquals(0, closing.text());
        var closed = state.update(second + 4_000_000_000L, second, 8, 8, step);
        assertEquals(new Rac1BoltHudAnimation.Phase(0, 0), closed);
    }
    @Test void pickupDuringCloseReversesAndNewWindowResets() {
        var state = new Rac1BoltHudAnimation();
        long start = 1_000_000_000L, step = 10_000_000;
        state.update(start + step * 16, start, 8, 8, step);
        long next = start + 3_960_000_000L;
        assertEquals(.5F, state.update(next, start, 8, 8, step).panel());
        assertEquals(.5F, state.update(next, next, 8, 8, step).panel());
        assertEquals(1, state.update(next + step * 12, next, 8, 8, step).text());
        long later = next + 10_000_000_000L;
        assertEquals(0, state.update(later, later, 8, 8, step).panel());
        assertEquals(0x40E08060, Rac1BoltHudAnimation.tween(0x00E08060, 0x80E08060, .5F));
        state.reset();
        assertEquals(new Rac1BoltHudAnimation.Phase(0, 0), state.update(later, 0, 8, 8, step));
    }
}
