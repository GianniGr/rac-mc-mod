package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the picture of the weapon in the first person.
 *
 * <p>The picture of the weapon in the first person is the picture of the weapon in the hand of
 * the player. The game of blocks lowers the hand of a player that uses an item by three fifths of
 * a block, and the weapon of the game does not take part in it: the weapon must stand in the same
 * place while it is only held, while it is fired and while the trigger is released. The checks
 * replay the chain of the transforms of the game of blocks with the classes of the game itself and
 * look at the place of the drawn weapon on the screen.
 */
class Rac1BlasterFirstPersonTest {

    /** The size of the tolerance of the checks, in blocks. */
    private static final double TOLERANCE = 1.0e-3;

    /** The height of the eyes of a standing player above the place of his feet. */
    private static final double EYE_HEIGHT = 1.62;

    /** The angle of the sight of the game, in degrees. */
    private static final double FOV = 70.0;

    /** The width of the screen over its height. */
    private static final double ASPECT = 16.0 / 9.0;

    /** The turn of the model of the item in the first person, in degrees. */
    private static double[] rotation;

    /** The move of the model of the item in the first person, in blocks. */
    private static double[] translation;

    /** The size of the model of the item in the first person. */
    private static double[] scale;

    @BeforeAll
    static void readModel() throws IOException {
        Path resources = Path.of(System.getProperty("racmc.projectDir")).resolve("src")
                .resolve("main").resolve("resources").resolve("assets").resolve("racmc");
        String model = Files.readString(
                resources.resolve("models").resolve("item").resolve("blaster.json"),
                StandardCharsets.UTF_8);
        String block = model.replaceAll("(?s).*\"firstperson_righthand\"\\s*:\\s*\\{(.*?)\\}.*",
                "$1");
        rotation = numbers(block, "rotation");
        translation = numbers(block, "translation");
        scale = numbers(block, "scale");
        // The move of the model of the item is written in the pixels of the game of blocks.
        for (int index = 0; index < translation.length; index++) {
            translation[index] /= 16.0;
        }
    }

    @Test
    @DisplayName("The model of the item does not move the picture of the weapon")
    void theModelDoesNotMoveThePicture() {
        // The move of the model of the item is a move in the frame of the item and not a move in
        // the space of the camera, so the picture of the weapon is not moved by the model of the
        // item.
        for (int index = 0; index < translation.length; index++) {
            assertEquals(0.0, translation[index], TOLERANCE,
                    "The model of the item moves the picture of the weapon");
        }
        assertEquals(Rac1BlasterGrip.FIRST_PERSON_ROTATION[0], rotation[0], TOLERANCE,
                "The turn of the model of the item is not the turn of the contract");
        assertEquals(Rac1BlasterGrip.FIRST_PERSON_ROTATION[1], rotation[1], TOLERANCE,
                "The turn of the model of the item is not the turn of the contract");
        assertEquals(Rac1BlasterGrip.FIRST_PERSON_ROTATION[2], rotation[2], TOLERANCE,
                "The turn of the model of the item is not the turn of the contract");
        assertEquals(Rac1BlasterGrip.FIRST_PERSON_SCALE, scale[0], TOLERANCE,
                "The size of the model of the item is not the size of the contract");
    }

    @Test
    @DisplayName("The picture of the weapon is on the screen while it is only held")
    void theHeldPictureIsOnTheScreen() {
        Bounds bounds = bounds(heldChain());
        assertTrue(bounds.maxY > -1.0,
                "The picture of the weapon is not on the screen while it is only held");
        assertTrue(bounds.minX > 0.0,
                "The picture of the weapon is not in the right half of the screen");
        assertTrue(bounds.maxY < 0.0,
                "The picture of the weapon is not in the lower half of the screen");
        assertTrue(bounds.maxX < 1.0,
                "The picture of the weapon runs out of the screen on the right");
    }

