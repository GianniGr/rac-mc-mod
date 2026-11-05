package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the rule that decides whether a shot of the Blaster is blocked.
 *
 * <p>The rule asks the world whether the mouth of the weapon stands inside a wall and whether
 * the way from the eyes of the player to the mouth of the weapon is free. The world of the checks
 * is a world that is built by hand, so that the answers of the rule can be read out.
 */
class Rac1BlasterObstructionTest {

    /** The place of the eyes of the player in the checks. */
    private static final double[] EYES = { 0.0, 1.62, 0.0 };

    /** The place of the mouth of the weapon in the checks. */
    private static final double[] MOUTH = { 0.0, 0.75, 0.63 };

    @Test
    @DisplayName("A shot is made when the world is free")
    void aShotIsMadeWhenTheWorldIsFree() {
        assertFalse(Rac1BlasterObstruction.blocked(freeWorld(), EYES, MOUTH),
                "A shot has to be made when the mouth of the weapon is in the open");
    }

    @Test
    @DisplayName("A shot is blocked when the mouth of the weapon stands in a wall")
    void aShotIsBlockedWhenTheMouthStandsInAWall() {
        Rac1BlasterObstruction.World world = new Rac1BlasterObstruction.World() {

            @Override
            public boolean solidAt(double x, double y, double z) {
                // The wall stands right in front of the player.
                return z > 0.5;
            }

            @Override
            public boolean pathBlocked(double fromX, double fromY, double fromZ, double toX,
                                       double toY, double toZ) {
                return false;
            }
        };
        assertTrue(Rac1BlasterObstruction.blocked(world, EYES, MOUTH),
                "A shot must not be made through a wall");
    }

    @Test
    @DisplayName("A shot is blocked when the way from the eyes to the mouth is blocked")
    void aShotIsBlockedWhenTheWayIsBlocked() {
        Rac1BlasterObstruction.World world = new Rac1BlasterObstruction.World() {

            @Override
            public boolean solidAt(double x, double y, double z) {
                return false;
            }

            @Override
            public boolean pathBlocked(double fromX, double fromY, double fromZ, double toX,
                                       double toY, double toZ) {
                return true;
            }
        };
        assertTrue(Rac1BlasterObstruction.blocked(world, EYES, MOUTH),
                "A shot must not be made out of a wall");
    }

    /**
     * A world without a wall.
     *
     * @return the world
     */
    private static Rac1BlasterObstruction.World freeWorld() {
        return new Rac1BlasterObstruction.World() {

            @Override
            public boolean solidAt(double x, double y, double z) {
                return false;
            }

            @Override
            public boolean pathBlocked(double fromX, double fromY, double fromZ, double toX,
                                       double toY, double toZ) {
                return false;
            }
        };
    }
}
