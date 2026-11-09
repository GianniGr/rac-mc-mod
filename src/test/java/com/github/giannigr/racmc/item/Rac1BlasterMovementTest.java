package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the speed of a player that holds the trigger of the Blaster.
 *
 * <p>The game of blocks keeps a fifth of the input of the keyboard of a player that uses an
 * item. The Blaster is not a bow, so the share that the game of blocks takes away is put back.
 * The rules hold no Minecraft types, so they are checked on a plain virtual machine.
 */
class Rac1BlasterMovementTest {

    /** The size of the tolerance of the checks. */
    private static final float TOLERANCE = 1.0e-6f;

    @Test
    @DisplayName("The game of blocks keeps a fifth of the input of a player that uses an item")
    void theGameOfBlocksKeepsAFifthOfTheInput() {
        assertEquals(0.2f, Rac1BlasterMovement.VANILLA_USING_ITEM_SCALE, TOLERANCE);
        assertEquals(1.0f,
                Rac1BlasterMovement.compensate(1.0f) * Rac1BlasterMovement.VANILLA_USING_ITEM_SCALE,
                TOLERANCE);
    }

    @Test
    @DisplayName("The whole input of the keyboard is left after the share is put back")
    void theWholeInputIsLeft() {
        for (float impulse : new float[] { 1.0f, 0.5f, -1.0f, -0.25f }) {
            float slowed = impulse * Rac1BlasterMovement.VANILLA_USING_ITEM_SCALE;
            assertEquals(impulse, Rac1BlasterMovement.compensate(slowed), TOLERANCE);
        }
    }

    @Test
    @DisplayName("A player that does not move stays still")
    void aPlayerThatDoesNotMoveStaysStill() {
        assertEquals(0.0f, Rac1BlasterMovement.compensate(0.0f), TOLERANCE);
    }

    @Test
    @DisplayName("Only the Blaster is treated this way: the factor is the inverse of the share")
    void theFactorIsTheInverseOfTheShare() {
        assertEquals(1.0f / Rac1BlasterMovement.VANILLA_USING_ITEM_SCALE,
                Rac1BlasterMovement.COMPENSATION, TOLERANCE);
        assertEquals(5.0f, Rac1BlasterMovement.COMPENSATION, TOLERANCE);
    }
}
