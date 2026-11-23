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

    @Test void creativePhysicalBackAdmitsHydroWithoutChangingSurvivalOrTheDurableProjection() {
        var back = new Rac1ClankEquipment(true, true, false, true, Rac1ClankEquipment.Module.HELI);
        var session = new Rac1ClankSession(UUID.randomUUID(), 1, back);
        assertFalse(session.testHydro(), "The released fixture-only gate rejected this worn Creative Back");
        assertFalse(session.hydroAvailable(false));
        assertTrue(session.hydroAvailable(true));
        assertEquals(Rac1ClankEquipment.Module.NONE, session.fixture());
        session.testEquipment(Rac1ClankEquipment.Module.THRUSTER);
        assertEquals(Rac1ClankEquipment.Module.THRUSTER, session.fixture());
        assertTrue(session.hydroAvailable(false), "Existing explicit test sessions still work");
        session.gearEquipment(back);
        assertEquals(back, session.equipment(), "Dismissing an overlay restores the same physical Back");
        assertEquals(Rac1ClankEquipment.Module.NONE, session.fixture());
        assertFalse(session.hydroAvailable(false));
        session.gearEquipment(new Rac1ClankEquipment(true, true, false, false, Rac1ClankEquipment.Module.HELI));
        assertFalse(session.hydroAvailable(true));
    }
}
