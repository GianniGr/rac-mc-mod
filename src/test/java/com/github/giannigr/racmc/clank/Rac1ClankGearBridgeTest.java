package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class Rac1ClankGearBridgeTest {
    @Test void identicalHeliGearAndHeliFixtureKeepSeparateHydroAccess() {
        var id = UUID.randomUUID();
        var heliBack = new Rac1ClankEquipment(true, true, false, true, Rac1ClankEquipment.Module.HELI);
        var session = new Rac1ClankSession(id, 1, heliBack);
        assertFalse(session.testHydro());
        session.testEquipment(Rac1ClankEquipment.Module.HELI);
        assertEquals(heliBack, session.equipment());
        assertTrue(session.testHydro());
        session.invalidate(); // Native dimension changes retain an active temporary session.
        assertTrue(session.testHydro());
        session.testEquipment(Rac1ClankEquipment.Module.THRUSTER);
        assertTrue(session.equipment().thrusterOwned());
        assertTrue(session.testHydro());
        session.gearEquipment(heliBack); // Gear projection after an explicit change or respawn.
        assertEquals(heliBack, session.equipment());
        assertFalse(session.testHydro());
        session.testEquipment(Rac1ClankEquipment.Module.NONE);
        assertEquals(Rac1ClankEquipment.EMPTY, session.equipment());
        assertFalse(session.testHydro());
    }
}
