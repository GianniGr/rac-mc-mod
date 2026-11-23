package com.github.giannigr.racmc.client.ship;

import static com.github.giannigr.racmc.ship.ShipThrust.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GreenShipRearFxTest {
    @Test
    void onlyDeliveredThrustSubmitsTwinRearJetsAndBoostExtendsBoth() {
        assertTrue(GreenShipFxGeometry.rear(OFF, 0).isEmpty());
        var normal = GreenShipFxGeometry.rear(NORMAL, 0);
        var boost = GreenShipFxGeometry.rear(BOOST, 0);
        assertEquals(4, normal.size());
        assertEquals(4, boost.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(-3.5f, normal.get(i).corners().getFirst().forward());
            assertTrue(boost.get(i).corners().get(2).forward() < normal.get(i).corners().get(2).forward());
        }
    }
}
