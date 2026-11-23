package com.github.giannigr.racmc.client.gear;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import com.github.giannigr.racmc.gear.Rac1GearState;
import org.junit.jupiter.api.Test;

class Rac1GearViewTest {
    private static final Rac1ClankEquipment UNLOCKED =
            new Rac1ClankEquipment(true, true, false, false, Rac1ClankEquipment.Module.HELI);

    @Test void notReadyIsDistinctFromKnownLockedAndOutOfOrderSnapshotsCannotUndoUnlock() {
        var view = new Rac1GearView();
        var current = new Rac1GearView.Context(new Object(), new Object(), new Object());
        var locked = new Rac1GearState(5, 0, Rac1ClankEquipment.EMPTY, false, false, false);
        var unlocked = new Rac1GearState(5, 1, UNLOCKED, true, false, false);
        assertTrue(view.state(current).isEmpty());
        view.accept(current, locked);
        assertFalse(view.state(current).orElseThrow().clankUnlocked());
        view.accept(current, unlocked);
        view.accept(current, locked);
        view.accept(current, new Rac1GearState(4, 1, UNLOCKED, true, false, false));
        assertEquals(unlocked, view.state(current).orElseThrow());
    }

    @Test void dimensionAndDeathRequireRebasedSnapshotEvenOnTheSameConnection() {
        var view = new Rac1GearView();
        var original = new Rac1GearView.Context(new Object(), new Object(), new Object());
        var unlocked = new Rac1GearState(1, 1, UNLOCKED, true, false, false);
        view.accept(original, unlocked);
        var otherDimension = new Rac1GearView.Context(original.connection(), new Object(), new Object());
        view.accept(otherDimension, unlocked);
        assertTrue(view.state(otherDimension).isEmpty());
        view.accept(otherDimension, unlocked);
        assertTrue(view.state(otherDimension).isEmpty());
        view.accept(otherDimension, new Rac1GearState(2, 1, UNLOCKED, true, false, false));
        assertTrue(view.state(otherDimension).orElseThrow().clankUnlocked());

        var respawn = new Rac1GearView.Context(otherDimension.connection(), otherDimension.level(), new Object());
        assertTrue(view.state(respawn).isEmpty());
        view.accept(respawn, new Rac1GearState(3, 0, Rac1ClankEquipment.EMPTY, false, false, false));
        assertTrue(view.state(respawn).isEmpty());
        view.accept(respawn, new Rac1GearState(3, 1, UNLOCKED, true, false, false));
        assertTrue(view.state(respawn).orElseThrow().clankUnlocked());
    }

    @Test void reconnectUsesNewIdentityAndDoesNotLeakPreviousSaveState() {
        var view = new Rac1GearView();
        var before = new Rac1GearView.Context(new Object(), new Object(), new Object());
        view.accept(before, new Rac1GearState(123, 1, UNLOCKED, true, false, false));
        var after = new Rac1GearView.Context(new Object(), new Object(), new Object());
        assertTrue(view.state(after).isEmpty());
        view.accept(after, new Rac1GearState(1, 0, Rac1ClankEquipment.EMPTY, false, false, false));
        assertFalse(view.state(after).orElseThrow().clankUnlocked());
        view.clear();
        assertTrue(view.state(after).isEmpty());
    }

    @Test void nativeUseSnapshotCanAtomicallyUnlockAndShowWornWithNoLooseItem() {
        var view = new Rac1GearView();
        var current = new Rac1GearView.Context(new Object(), new Object(), new Object());
        var locked = new Rac1GearState(1, 1, Rac1ClankEquipment.EMPTY, true, false, false);
        var worn = new Rac1ClankEquipment(true, true, false, true, Rac1ClankEquipment.Module.HELI);
        var confirmed = new Rac1GearState(1, 2, worn, false, false, false);
        view.accept(current, locked);
        view.accept(current, confirmed);
        view.accept(current, locked);
        assertEquals(confirmed, view.state(current).orElseThrow());
    }
}
