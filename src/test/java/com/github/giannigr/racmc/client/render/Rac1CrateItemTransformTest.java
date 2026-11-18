package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.block.Rac1CrateFit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the place of a crate of the game as an item of the game of blocks.
 *
 * <p>The checks replay the chain of the game of blocks itself: the turn of the model of the
 * item, the move of the cube of the item by half a block in every direction and the place of the
 * crate inside that cube. The crate has to end up in the same place as a block of the game of
 * blocks, so that it is held in the hand of the player and shown in the lists of the game the
 * way every other block is.
 *
 * <p>The turn of the model of the item is read with the reader of the game of blocks itself, so
 * that the check uses the same numbers the game uses.
 */
class Rac1CrateItemTransformTest {

    /** The size of the tolerance of the checks. */
    private static final double TOLERANCE = 1.0e-4;

    /** The contexts the model of a crate names. */
    private static final String[] CONTEXTS = { "gui", "ground", "fixed",
            "thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
            "firstperson_lefthand" };

    /**
     * The reader of the turns of the game of blocks.
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer()).create();

    /**
     * The file of the resources of the mod.
     *
     * @return the directory of the assets of the mod
     */
    private static Path resources() {
        return Path.of(System.getProperty("racmc.projectDir")).resolve("src").resolve("main")
                .resolve("resources").resolve("assets").resolve("racmc");
    }

    @Test
    @DisplayName("The crate is drawn where a block of the game of blocks is drawn")
    void theCrateIsDrawnWhereABlockIsDrawn() throws IOException {
        Rac1CrateFit.Place place = Rac1CrateFit.place(crate());
        ItemTransform transform = ItemTransform.NO_TRANSFORM;

        for (String context : CONTEXTS) {
            transform = transformOf(context);
            for (boolean leftHand : new boolean[] { false, true }) {
                float[] block = box(ofBlock(transform, leftHand));
                float[] crate = box(ofCrate(transform, leftHand, place));

                assertEquals(block[0], crate[0], TOLERANCE,
                        "The crate is not at the place of the block in the context " + context);
                assertEquals(block[1], crate[1], TOLERANCE,
                        "The crate is not at the place of the block in the context " + context);
                assertEquals(block[2], crate[2], TOLERANCE,
                        "The crate is not at the place of the block in the context " + context);
                assertEquals(block[3], crate[3], TOLERANCE,
                        "The crate is not at the place of the block in the context " + context);
                assertEquals(block[4], crate[4], TOLERANCE,
                        "The crate is not at the place of the block in the context " + context);
                assertEquals(block[5], crate[5], TOLERANCE,
                        "The crate is not at the place of the block in the context " + context);
            }
        }
    }

    @Test
    @DisplayName("The crate stands in the middle of the cube of the item")
    void theCrateStandsInTheMiddleOfTheCube() {
        Rac1CrateFit.Place place = Rac1CrateFit.place(crate());

        // The crate of the game has its origin in the middle of its own width and at its feet.
        // The cube of an item of the game of blocks holds the block that is drawn in it, and the
        // block has its origin in the corner of the cube, so the crate is moved by half a block
        // along the first and the third axis and by nothing along the second axis.
        assertEquals(0.5f, place.itemOffsetX(), TOLERANCE);
        assertEquals(0.0f, place.itemOffsetY(), TOLERANCE);
        assertEquals(0.5f, place.itemOffsetZ(), TOLERANCE);
    }

    @Test
    @DisplayName("The crate fills the cube of the item")
    void theCrateFillsTheCubeOfTheItem() {
        Rac1CrateFit.Place place = Rac1CrateFit.place(crate());

        float lowest = place.itemOffsetY();
        float highest = place.itemOffsetY() + place.height() * place.scale();
        float left = place.itemOffsetX() - place.width() * place.scale() / 2.0f;
        float right = place.itemOffsetX() + place.width() * place.scale() / 2.0f;

        assertEquals(0.0f, lowest, TOLERANCE, "The crate has to stand on the ground of the cube");
        assertEquals(1.0f, highest, TOLERANCE, "The crate has to fill the cube of the item");
        assertEquals(0.0f, left, TOLERANCE, "The crate has to fill the cube of the item");
        assertEquals(1.0f, right, TOLERANCE, "The crate has to fill the cube of the item");
    }

