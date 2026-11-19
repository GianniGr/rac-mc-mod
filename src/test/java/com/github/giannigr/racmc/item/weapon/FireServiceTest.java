package com.github.giannigr.racmc.item.weapon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireServiceTest {
    private static final class Harness implements FireService.AmmoPolicy, FireService.ProjectileFactory<Object> {
        int ammo = 1, accepted, discarded, prepared;
        boolean obstructed, veto, mutateInventory;
        @Override public FireService.AmmoReservation reserve() {
            if (ammo == 0) return null;
            ammo--;
            return new FireService.AmmoReservation() {
                boolean resolved;
                public void commit() { assertFalse(resolved); resolved = true; }
                public void refund() { assertFalse(resolved); resolved = true; ammo++; }
            };
        }
        @Override public Object prepare() { prepared++; return obstructed ? null : new Object(); }
        @Override public boolean admit(Object projectile) {
            assertEquals(0, ammo, "Price removed before spawn callbacks");
            if (mutateInventory) ammo = 3; // other callback-owned inventory work must not be overwritten
            return !veto;
        }
        @Override public void discard(Object projectile) { discarded++; }
        boolean fire() { return FireService.fire(this, this, projectile -> accepted++); }
    }
    @Test void acceptedShotPaysExactlyOnceAndFeedbackFollowsAdmission() {
        var h = new Harness();
        assertTrue(h.fire());
        assertEquals(0, h.ammo);
        assertEquals(1, h.accepted);
        assertFalse(h.fire());
        assertEquals(1, h.accepted);
    }
    @Test void obstructionNeverReservesAndVetoRefundsWithoutFeedback() {
        var h = new Harness(); h.obstructed = true;
        assertFalse(h.fire()); assertEquals(1, h.ammo); assertEquals(0, h.discarded);
        h.obstructed = false; h.veto = true;
        assertFalse(h.fire()); assertEquals(1, h.ammo); assertEquals(1, h.discarded);
        assertEquals(0, h.accepted);
    }
    @Test void spawnCallbackInventoryMutationDoesNotLoseTheReservedRefund() {
        var h = new Harness(); h.veto = true; h.mutateInventory = true;
        assertFalse(h.fire()); assertEquals(4, h.ammo); assertEquals(0, h.accepted);
    }
    @Test void syntheticCadenceAndPersistedDryStateAreIndependentOfBlaster() {
        var state = new FireState(HandTransformTest.SYNTHETIC.shotIntervalTicks(), 13);
        assertTrue(state.shotDue(0));
        state.recordShot(0);
        assertFalse(state.shotDue(6)); assertTrue(state.shotDue(7));
        state.recordDryClick(0);
        var restored = new FireState(7, 13);
        restored.restore(state.snapshot());
        assertFalse(restored.dryClickAllowed(100));
        restored.endPull(); restored.beginPull();
        assertTrue(restored.shotDue(1), "A genuinely new pull remains immediate");
        assertFalse(restored.dryClickAllowed(12));
        assertTrue(restored.dryClickAllowed(13));
    }
    @Test void secondDefinitionUsesSameTransformFireAmmoAndProjectileSeamsWithoutBlasterDefaults() {
        var weapon = HandTransformTest.SYNTHETIC;
        var pose = HandPoseSnapshot.standing(new WeaponDefinition.Vector(2, 4, 6),
                37, -21, 19, true, true, 1.2);
        var state = new FireState(weapon.shotIntervalTicks(), 13);
        int[] cells = {3}, feedback = {0};
        record ProbeShot(String behavior, WeaponDefinition.Vector muzzle) {}
        var factory = new FireService.ProjectileFactory<ProbeShot>() {
            public ProbeShot prepare() { return new ProbeShot(weapon.projectilePolicy(), HandTransform.muzzle(weapon, pose)); }
            public boolean admit(ProbeShot shot) { return shot.behavior().equals("test:probe"); }
            public void discard(ProbeShot shot) { fail("Valid synthetic shot unexpectedly discarded"); }
        };
        FireService.AmmoPolicy cellPolicy = () -> {
            if (cells[0] == 0) return null;
            cells[0]--;
            return new FireService.AmmoReservation() {
                public void commit() {}
                public void refund() { cells[0]++; }
            };
        };
        for (int tick = 0; tick < 15; tick++) {
            if (!state.shotDue(tick)) continue;
            int now = tick;
            assertTrue(FireService.fire(cellPolicy, factory, shot -> {
                state.recordShot(now); feedback[0]++;
            }));
        }
        assertEquals("test:cell", weapon.ammoPolicy());
        assertEquals(0, cells[0]);
        assertEquals(3, feedback[0]); // ticks0,7,14, not the Blaster's two-tick cadence
    }
}
