package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WrenchThrowReceiverGateTest {
    @Test void sameIdentityPersistsAcrossLegsAndReopensAt37SourceUpdatesNot37McTicks() {
        var gate = new WrenchThrowReceiverGate();
        UUID owner = UUID.randomUUID(), target = UUID.randomUUID();
        assertTrue(gate.message(owner, target, 1, 0));
        gate.accepted(owner, target, 1, 0);
        assertFalse(gate.message(owner, target, 1, 20)); // Turnaround grants no new allowance.
        assertFalse(gate.message(owner, target, 1, 36));
        assertTrue(gate.message(owner, target, 1, 37));
        assertFalse(gate.message(owner, target, 1, 37)); // Same source sample coalesces.
        assertEquals(5.1F, WrenchDamageDelivery.THROW_DAMAGE);
    }

    @Test void identityChangesAreServerOwnedAndAnotherOwnerCannotResetThisReceiverGate() {
        var gate = new WrenchThrowReceiverGate();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), target = UUID.randomUUID();
        assertTrue(gate.message(a, target, 1, 0));
        gate.accepted(a, target, 1, 0);
        gate.accepted(b, target, 2, 1);
        assertFalse(gate.message(a, target, 1, 2));
        assertTrue(gate.message(a, target, 2, 3));
        gate.accepted(a, target, 2, 3);
        gate.accepted(a, target, 0x47, 4); // A genuinely accepted native held hit, not a packet.
        assertTrue(gate.message(a, target, 2, 5));
    }

    @Test void rejectedNativeHurtDoesNotBecomeDamageAndNoPermanentFlightBlacklistExists() {
        var gate = new WrenchThrowReceiverGate();
        UUID owner = UUID.randomUUID(), target = UUID.randomUUID();
        assertTrue(gate.message(owner, target, 1, 0));
        // Native armor/hurt/protection rejected it: caller does not commit a successful hit.
        assertFalse(gate.message(owner, target, 1, 0));
        assertTrue(gate.message(owner, target, 1, 1));
        gate.accepted(owner, target, 1, 1);
        assertTrue(gate.message(owner, target, 1, 38));
    }
}
