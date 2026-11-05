package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the files of the Blaster in the resources of the mod.
 *
 * <p>The model of the weapon is the model of an item of the game with a renderer of its own, and
 * the name of the weapon is a name of its own. The checks read the files of the resources, so
 * that a file that is missing or that is emptied is detected.
 */
class Rac1BlasterAssetsTest {

    /** The size of the tolerance of the checks. */
    private static final double TOLERANCE = 1.0e-6;

    /** The contexts the model of the weapon has to name. */
    private static final String[] CONTEXTS = { "thirdperson_righthand", "firstperson_righthand",
            "gui", "ground", "head", "fixed" };

    /** The name of the weapon in the language file. */
    private static final String NAME_KEY = "item.racmc.blaster";

    private static String model;
    private static String language;

    @BeforeAll
    static void readFiles() throws IOException {
        Path resources = Path.of(System.getProperty("racmc.projectDir")).resolve("src")
                .resolve("main").resolve("resources").resolve("assets").resolve("racmc");
        model = Files.readString(resources.resolve("models").resolve("item").resolve("blaster.json"),
                StandardCharsets.UTF_8);
        language = Files.readString(resources.resolve("lang").resolve("en_us.json"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("The model of the weapon is the model of an item with a renderer of its own")
    void modelHasTheParentOfACustomRenderer() {
        Matcher matcher = Pattern.compile("\"parent\"\\s*:\\s*\"([^\"]+)\"").matcher(model);
        assertTrue(matcher.find(), "The model of the Blaster must name a parent");
        assertEquals("builtin/entity", matcher.group(1),
                "The model of the Blaster must be drawn by a renderer of its own");
    }

    @Test
    @DisplayName("The model of the weapon names a transform for every context")
    void modelNamesEveryContext() {
        for (String context : CONTEXTS) {
            assertTrue(model.contains("\"" + context + "\""),
                    "The model of the Blaster must name the context " + context);
        }
    }

    @Test
    @DisplayName("Every transform of the model of the weapon names a rotation, a place and a size")
    void everyTransformIsComplete() {
        for (String context : CONTEXTS) {
            Matcher matcher = Pattern.compile("\"" + context + "\"\\s*:\\s*\\{([^}]*)\\}").matcher(model);
            assertTrue(matcher.find(), "The context " + context + " must name a transform");
            String transform = matcher.group(1);
            assertTrue(transform.contains("\"rotation\""),
                    "The transform of " + context + " must name a rotation");
            assertTrue(transform.contains("\"translation\""),
                    "The transform of " + context + " must name a place");
            assertTrue(transform.contains("\"scale\""),
                    "The transform of " + context + " must name a size");
        }
    }

    @Test
    @DisplayName("The weapon has a name of its own")
    void weaponHasAName() {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(NAME_KEY) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(language);
        assertTrue(matcher.find(), "The language file must name the Blaster");
        String name = matcher.group(1);
        assertFalse(name.isBlank(), "The name of the Blaster must not be empty");
        assertFalse(NAME_KEY.equals(name),
                "The name of the Blaster must not be the name of its own key");
    }

    @Test
    @DisplayName("The tab of the mod is named after the mod")
    void tabIsNamedAfterTheMod() {
        Matcher matcher = Pattern.compile("\"itemGroup\\.racmc\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(language);
        assertTrue(matcher.find(), "The language file must name the tab of the mod");
        assertEquals("Ratchet & Clank: Getting Wood", matcher.group(1),
                "The tab of the mod must be named after the mod");
    }

    @Test
    @DisplayName("The turns of the model of the weapon are the turns of the contract")
    void turnsOfTheModelAreTheTurnsOfTheContract() {
        // The contract holds the turns that the weapon needs, so that the place of the weapon in
        // the hand and the place of the mouth of the weapon are the same on the server and in the
        // game. The model of the item has to use them.
        assertEquals(Rac1BlasterGrip.THIRD_PERSON_ROTATION[0], turn("thirdperson_righthand", 0),
                TOLERANCE);
        assertEquals(Rac1BlasterGrip.THIRD_PERSON_ROTATION[1], turn("thirdperson_righthand", 1),
                TOLERANCE);
        assertEquals(Rac1BlasterGrip.THIRD_PERSON_ROTATION[2], turn("thirdperson_righthand", 2),
                TOLERANCE);
        assertEquals(Rac1BlasterGrip.FIRST_PERSON_ROTATION[0], turn("firstperson_righthand", 0),
                TOLERANCE);
        assertEquals(Rac1BlasterGrip.FIRST_PERSON_ROTATION[1], turn("firstperson_righthand", 1),
                TOLERANCE);
        assertEquals(Rac1BlasterGrip.FIRST_PERSON_ROTATION[2], turn("firstperson_righthand", 2),
                TOLERANCE);
        assertEquals(Rac1BlasterGrip.GUI_ROTATION[0], turn("gui", 0), TOLERANCE);
        assertEquals(Rac1BlasterGrip.GUI_ROTATION[1], turn("gui", 1), TOLERANCE);
        assertEquals(Rac1BlasterGrip.GUI_ROTATION[2], turn("gui", 2), TOLERANCE);
    }

    @Test
    @DisplayName("The weapon of the game is held at the hand of the player")
    void weaponIsHeldAtTheHandOfThePlayer() {
        // The place of the item in the joint of the arm is the place of the game, and the turn of
        // the model of the item does not move it away from the hand.
        Rac1BlasterGrip.Point rest = Rac1BlasterGrip.originInModel(0.0);
        assertEquals(-0.375, rest.x(), TOLERANCE);
        assertEquals(0.75, rest.y(), TOLERANCE);
        assertEquals(-0.125, rest.z(), TOLERANCE);
    }

    /**
     * The number of a turn of the model of the weapon.
     *
     * @param context the context of the turn
     * @param axis the number of the axis of the turn
     * @return the turn in degrees
     */
    private static double turn(String context, int axis) {
        Matcher matcher = Pattern.compile("\"" + context + "\"\\s*:\\s*\\{[^}]*\"rotation\"\\s*:\\s*\\[\\s*"
                + "(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)").matcher(model);
        assertTrue(matcher.find(), "The model of the weapon must name the turn of " + context);
        return Double.parseDouble(matcher.group(axis + 1));
    }
}
