package com.github.giannigr.racmc.client.rescue;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Rac1PodOccupancyTest {
    @Test
    void onlyAnUnclaimedNaturalHullShowsClank() {
        assertTrue(Rac1PodViewer.showOccupant(true, false));
        assertFalse(Rac1PodViewer.showOccupant(true, true));
        assertFalse(Rac1PodViewer.showOccupant(false, false));
        assertFalse(Rac1PodViewer.showOccupant(false, true));
    }
}
