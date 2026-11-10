package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.ModSource;
import com.github.giannigr.racmc.asset.Rac1CrateDefinition;
import com.github.giannigr.racmc.asset.Rac1Crates;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the files of the crates of the game in the resources of the mod.
 *
 * <p>The checks read the files of the resources, so that a file that is missing or that is
 * emptied is detected.
 *
 * <p>The model of the item of a crate names the turn and the size of the crate for every place
 * the item is drawn at. The turns are the ones of a block of the game of blocks, so that the
 * crate is drawn like every other block: as a small block in the lists of the game, as a block in
 * the hand of the player and as a block on the ground.
 */
class Rac1CrateAssetsTest {

    /** The size of the tolerance of the checks. */
    private static final double TOLERANCE = 1.0e-6;

    /** The contexts the model of a crate has to name. */
    private static final String[] CONTEXTS = { "gui", "ground", "fixed",
            "thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
            "firstperson_lefthand" };

    /**
     * The file of the resources of the mod.
     *
     * @return the directory of the assets of the mod
     */
    private static Path resources() {
        return Path.of(System.getProperty("racmc.projectDir")).resolve("src").resolve("main")
                .resolve("resources").resolve("assets").resolve("racmc");
    }

    /**
     * The text of a file of the resources of the mod.
     *
     * @param name the place of the file, with the slashes of the places of the directories
     * @return the text of the file
     * @throws IOException when the file cannot be read
     */
    private static String read(String name) throws IOException {
        return Files.readString(resources().resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Every crate of the game has the files of its block and of its item")
    void everyCrateHasItsFiles() throws IOException {
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            String key = definition.key();
            assertTrue(Files.isRegularFile(resources().resolve("blockstates").resolve(key + ".json")),
                    "The crate " + key + " needs a file of the states of its block");
            assertTrue(Files.isRegularFile(resources().resolve("models").resolve("block")
                    .resolve(key + ".json")),
                    "The crate " + key + " needs a model of its block");
            assertTrue(Files.isRegularFile(resources().resolve("models").resolve("item")
                    .resolve(key + ".json")),
                    "The crate " + key + " needs a model of its item");
        }
    }

    @Test
    @DisplayName("The model of a crate is the model of an item with a renderer of its own")
    void modelHasTheParentOfACustomRenderer() throws IOException {
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            String model = read("models/item/" + definition.key() + ".json");
            Matcher matcher = Pattern.compile("\"parent\"\\s*:\\s*\"([^\"]+)\"").matcher(model);
            assertTrue(matcher.find(),
                    "The model of the crate " + definition.key() + " must name a parent");
            assertEquals("builtin/entity", matcher.group(1),
                    "The model of the crate " + definition.key()
                            + " must be drawn by a renderer of its own");
        }
    }

