package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the life of the arrows of the Blaster.
 *
 * <p>The rule holds no Minecraft types, so it is checked on a plain virtual machine.
 */
class Rac1ArrowLifetimeTest {

    @Test
    @DisplayName("The arrow of the Blaster flies for twelve ticks")
    void lifetimeIsTwelveTicks() {
        assertEquals(12, Rac1ArrowLifetime.LIFETIME_TICKS);
        assertEquals(0.6f, Rac1ArrowLifetime.LIFETIME_TICKS / 20.0f, 1.0e-4f);
    }

    @Test
    @DisplayName("The arrow of the Blaster lives while it flies")
    void arrowLivesWhileItFlies() {
        for (int tick = 0; tick < Rac1ArrowLifetime.LIFETIME_TICKS; tick++) {
            assertFalse(Rac1ArrowLifetime.expired(1000L, 1000L + tick),
                    "The arrow must live at tick " + tick);
        }
    }

    @Test
    @DisplayName("The arrow of the Blaster is removed when its flight is over")
    void arrowDiesWhenItsFlightIsOver() {
        assertTrue(Rac1ArrowLifetime.expired(1000L, 1000L + Rac1ArrowLifetime.LIFETIME_TICKS));
        assertTrue(Rac1ArrowLifetime.expired(1000L, 1000L + Rac1ArrowLifetime.LIFETIME_TICKS + 100));
    }

    @Test
    @DisplayName("The life of the arrow of the Blaster does not depend on the tick it was fired on")
    void lifetimeDoesNotDependOnTheTick() {
        for (long birth : new long[] { 0L, 1L, 12345L, 999999L }) {
            assertFalse(Rac1ArrowLifetime.expired(birth, birth + Rac1ArrowLifetime.LIFETIME_TICKS - 1));
            assertTrue(Rac1ArrowLifetime.expired(birth, birth + Rac1ArrowLifetime.LIFETIME_TICKS));
        }
    }
}