    /**
     * The turn of the model of a crate at a place the item is drawn at.
     *
     * @param context the place the item is drawn at
     * @return the turn of the game of blocks
     * @throws IOException when the model of the crate cannot be read
     */
    private static ItemTransform transformOf(String context) throws IOException {
        Path model = resources().resolve("models").resolve("item").resolve("bolt_crate.json");
        JsonObject json = JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject display = json.getAsJsonObject("display");
        return GSON.fromJson(display.getAsJsonObject(context), ItemTransform.class);
    }

    /**
     * The corners of a block of the game of blocks, after the chain of the game of blocks.
     *
     * @param transform the turn of the model of the item
     * @param leftHand whether the item is held in the left hand
     * @return the corners of the block
     */
    private static Vector3f[] ofBlock(ItemTransform transform, boolean leftHand) {
        PoseStack pose = new PoseStack();
        transform.apply(leftHand, pose);
        // The game of blocks moves the cube of the item by half a block in every direction
        // before it hands the item to the renderer.
        pose.translate(-0.5f, -0.5f, -0.5f);
        return new Vector3f[] {
                place(pose, 0.0f, 0.0f, 0.0f),
                place(pose, 1.0f, 0.0f, 0.0f),
                place(pose, 0.0f, 1.0f, 0.0f),
                place(pose, 0.0f, 0.0f, 1.0f) };
    }

    /**
     * The corners of a crate of the game, after the chain of the game of blocks.
     *
     * @param transform the turn of the model of the item
     * @param leftHand whether the item is held in the left hand
     * @param place the place of the crate in the cube of the item
     * @return the corners of the crate
     */
    private static Vector3f[] ofCrate(ItemTransform transform, boolean leftHand,
                                      Rac1CrateFit.Place place) {
        PoseStack pose = new PoseStack();
        transform.apply(leftHand, pose);
        pose.translate(-0.5f, -0.5f, -0.5f);
        pose.scale(place.scale(), place.scale(), place.scale());
        pose.translate(place.itemOffsetX(), place.itemOffsetY(), place.itemOffsetZ());
        return new Vector3f[] {
                place(pose, -0.5f, 0.0f, -0.5f),
                place(pose, 0.5f, 0.0f, -0.5f),
                place(pose, -0.5f, 1.0f, -0.5f),
                place(pose, -0.5f, 0.0f, 0.5f) };
    }

    /**
     * A place of the game of blocks, after the chain of the game of blocks.
     *
     * @param pose the chain of the game of blocks
     * @param x the place along the first axis
     * @param y the place along the second axis
     * @param z the place along the third axis
     * @return the place in the space of the game
     */
    private static Vector3f place(PoseStack pose, float x, float y, float z) {
        Vector3f point = new Vector3f(x, y, z);
        pose.last().pose().transformPosition(point);
        return point;
    }

    /**
     * The box around the corners of an object.
     *
     * @param corners the corners of the object, in the space of the game
     * @return the smallest and the largest place of every axis
     */
    private static float[] box(Vector3f[] corners) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (Vector3f corner : corners) {
            minX = Math.min(minX, corner.x);
            maxX = Math.max(maxX, corner.x);
            minY = Math.min(minY, corner.y);
            maxY = Math.max(maxY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new float[] {minX, maxX, minY, maxY, minZ, maxZ};
    }

    /**
     * The geometry of a crate of the game: one unit high, one unit wide and deep, with its
     * origin in the middle of its width and at its feet.
     *
     * @return the geometry of the crate
     */
    private static Rac1Mesh crate() {
        float[] positions = {
                -0.5f, 0.0f, -0.5f,
                0.5f, 0.0f, -0.5f,
                0.5f, 1.0f, -0.5f,
                -0.5f, 1.0f, -0.5f,
                -0.5f, 0.0f, 0.5f,
                0.5f, 0.0f, 0.5f,
                0.5f, 1.0f, 0.5f,
                -0.5f, 1.0f, 0.5f };
        float[] normals = new float[positions.length];
        float[] uvs = new float[16];
        int[] indices = { 0, 1, 2, 0, 2, 3, 4, 6, 5, 4, 7, 6 };
        int[] materials = new int[indices.length / 3];
        return new Rac1Mesh(positions, normals, uvs, indices, materials);
    }
}
