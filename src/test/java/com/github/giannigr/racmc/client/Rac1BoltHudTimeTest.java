package com.github.giannigr.racmc.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltHudTimeTest {
    @Test void fourSecondsSinceLastPickupNotInitialSyncOrSpend() {
        assertFalse(Rac1BoltHudTime.visible(0, 1));
        assertTrue(Rac1BoltHudTime.visible(100, 100));
        assertTrue(Rac1BoltHudTime.visible(100, 4_000_000_099L));
        assertFalse(Rac1BoltHudTime.visible(100, 4_000_000_100L));
        assertTrue(Rac1BoltHudTime.visible(3_000_000_100L, 4_000_000_100L));
        assertFalse(Rac1BoltHudTime.visible(100, 99));
    }
}
