package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the rules that decide whether the Blaster may fire.
 *
 * <p>The rules hold no Minecraft types, so they are checked on a plain virtual machine. The ways
 * of play of the game are named after the ways of play of the game of blocks.
 */
class Rac1FirePolicyTest {

    @Test
    @DisplayName("The creative mode fires without ammunition and consumes none")
    void creativeFiresForFree() {
        Rac1FirePolicy.Outcome outcome = Rac1FirePolicy.evaluate(Rac1GameMode.CREATIVE, 0);
        assertEquals(Rac1FirePolicy.Outcome.FIRE_WITHOUT_AMMO, outcome);
        assertTrue(Rac1FirePolicy.firesShot(outcome));
        assertFalse(Rac1FirePolicy.consumesArrow(outcome));
        assertFalse(Rac1FirePolicy.playsDryFireClick(outcome));
    }

    @Test
    @DisplayName("The survival mode fires with one ordinary arrow and consumes it")
    void survivalFiresWithOneArrow() {
        Rac1FirePolicy.Outcome outcome = Rac1FirePolicy.evaluate(Rac1GameMode.SURVIVAL, 3);
        assertEquals(Rac1FirePolicy.Outcome.FIRE_WITH_AMMO, outcome);
        assertTrue(Rac1FirePolicy.firesShot(outcome));
        assertTrue(Rac1FirePolicy.consumesArrow(outcome));
        assertFalse(Rac1FirePolicy.playsDryFireClick(outcome));
    }

    @Test
    @DisplayName("The adventure mode fires with one ordinary arrow and consumes it")
    void adventureFiresWithOneArrow() {
        Rac1FirePolicy.Outcome outcome = Rac1FirePolicy.evaluate(Rac1GameMode.ADVENTURE, 1);
        assertEquals(Rac1FirePolicy.Outcome.FIRE_WITH_AMMO, outcome);
        assertTrue(Rac1FirePolicy.firesShot(outcome));
        assertTrue(Rac1FirePolicy.consumesArrow(outcome));
    }

    @Test
    @DisplayName("The survival mode clicks instead of firing when there is no arrow")
    void survivalWithoutAmmoClicks() {
        Rac1FirePolicy.Outcome outcome = Rac1FirePolicy.evaluate(Rac1GameMode.SURVIVAL, 0);
        assertEquals(Rac1FirePolicy.Outcome.DRY_FIRE, outcome);
        assertFalse(Rac1FirePolicy.firesShot(outcome));
        assertFalse(Rac1FirePolicy.consumesArrow(outcome));
        assertTrue(Rac1FirePolicy.playsDryFireClick(outcome));
    }

    @Test
    @DisplayName("The adventure mode clicks instead of firing when there is no arrow")
    void adventureWithoutAmmoClicks() {
        Rac1FirePolicy.Outcome outcome = Rac1FirePolicy.evaluate(Rac1GameMode.ADVENTURE, 0);
        assertEquals(Rac1FirePolicy.Outcome.DRY_FIRE, outcome);
        assertFalse(Rac1FirePolicy.firesShot(outcome));
        assertTrue(Rac1FirePolicy.playsDryFireClick(outcome));
    }

    @Test
    @DisplayName("The spectator mode never fires and never clicks")
    void spectatorIsBlocked() {
        Rac1FirePolicy.Outcome outcome = Rac1FirePolicy.evaluate(Rac1GameMode.SPECTATOR, 64);
        assertEquals(Rac1FirePolicy.Outcome.BLOCKED, outcome);
        assertFalse(Rac1FirePolicy.firesShot(outcome));
        assertFalse(Rac1FirePolicy.consumesArrow(outcome));
        assertFalse(Rac1FirePolicy.playsDryFireClick(outcome));
    }

    @Test
    @DisplayName("The weapon fires ten shots a second")
    void cadenceIsTenShotsASecond() {
        assertEquals(2, Rac1FirePolicy.SHOT_INTERVAL_TICKS);
        assertEquals(20 / Rac1FirePolicy.SHOT_INTERVAL_TICKS, 10);
    }

    @Test
    @DisplayName("The click of the empty weapon is bound to a rate of two a second")
    void dryFireIsBoundToARate() {
        assertEquals(10, Rac1FirePolicy.DRY_FIRE_COOLDOWN_TICKS);
        assertEquals(20 / Rac1FirePolicy.DRY_FIRE_COOLDOWN_TICKS, 2);
    }

    @Test
    @DisplayName("The other hand never fires, never clicks and never uses ammunition")
    void theOtherHandIsBlocked() {
        for (Rac1GameMode mode : Rac1GameMode.values()) {
            for (int arrows : new int[] { 0, 1, 64 }) {
                Rac1FirePolicy.Outcome outcome =
                        Rac1FirePolicy.evaluate(mode, arrows, Rac1FirePolicy.Hand.OFF);
                assertEquals(Rac1FirePolicy.Outcome.BLOCKED, outcome);
                assertTrue(Rac1FirePolicy.isBlocked(outcome));
                assertFalse(Rac1FirePolicy.firesShot(outcome));
                assertFalse(Rac1FirePolicy.consumesArrow(outcome));
                assertFalse(Rac1FirePolicy.playsDryFireClick(outcome));
            }
        }
    }

    @Test
    @DisplayName("The main hand fires as the way of play and the ammunition ask")
    void theMainHandIsNotBlocked() {
        for (Rac1GameMode mode : Rac1GameMode.values()) {
            Rac1FirePolicy.Outcome outcome =
                    Rac1FirePolicy.evaluate(mode, 64, Rac1FirePolicy.Hand.MAIN);
            assertEquals(Rac1FirePolicy.evaluate(mode, 64), outcome);
        }
        // Only the spectator mode is blocked with the main hand.
        assertTrue(Rac1FirePolicy.isBlocked(
                Rac1FirePolicy.evaluate(Rac1GameMode.SPECTATOR, 64, Rac1FirePolicy.Hand.MAIN)));
        assertFalse(Rac1FirePolicy.isBlocked(
                Rac1FirePolicy.evaluate(Rac1GameMode.CREATIVE, 64, Rac1FirePolicy.Hand.MAIN)));
        assertFalse(Rac1FirePolicy.isBlocked(
                Rac1FirePolicy.evaluate(Rac1GameMode.SURVIVAL, 64, Rac1FirePolicy.Hand.MAIN)));
        assertFalse(Rac1FirePolicy.isBlocked(
                Rac1FirePolicy.evaluate(Rac1GameMode.ADVENTURE, 64, Rac1FirePolicy.Hand.MAIN)));
    }
}