    @Test
    @DisplayName("The model of a crate names a transform for every place the item is drawn at")
    void modelNamesEveryContext() throws IOException {
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            String model = read("models/item/" + definition.key() + ".json");
            for (String context : CONTEXTS) {
                assertTrue(model.contains("\"" + context + "\""),
                        "The model of the crate " + definition.key() + " must name the context "
                                + context);
                Matcher matcher = Pattern.compile("\"" + context + "\"\\s*:\\s*\\{([^}]*)\\}")
                        .matcher(model);
                assertTrue(matcher.find(),
                        "The context " + context + " must name a transform");
                String transform = matcher.group(1);
                assertTrue(transform.contains("\"rotation\""),
                        "The transform of " + context + " must name a rotation");
                assertTrue(transform.contains("\"translation\""),
                        "The transform of " + context + " must name a place");
                assertTrue(transform.contains("\"scale\""),
                        "The transform of " + context + " must name a size");
            }
        }
    }

    @Test
    @DisplayName("The crate is drawn in the lists of the game like a block of the game of blocks")
    void theCrateIsDrawnLikeABlock() throws IOException {
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            String model = read("models/item/" + definition.key() + ".json");
            // The turns and the size of the lists of the game are the ones of the model of a
            // block of the game of blocks, so that the crate fills the place of the item the
            // way every other block does.
            assertEquals(30.0, turn(model, "gui", 0), TOLERANCE);
            assertEquals(225.0, turn(model, "gui", 1), TOLERANCE);
            assertEquals(0.0, turn(model, "gui", 2), TOLERANCE);
            assertEquals(0.625, size(model, "gui"), TOLERANCE);
            // The crate in the hand of the player is a small block, and not the whole cube of
            // an item.
            assertEquals(0.375, size(model, "thirdperson_righthand"), TOLERANCE);
            assertEquals(0.375, size(model, "thirdperson_lefthand"), TOLERANCE);
            assertEquals(0.4, size(model, "firstperson_righthand"), TOLERANCE);
            assertEquals(0.4, size(model, "firstperson_lefthand"), TOLERANCE);
        }
    }

    @Test
    @DisplayName("Every crate of the game has a name of its own")
    void everyCrateHasAName() throws IOException {
        String language = read("lang/en_us.json");
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            String key = "block.racmc." + definition.key();
            Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                    .matcher(language);
            assertTrue(matcher.find(), "The language file must name the crate " + key);
            String name = matcher.group(1);
            assertFalse(name.isBlank(), "The name of the crate " + key + " must not be empty");
            assertFalse(key.equals(name),
                    "The name of the crate " + key + " must not be the name of its own key");
        }
    }

    @Test
    @DisplayName("The crate is broken in as long as a plank of the game of blocks")
    void theCrateIsBrokenLikeAPlank() {
        String body = ModSource.methodOf(
                ModSource.of("com/github/giannigr/racmc/block/RacMcBlocks.java"),
                "private static BlockBehaviour.Properties properties(");

        // The hardness of the planks of the game of blocks is two, and the sound of the planks
        // is the sound of the wood. The block of a crate uses the same values, so that a crate
        // is broken with a bare hand in as long as a plank is.
        assertTrue(body.contains("strength(2.0F, 3.0F)"),
                "The block of a crate has to be as hard as a plank: " + body);
        assertTrue(body.contains("SoundType.WOOD"),
                "The block of a crate has to sound like wood: " + body);
        assertFalse(body.contains("requiresCorrectToolForDrops"),
                "The block of a crate must not ask for the right tool, so that a bare hand "
                        + "breaks it in the time of a plank: " + body);
    }

    @Test
    @DisplayName("The crate stays visible while it is broken")
    void theCrateStaysVisibleWhileItIsBroken() {
        String body = ModSource.methodOf(
                ModSource.of("com/github/giannigr/racmc/client/render/Rac1CrateRenderer.java"),
                "public void render(");

        // While a block is broken, the game of blocks hands the renderer of the block entity a
        // buffer that cannot be closed and that draws the crack of the block in front of the
        // buffer of the block. The renderer of the crate has to draw the crate into that
        // buffer as well, so that the crate stays visible and the crack is drawn over it.
        assertFalse(body.contains("instanceof MultiBufferSource.BufferSource"),
                "The renderer of the crate must not refuse a buffer that cannot be closed: "
                        + body);
        assertTrue(body.contains("Rac1CrateDrawing.drawInBlock(poseStack, buffers,"),
                "The renderer of the crate has to draw the crate into the buffers it is given: "
                        + body);
    }

    @Test
    @DisplayName("The files of the crates are written without a byte order mark")
    void theFilesHaveNoByteOrderMark() throws IOException {
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            String key = definition.key();
            for (String name : new String[] { "blockstates/" + key + ".json",
                    "models/block/" + key + ".json", "models/item/" + key + ".json" }) {
                byte[] bytes = Files.readAllBytes(resources().resolve(name));
                assertFalse(marked(bytes),
                        "The file " + name + " must not start with a byte order mark, or the "
                                + "game of blocks cannot read it");
            }
        }
    }

    /**
     * Whether the bytes start with a byte order mark.
     *
     * @param bytes the bytes of a file
     * @return {@code true} when the bytes start with a byte order mark
     */
    private static boolean marked(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    /**
     * The number of a turn of the model of a crate.
     *
     * @param model the text of the model
     * @param context the place the item is drawn at
     * @param axis the number of the axis of the turn
     * @return the turn in degrees
     */
    private static double turn(String model, String context, int axis) {
        Matcher matcher = Pattern.compile("\"" + context + "\"\\s*:\\s*\\{[^}]*\"rotation\"\\s*:"
                + "\\s*\\[\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)").matcher(model);
        assertTrue(matcher.find(), "The model must name the turn of " + context);
        return Double.parseDouble(matcher.group(axis + 1));
    }

    /**
     * The size of the model of a crate at a place the item is drawn at.
     *
     * @param model the text of the model
     * @param context the place the item is drawn at
     * @return the size
     */
    private static double size(String model, String context) {
        Matcher matcher = Pattern.compile("\"" + context + "\"\\s*:\\s*\\{[^}]*\"scale\"\\s*:"
                + "\\s*\\[\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)").matcher(model);
        assertTrue(matcher.find(), "The model must name the size of " + context);
        double first = Double.parseDouble(matcher.group(1));
        double second = Double.parseDouble(matcher.group(2));
        double third = Double.parseDouble(matcher.group(3));
        assertEquals(first, second, TOLERANCE, "The size of " + context + " has to be the same "
                + "in every direction");
        assertEquals(first, third, TOLERANCE, "The size of " + context + " has to be the same "
                + "in every direction");
        return first;
    }
}
