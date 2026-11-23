package com.github.giannigr.racmc.loot;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltCreditTest {
    @Test
    void creditsOnlyTheSelectedAccountAndRejectsInvalidGrants() {
        var ledger = new Rac1BoltLedger();
        var player = UUID.randomUUID();
        var other = UUID.randomUUID();

        assertTrue(ledger.credit(player, 25_000));
        assertTrue(ledger.credit(player, 1));
        assertEquals(25_001, ledger.balance(player));
        assertEquals(2, ledger.revision(player));
        assertEquals(0, ledger.balance(other));

        assertFalse(ledger.credit(player, 0));
        assertFalse(ledger.credit(player, -1));
        assertFalse(ledger.credit(player, Long.MAX_VALUE));
        assertEquals(25_001, ledger.balance(player));
        assertEquals(2, ledger.revision(player));

        assertTrue(ledger.credit(other, Long.MAX_VALUE));
        assertFalse(ledger.credit(other, 1));
        assertEquals(Long.MAX_VALUE, ledger.balance(other));
        assertEquals(1, ledger.revision(other));
    }
}
