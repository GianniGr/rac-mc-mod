package com.github.giannigr.racmc.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.ModSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the speed of a player that holds the trigger of the Blaster.
 *
 * <p>The game of blocks keeps a fifth of the input of the keyboard of a player that uses an
 * item. Only the Blaster is treated this way: every other item keeps the share of the game of
 * blocks, and no other movement of the player is changed.
 */
class Rac1BlasterSpeedTest {

    @Test
    @DisplayName("Only a player that fires the Blaster with the main hand keeps the speed")
    void onlyTheBlasterKeepsTheSpeed() {
        String body = ModSource.methodOf(
                ModSource.of("com/github/giannigr/racmc/client/RacMcClient.java"),
                "public static void onMovementInputUpdate(");
        assertTrue(body.contains("Rac1BlasterClient.isFiring"),
                "the input of the player is changed without asking whether the weapon fires");
        assertTrue(body.indexOf("Rac1BlasterClient.isFiring") < body.indexOf("compensate"),
                "the input of the player is changed before the weapon is asked for");
        assertTrue(body.contains("Rac1BlasterMovement.compensate"),
                "the share of the game of blocks is not put back");
    }

    @Test
    @DisplayName("The input of the player is not changed anywhere else")
    void theInputIsNotChangedAnywhereElse() {
        String source = ModSource.of("com/github/giannigr/racmc/client/RacMcClient.java");
        String body = ModSource.methodOf(source, "public static void onMovementInputUpdate(");
        String rest = source.replace(body, "");
        assertFalse(rest.contains("leftImpulse"), "the input of the player is changed somewhere else");
        assertFalse(rest.contains("forwardImpulse"),
                "the input of the player is changed somewhere else");
    }
}
