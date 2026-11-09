package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * <p>The shot of the weapon leaves the mouth of the weapon in the third person, and the picture
 * of the weapon in the first person is a picture of the same weapon. The checks replay the chain
 * of the transforms of the game of blocks with the classes of the game itself, and compare the
 * mouth of the drawn weapon with the true mouth of the weapon in the space of the camera.
 */
class Rac1BlasterFirstPersonTest {

    /** The size of the tolerance of the checks, in blocks. */
    private static final double TOLERANCE = 1.0e-3;

    /** The height of the eyes of a standing player above the place of his feet. */
    private static final double EYE_HEIGHT = 1.62;

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
    @DisplayName("The drawn mouth of the weapon is the place the shot of the weapon leaves from")
    void theDrawnMouthIsTheTrueMouth() {
        // The chain of the first person is the chain of the camera of the player, so the mouth of
        // the drawn weapon is already in the space of the camera.
        double[] drawn = mouth(Rac1BlasterChains.place(firstPersonChain(),
                Rac1BlasterChains.MOUTH));
        double[] trueMouth = Rac1BlasterChains.toCamera(
                Rac1BlasterChains.place(aimChain(), Rac1BlasterChains.MOUTH), EYE_HEIGHT);
        assertEquals(trueMouth[0], drawn[0], TOLERANCE,
                "The mouth of the drawn weapon is not at the place the shot leaves from");
        assertEquals(trueMouth[1], drawn[1], TOLERANCE,
                "The mouth of the drawn weapon is not at the place the shot leaves from");
        assertEquals(trueMouth[2], drawn[2], TOLERANCE,
                "The mouth of the drawn weapon is not at the place the shot leaves from");
    }

    @Test
    @DisplayName("The move of the drawn weapon is small")
    void theMoveOfTheDrawnWeaponIsSmall() {
        for (int index = 0; index < translation.length; index++) {
            assertTrue(Math.abs(translation[index]) <= Rac1BlasterGrip.FIRST_PERSON_MOVE_LIMIT,
                    "The picture of the weapon in the first person is moved too far");
        }
    }

    @Test
    @DisplayName("The drawn mouth of the weapon stands in front of the eye of the player")
    void theDrawnMouthStandsInFrontOfTheEye() {
        double[] drawn = mouth(Rac1BlasterChains.place(firstPersonChain(),
                Rac1BlasterChains.MOUTH));
        double distance = -drawn[2];
        assertTrue(distance > 0.0,
                "The mouth of the drawn weapon is drawn behind the eye of the player");
        assertTrue(distance <= Rac1BlasterGrip.FIRST_PERSON_MOUTH_RANGE,
                "The mouth of the drawn weapon is drawn too far away from the eye of the player");
    }

    @Test
    @DisplayName("The bore of the drawn weapon points into the screen")
    void theBorePointsIntoTheScreen() {
        Vector3f bore = Rac1BlasterChains.direction(firstPersonChain(),
                Rac1BlasterChains.BORE);
        assertEquals(0.0, bore.x, TOLERANCE,
                "The bore of the drawn weapon does not point into the screen");
        assertEquals(0.0, bore.y, TOLERANCE,
                "The bore of the drawn weapon does not point into the screen");
        assertEquals(-1.0, bore.z, TOLERANCE,
                "The bore of the drawn weapon does not point into the screen");
    }

    @Test
    @DisplayName("The shot of the weapon flies along the place the player looks at")
    void theShotFliesAlongTheLook() {
        // The shot of the weapon leaves the true mouth of the weapon and flies towards the place
        // the player looks at, so that the shot of the weapon and the bore of the drawn weapon
        // point the same way.
        Vector3f bore = Rac1BlasterChains.direction(firstPersonChain(),
                Rac1BlasterChains.BORE);
        double[] flight = Rac1BlasterChains.toCamera(Rac1BlasterChains.direction(aimChain(),
                Rac1BlasterChains.BORE), 0.0);
        assertEquals(flight[0], bore.x, TOLERANCE,
                "The shot of the weapon does not fly the way the drawn weapon points");
        assertEquals(flight[1], bore.y, TOLERANCE,
                "The shot of the weapon does not fly the way the drawn weapon points");
        assertEquals(flight[2], bore.z, TOLERANCE,
                "The shot of the weapon does not fly the way the drawn weapon points");
    }

    /**
     * The place of the mouth of the drawn weapon in the space of the camera.
     *
     * @param place the place of the mouth of the drawn weapon
     * @return the place in the space of the camera
     */
    private static double[] mouth(Vector3f place) {
        return new double[] { place.x, place.y, place.z };
    }

    /**
     * The chain of the first person of the game of blocks, with the transform of the model of the
     * item of the mod.
     *
     * @return the stack of the poses of the game
     */
    private static com.mojang.blaze3d.vertex.PoseStack firstPersonChain() {
        return Rac1BlasterChains.firstPerson(rotation, translation, scale);
    }

    /**
     * The chain of the third person of the game of blocks, with the pose of the aim.
     *
     * @return the stack of the poses of the game
     */
    private static com.mojang.blaze3d.vertex.PoseStack aimChain() {
        return Rac1BlasterChains.thirdPerson(0.0, 0.0, 0.0);
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
}
