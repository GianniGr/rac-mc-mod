package com.github.giannigr.racmc.asset.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreenShipFxBeatsTest {
    @Test
    void parkedGlowDoesNotDriveDepartureFx() {
        assertEquals(100, GreenShipFxBeats.parked(0).glow());
        assertEquals(200, GreenShipFxBeats.parked(64).glow());
        assertEquals(0, GreenShipFxBeats.parked(421).flameQuads());
        assertEquals(0, GreenShipFxBeats.parked(421).exhaustBursts());
    }

    @Test
    void departureUsesTheOriginalRampAndStrictFlameWindow() {
        assertEquals(0, GreenShipFxBeats.takeoff(360).glow());
        assertEquals(0, GreenShipFxBeats.takeoff(420).flameQuads());
        assertEquals(8, GreenShipFxBeats.takeoff(421).flameQuads());
        assertEquals(5, GreenShipFxBeats.takeoff(503).flameTexture());
        assertEquals(0, GreenShipFxBeats.takeoff(504).flameQuads());
    }

    @Test
    void landingCutsExhaustBeforeFlamesAndReturnsToPulse() {
        assertEquals(2, GreenShipFxBeats.landing(199).exhaustBursts());
        assertEquals(6, GreenShipFxBeats.EXHAUST_VENTS.size());
        assertEquals(0, GreenShipFxBeats.landing(200).exhaustBursts());
        assertEquals(0x32, GreenShipFxBeats.landing(239).flameCommand());
        assertEquals(90, GreenShipFxBeats.landing(240).flameCommand());
        assertEquals(0, GreenShipFxBeats.landing(300).flameQuads());
        assertEquals(0, GreenShipFxBeats.landing(360).glow());
    }
}
