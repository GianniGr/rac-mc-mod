package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the change of the coordinate systems between the game and the game of
 * blocks.
 *
 * <p>No data of the game is used anywhere.
 */
class Rac1CoordinateTransformTest {

    @Test
    @DisplayName("The up axis of the game becomes the up axis of the game of blocks")
    void theUpAxisIsTurnedAround() {
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(1.0f);

        float[] up = transform.convert(0.0f, 0.0f, 1.0f);

        assertEquals(0.0f, up[0], 1.0e-6f, "The first axis does not move");
        assertEquals(1.0f, up[1], 1.0e-6f, "The up axis of the game is the up axis of the blocks");
        assertEquals(0.0f, up[2], 1.0e-6f, "The third axis does not move");
    }

    @Test
    @DisplayName("The change of the axes is a turn around the first axis")
    void theChangeIsATurnAroundTheFirstAxis() {
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(1.0f);

        float[] forward = transform.convert(0.0f, 1.0f, 0.0f);

        assertEquals(0.0f, forward[0], 1.0e-6f);
        assertEquals(0.0f, forward[1], 1.0e-6f);
        assertEquals(-1.0f, forward[2], 1.0e-6f,
                "The second axis of the game points into the third axis of the blocks");
    }

    @Test
    @DisplayName("The change of the axes keeps the length of a direction")
    void theChangeKeepsTheLengthOfADirection() {
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(1.0f);

        float[] direction = transform.convertDirection(3.0f, 4.0f, 12.0f);

        float length = (float) Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]
                + direction[2] * direction[2]);
        assertEquals(13.0f, length, 1.0e-5f, "The length of a direction does not change");
    }

    @Test
    @DisplayName("A direction is not scaled")
    void aDirectionIsNotScaled() {
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(4.0f);

        float[] direction = transform.convertDirection(1.0f, 2.0f, 3.0f);

        assertEquals(1.0f, direction[0], 1.0e-6f);
        assertEquals(3.0f, direction[1], 1.0e-6f);
        assertEquals(-2.0f, direction[2], 1.0e-6f);
    }

    @Test
    @DisplayName("A point is scaled with the size of a unit")
    void aPointIsScaled() {
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(0.5f);

        float[] point = transform.convert(2.0f, 4.0f, 6.0f);

        assertEquals(1.0f, point[0], 1.0e-6f);
        assertEquals(3.0f, point[1], 1.0e-6f);
        assertEquals(-2.0f, point[2], 1.0e-6f);
    }

    @Test
    @DisplayName("A size of a unit that is not a number is refused")
    void aSizeOfAUnitThatIsNotANumberIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Rac1CoordinateTransform(0.0f));
        assertThrows(IllegalArgumentException.class, () -> new Rac1CoordinateTransform(-1.0f));
        assertThrows(IllegalArgumentException.class, () -> new Rac1CoordinateTransform(
                Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Rac1CoordinateTransform(
                Float.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("The change of the axes is the change of the axes of the game")
    void theChangeIsTheChangeOfTheGame() {
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(1.0f);

        // The game of the disc is Z-up, so the third axis of the game is the up axis and the
        // second axis of the game points into the third axis of the game of blocks.
        float[] point = transform.convert(1.0f, 2.0f, 3.0f);

        assertTrue(point[1] > point[2], "The up axis of the blocks is the up axis of the game");
        assertEquals(1.0f, point[0], 1.0e-6f);
    }
}
