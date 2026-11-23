package com.github.giannigr.racmc.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BombGloveAmmoPickupTest {
    @Test
    void emitsOnlyForActualGainEvenWhenInsertionIsPartial() {
        var count = new AtomicInteger(38);
        var cues = new ArrayList<Integer>();
        assertFalse(BombGloveAmmoPickup.observe(3, count::get, () -> {
            count.addAndGet(2);
            return false;
        }, cues::add));
        assertEquals(List.of(2), cues);
    }

    @Test
    void noCueForRejectedInsertionOrSuccessfulDiscardWithoutGain() {
        var cues = new ArrayList<Integer>();
        assertFalse(BombGloveAmmoPickup.observe(3, () -> 40, () -> false, cues::add));
        assertTrue(BombGloveAmmoPickup.observe(3, () -> 40, () -> true, cues::add));
        assertTrue(cues.isEmpty());
    }
}
