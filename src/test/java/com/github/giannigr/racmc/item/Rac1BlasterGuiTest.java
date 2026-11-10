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
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the picture of the weapon in the lists of the game.
 *
 * <p>The picture of the weapon in the lists of the game is drawn with the transform of the model
 * of the item of the mod, and the transform is applied by the game of blocks with its own class,
 * {@link ItemTransform}. The checks read the transform out of the file of the mod and apply it with
 * the class of the game, so that the picture of the weapon is checked with the values the game
 * really uses.
 *
 * <p>The frame of the mesh of the weapon was measured out of the real asset of the game: the first
 * axis of the mesh runs up and the third axis of the mesh runs along the bore. The checks use the
 * box of the mesh, so that the picture of the weapon is measured, and not guessed.
 */
class Rac1BlasterGuiTest {

    /** The size of the tolerance of the checks. */
    private static final double TOLERANCE = 1.0e-6;

    /** The size of the place of an item in the lists of the game, in pixels. */
    private static final double SLOT_PIXELS = 16.0;

    /** The size of a block in the place of an item of the lists of the game, in pixels. */
    private static final double PIXELS_PER_BLOCK = 16.0;

    /** The size of the tolerance of the size of the picture, in pixels. */
    private static final double SIZE_TOLERANCE = 1.5;

    private static String model;

