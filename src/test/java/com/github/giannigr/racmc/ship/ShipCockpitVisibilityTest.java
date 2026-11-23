package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.Rac1TravelShip.AccessPhase.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShipCockpitVisibilityTest {
    @Test
    void restoresOriginalHullOnlyWhenParkedWithoutAnActiveRiderOrPath() {
        assertFalse(TravelShipPlaceholderRenderer.openCockpit(PARKED, false, false));
        assertTrue(TravelShipPlaceholderRenderer.openCockpit(PARKED, true, false));
        assertTrue(TravelShipPlaceholderRenderer.openCockpit(PARKED, false, true));
        assertTrue(TravelShipPlaceholderRenderer.openCockpit(BOARDING, false, false));
        assertTrue(TravelShipPlaceholderRenderer.openCockpit(PILOTING, true, false));
        assertTrue(TravelShipPlaceholderRenderer.openCockpit(EXITING, false, false));
    }
}