    @Test
    @DisplayName("The weapon does not move while it is fired")
    void theWeaponDoesNotMoveWhileItIsFired() {
        // The game of blocks lowers the hand of a player that uses an item by three fifths of a
        // block, and it raises the hand again when the use is over. The renderer of the Blaster
        // gives the weapon its own place in the hand of the player, so that the weapon stands in
        // the same place while it is only held, while it is fired and while the trigger is
        // released.
        double[] held = mouth(heldChain());
        double[] fired = mouth(firedChain());
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(held[axis], fired[axis], TOLERANCE,
                    "The weapon of the game moves while it is fired: " + fired[axis] + " instead of "
                            + held[axis]);
        }
    }

    @Test
    @DisplayName("The crosshair of the screen stays clear")
    void theCrosshairStaysClear() {
        for (PoseStack chain : new PoseStack[] { heldChain(), firedChain() }) {
            Bounds bounds = bounds(chain);
            assertTrue(bounds.minX > 0.05 || bounds.maxY < -0.05,
                    "The picture of the weapon covers the crosshair of the screen");
        }
    }

    @Test
    @DisplayName("The drawn mouth of the weapon stands in front of the eye of the player")
    void theDrawnMouthStandsInFrontOfTheEye() {
        double[] drawn = mouth(firedChain());
        double distance = -drawn[2];
        assertTrue(distance > 0.0,
                "The mouth of the drawn weapon is drawn behind the eye of the player");
        assertTrue(distance <= Rac1BlasterGrip.FIRST_PERSON_MOUTH_RANGE,
                "The mouth of the drawn weapon is drawn too far away from the eye of the player");
    }

    /**
     * The chain of the first person while the weapon is only held.
     *
     * @return the stack of the poses of the game
     */
    private static PoseStack heldChain() {
        return Rac1BlasterChains.firstPerson(rotation, translation, scale, false);
    }

    /**
     * The chain of the first person while the weapon is fired.
     *
     * @return the stack of the poses of the game
     */
    private static PoseStack firedChain() {
        return Rac1BlasterChains.firstPerson(rotation, translation, scale, true);
    }

    /**
     * The place of the mouth of the drawn weapon in the space of the camera.
     *
     * @param chain the stack of the poses of the game
     * @return the place in the space of the camera
     */
    private static double[] mouth(PoseStack chain) {
        Vector3f drawn = Rac1BlasterChains.place(chain, Rac1BlasterChains.MOUTH);
        return new double[] { drawn.x, drawn.y, drawn.z };
    }

    /**
     * The place of the picture of the weapon on the screen.
     *
     * @param chain the stack of the poses of the game
     * @return the place of the picture on the screen
     */
    private static Bounds bounds(PoseStack chain) {
        double[] xs = { Rac1BlasterGrip.MESH_MIN_X, Rac1BlasterGrip.MESH_MAX_X };
        double[] ys = { Rac1BlasterGrip.MESH_MIN_Y, Rac1BlasterGrip.MESH_MAX_Y };
        double[] zs = { Rac1BlasterGrip.MESH_MIN_Z, Rac1BlasterGrip.MESH_MAX_Z };
        Bounds bounds = new Bounds();
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vector3f corner = Rac1BlasterChains.place(chain, new double[] { x, y, z });
                    double[] screen = Rac1BlasterChains.toScreen(
                            new double[] { corner.x, corner.y, corner.z }, FOV, ASPECT);
                    if (screen == null) {
                        continue;
                    }
                    bounds.minX = Math.min(bounds.minX, screen[0]);
                    bounds.maxX = Math.max(bounds.maxX, screen[0]);
                    bounds.minY = Math.min(bounds.minY, screen[1]);
                    bounds.maxY = Math.max(bounds.maxY, screen[1]);
                }
            }
        }
        return bounds;
    }

    /**
     * The numbers of one place of the transform of the model of the item.
     *
     * @param block the text of the transform
     * @param name the name of the place
     * @return the three numbers of the place
     */
    private static double[] numbers(String block, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(block);
        assertTrue(matcher.find(), "the place is not in the transform: " + name);
        String[] parts = matcher.group(1).split(",");
        double[] numbers = new double[parts.length];
        for (int index = 0; index < parts.length; index++) {
            numbers[index] = Double.parseDouble(parts[index].trim());
        }
        return numbers;
    }

    /**
     * The place of the picture of the weapon on the screen.
     */
    private static final class Bounds {

        /** The smallest place on the screen along the width. */
        private double minX = Double.MAX_VALUE;

        /** The greatest place on the screen along the width. */
        private double maxX = -Double.MAX_VALUE;

        /** The smallest place on the screen along the height. */
        private double minY = Double.MAX_VALUE;

        /** The greatest place on the screen along the height. */
        private double maxY = -Double.MAX_VALUE;
    }
}
