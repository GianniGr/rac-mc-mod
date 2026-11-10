package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the bookkeeping of a burst of shots.
 *
 * <p>The bookkeeping holds no Minecraft types, so it is checked on a plain virtual machine.
 */
class Rac1BurstStateTest {

    @Test
    @DisplayName("The first shot of a burst is due at once")
    void firstShotIsDueAtOnce() {
        Rac1BurstState state = new Rac1BurstState();
        assertTrue(state.shotDue(0L));
        assertTrue(state.shotDue(1000L));
    }

    @Test
    @DisplayName("The shots of a burst follow every second tick")
    void shotsFollowEverySecondTick() {
        Rac1BurstState state = new Rac1BurstState();
        state.recordShot(100L);
        assertFalse(state.shotDue(100L));
        assertFalse(state.shotDue(101L));
        assertTrue(state.shotDue(102L));
        state.recordShot(102L);
        assertFalse(state.shotDue(103L));
        assertTrue(state.shotDue(104L));
    }

    @Test
    @DisplayName("A burst of a second holds ten shots")
    void burstOfASecondHoldsTenShots() {
        Rac1BurstState state = new Rac1BurstState();
        int shots = 0;
        for (long tick = 0L; tick < 20L; tick++) {
            if (state.shotDue(tick)) {
                state.recordShot(tick);
                shots++;
            }
        }
        assertEquals(10, shots);
    }

    @Test
    @DisplayName("The click of the empty weapon is played once for a pull of the trigger")
    void clickIsPlayedOnceForAPull() {
        Rac1BurstState state = new Rac1BurstState();
        state.beginPull();
        assertTrue(state.dryClickAllowed(0L));
        state.recordDryClick(0L);
        assertFalse(state.dryClickAllowed(1L));
        assertFalse(state.dryClickAllowed(5L));
        assertFalse(state.dryClickAllowed(100L));
    }

    @Test
    @DisplayName("The click of the empty weapon is played again for the next pull of the trigger")
    void clickIsPlayedAgainForTheNextPull() {
        Rac1BurstState state = new Rac1BurstState();
        state.beginPull();
        state.recordDryClick(0L);
        state.beginPull();
        assertTrue(state.dryClickAllowed(20L));
    }

    @Test
    @DisplayName("The click of the empty weapon keeps the rate of two a second")
    void clickKeepsItsRate() {
        Rac1BurstState state = new Rac1BurstState();
        state.beginPull();
        state.recordDryClick(0L);
        state.beginPull();
        assertFalse(state.dryClickAllowed(9L),
                "The click must not be played again before the rate allows it");
        assertTrue(state.dryClickAllowed(10L));
    }

    @Test
    @DisplayName("A held trigger cannot spam the click of the empty weapon")
    void heldTriggerCannotSpamTheClick() {
        Rac1BurstState state = new Rac1BurstState();
        state.beginPull();
        int clicks = 0;
        for (long tick = 0L; tick < 200L; tick++) {
            if (state.dryClickAllowed(tick)) {
                state.recordDryClick(tick);
                clicks++;
            }
        }
        assertEquals(1, clicks, "A held trigger must click the empty weapon once only");
    }

    @Test
    @DisplayName("The click of the empty weapon keeps its rate over many pulls of the trigger")
    void clickKeepsItsRateOverManyPulls() {
        Rac1BurstState state = new Rac1BurstState();
        int clicks = 0;
        for (long tick = 0L; tick < 200L; tick++) {
            state.beginPull();
            if (state.dryClickAllowed(tick)) {
                state.recordDryClick(tick);
                clicks++;
            }
        }
        assertEquals(20, clicks, "The click of the empty weapon must not be played more than "
                + "twice a second");
    }

    @Test
    @DisplayName("The burst is forgotten when the trigger is released")
    void burstIsForgottenOnRelease() {
        Rac1BurstState state = new Rac1BurstState();
        state.recordShot(100L);
        state.recordDryClick(100L);
        state.reset();
        assertTrue(state.shotDue(101L));
        assertEquals(Long.MIN_VALUE, state.lastShotTick());
        assertEquals(Long.MIN_VALUE, state.lastDryClickTick());
    }

    @Test
    @DisplayName("The burst survives the leaving of the player")
    void burstSurvivesTheLeavingOfThePlayer() {
        Rac1BurstState state = new Rac1BurstState();
        state.recordShot(100L);
        state.recordDryClick(90L);
        Rac1BurstState restored = new Rac1BurstState();
        restored.restore(state.lastShotTick(), state.lastDryClickTick());
        assertFalse(restored.shotDue(101L));
        assertTrue(restored.shotDue(102L));
        assertFalse(restored.dryClickAllowed(95L));
        assertTrue(restored.dryClickAllowed(100L));
    }
}