    @BeforeAll
    static void readModel() throws IOException {
        Path resources = Path.of(System.getProperty("racmc.projectDir")).resolve("src")
                .resolve("main").resolve("resources").resolve("assets").resolve("racmc");
        model = Files.readString(resources.resolve("models").resolve("item").resolve("blaster.json"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("The picture of the weapon in the lists stands upright")
    void pictureStandsUpright() {
        // The up axis of the mesh of the weapon has to run up in the lists of the game, so that the
        // picture of the weapon is the weapon seen from the side and not the weapon seen from above.
        Vector3f up = direction(guiChain(), 1.0, 0.0, 0.0);
        assertEquals(0.0, up.x, TOLERANCE,
                "The up axis of the weapon does not run up in the lists of the game");
        assertEquals(1.0, up.y, TOLERANCE,
                "The up axis of the weapon does not run up in the lists of the game");
        assertEquals(0.0, up.z, TOLERANCE,
                "The up axis of the weapon does not run up in the lists of the game");
    }

    @Test
    @DisplayName("The bore of the weapon in the lists runs to the right and towards the player")
    void boreRunsToTheRight() {
        // The bore of the weapon runs to the right of the lists of the game and a little towards
        // the player, so that the picture of the weapon is the weapon seen from the side and a
        // little from the front.
        Vector3f bore = direction(guiChain(), 0.0, 0.0, 1.0);
        assertTrue(bore.x > 0.7,
                "The bore of the weapon does not run to the right in the lists of the game");
        assertTrue(bore.z > 0.0,
                "The bore of the weapon does not run towards the player in the lists of the game");
    }

    @Test
    @DisplayName("The picture of the weapon in the lists keeps the shape of the weapon")
    void pictureKeepsTheShapeOfTheWeapon() {
        double[] scale = scale();
        assertEquals(scale[0], scale[1], TOLERANCE,
                "The size of the picture of the weapon is not the same on every axis");
        assertEquals(scale[1], scale[2], TOLERANCE,
                "The size of the picture of the weapon is not the same on every axis");
    }

    @Test
    @DisplayName("The picture of the weapon in the lists fits into its place")
    void pictureFitsIntoItsPlace() {
        double[] picture = picture();
        assertTrue(picture[2] <= SLOT_PIXELS,
                "The picture of the weapon is too wide for its place in the lists of the game");
        assertTrue(picture[3] <= SLOT_PIXELS,
                "The picture of the weapon is too tall for its place in the lists of the game");
    }

    @Test
    @DisplayName("The picture of the weapon in the lists stands in the middle of its place")
    void pictureStandsInTheMiddleOfItsPlace() {
        double[] picture = picture();
        assertEquals(0.0, picture[0], SIZE_TOLERANCE,
                "The picture of the weapon does not stand in the middle of its place");
        assertEquals(0.0, picture[1], SIZE_TOLERANCE,
                "The picture of the weapon does not stand in the middle of its place");
    }

    /**
     * The chain of the lists of the game, with the transform of the model of the item.
     *
     * @return the stack of the poses of the game
     */
    private static PoseStack guiChain() {
        PoseStack chain = new PoseStack();
        chain.setIdentity();
        double[] rotation = turn();
        double[] translation = translation();
        double[] size = scale();
        ItemTransform transform = new ItemTransform(
                new Vector3f((float) rotation[0], (float) rotation[1], (float) rotation[2]),
                new Vector3f((float) (translation[0] / 16.0), (float) (translation[1] / 16.0),
                        (float) (translation[2] / 16.0)),
                new Vector3f((float) size[0], (float) size[1], (float) size[2]));
        transform.apply(false, chain);
        return chain;
    }

    /**
     * The picture of the weapon in the lists of the game, in pixels.
     *
     * @return the middle of the picture along the first axis, the middle along the second axis, the
     *         width of the picture and the height of the picture
     */
    private static double[] picture() {
        PoseStack chain = guiChain();
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? Rac1BlasterGrip.MESH_MIN_X : Rac1BlasterGrip.MESH_MAX_X;
            double y = (corner & 2) == 0 ? Rac1BlasterGrip.MESH_MIN_Y : Rac1BlasterGrip.MESH_MAX_Y;
            double z = (corner & 4) == 0 ? Rac1BlasterGrip.MESH_MIN_Z : Rac1BlasterGrip.MESH_MAX_Z;
            Vector3f place = transform(chain, x, y, z);
            minX = Math.min(minX, place.x);
            maxX = Math.max(maxX, place.x);
            minY = Math.min(minY, place.y);
            maxY = Math.max(maxY, place.y);
        }
        double middleX = (minX + maxX) / 2.0 * PIXELS_PER_BLOCK;
        double middleY = (minY + maxY) / 2.0 * PIXELS_PER_BLOCK;
        double width = (maxX - minX) * PIXELS_PER_BLOCK;
        double height = (maxY - minY) * PIXELS_PER_BLOCK;
        return new double[] { middleX, middleY, width, height };
    }

    /**
     * The direction of an axis of the mesh of the weapon in the lists of the game.
     *
     * @param chain the stack of the poses of the game
     * @param x the direction along the first axis
     * @param y the direction along the second axis
     * @param z the direction along the third axis
     * @return the direction in the lists of the game
     */
    private static Vector3f direction(PoseStack chain, double x, double y, double z) {
        Vector4f direction = new Vector4f((float) x, (float) y, (float) z, 0.0f)
                .mul(chain.last().pose());
        return new Vector3f(direction.x, direction.y, direction.z).normalize();
    }

    /**
     * The place of a point of the mesh of the weapon in the lists of the game.
     *
     * @param chain the stack of the poses of the game
     * @param x the place along the first axis
     * @param y the place along the second axis
     * @param z the place along the third axis
     * @return the place in the lists of the game
     */
    private static Vector3f transform(PoseStack chain, double x, double y, double z) {
        Vector4f place = new Vector4f((float) x, (float) y, (float) z, 1.0f)
                .mul(chain.last().pose());
        return new Vector3f(place.x, place.y, place.z);
    }

    /**
     * The turn of the model of the weapon in the lists of the game, in degrees.
     *
     * @return the turn around the first axis, the turn around the second axis and the turn around
     *         the third axis
     */
    private static double[] turn() {
        double[] turn = numbers("rotation");
        return new double[] { turn[0], turn[1], turn[2] };
    }

    /**
     * The place of the model of the weapon in the lists of the game, in sixteenths of a block.
     *
     * @return the place along the first axis, along the second axis and along the third axis
     */
    private static double[] translation() {
        return numbers("translation");
    }

    /**
     * The size of the model of the weapon in the lists of the game.
     *
     * @return the size along the first axis, along the second axis and along the third axis
     */
    private static double[] scale() {
        return numbers("scale");
    }

    /**
     * The numbers of one part of the transform of the lists of the game.
     *
     * @param name the name of the part of the transform
     * @return the three numbers of the part
     */
    private static double[] numbers(String name) {
        Matcher matcher = Pattern.compile("\"gui\"\\s*:\\s*\\{[^}]*\"" + name + "\"\\s*:\\s*\\[\\s*"
                + "(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)").matcher(model);
        assertTrue(matcher.find(), "The model of the weapon must name the " + name + " of the lists");
        return new double[] { Double.parseDouble(matcher.group(1)),
                Double.parseDouble(matcher.group(2)), Double.parseDouble(matcher.group(3)) };
    }
}
