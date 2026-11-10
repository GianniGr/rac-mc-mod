package com.github.giannigr.racmc.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the place of a crate of the game inside one block.
 *
 * <p>The checks hold the rules of the place: the crate keeps its proportions, it fits into one
 * block and it stands on the ground of the block.
 */
class Rac1CrateFitTest {

    @Test
    @DisplayName("A crate that is one block big is not scaled")
    void aCrateOfOneBlockIsNotScaled() {
        Rac1CrateFit.Place place = Rac1CrateFit.place(cube(-0.5f, 0.5f, 0.0f, 1.0f, -0.5f, 0.5f));

        assertEquals(1.0f, place.scale(), 1.0e-6f,
                "The crate of the game is one block big and is not scaled");
        assertEquals(0.0f, place.offsetX(), 1.0e-6f);
        assertEquals(0.0f, place.offsetY(), 1.0e-6f);
        assertEquals(0.0f, place.offsetZ(), 1.0e-6f);
    }

    @Test
    @DisplayName("A crate that is too big is scaled down and keeps its proportions")
    void aCrateThatIsTooBigIsScaledDown() {
        Rac1CrateFit.Place place = Rac1CrateFit.place(cube(-1.0f, 1.0f, 0.0f, 2.0f, -1.0f, 1.0f));

        assertEquals(0.5f, place.scale(), 1.0e-6f,
                "The crate has to be scaled by the factor that makes it fit into the block");
        assertEquals(2.0f, place.width(), 1.0e-6f, "The crate keeps its width");
        assertEquals(2.0f, place.height(), 1.0e-6f, "The crate keeps its height");
        assertEquals(2.0f, place.depth(), 1.0e-6f, "The crate keeps its depth");
    }

    @Test
    @DisplayName("A crate that is smaller than a block keeps its size")
    void aSmallCrateKeepsItsSize() {
        Rac1CrateFit.Place place = Rac1CrateFit.place(cube(-0.25f, 0.25f, 0.0f, 0.5f, -0.25f, 0.25f));

        assertEquals(1.0f, place.scale(), 1.0e-6f,
                "A crate that fits into the block is not scaled");
    }

    @Test
    @DisplayName("A crate stands on the ground of the block and is centred above it")
    void aCrateStandsOnTheGroundAndIsCentred() {
        Rac1CrateFit.Place place = Rac1CrateFit.place(cube(-0.5f, 0.5f, 0.25f, 1.25f, -0.5f, 0.5f));

        assertEquals(0.0f, place.offsetX(), 1.0e-6f,
                "The crate has to be centred above the block");
        assertEquals(-0.25f, place.offsetY(), 1.0e-6f,
                "The crate has to stand on the ground of the block");
        assertEquals(0.0f, place.offsetZ(), 1.0e-6f,
                "The crate has to be centred above the block");
    }

    @Test
    @DisplayName("A crate fits into one block after it was placed")
    void aCrateFitsIntoOneBlock() {
        Rac1CrateFit.Place place = Rac1CrateFit.place(cube(-1.0f, 1.0f, 0.0f, 2.0f, -1.0f, 1.0f));

        float width = place.width() * place.scale();
        float height = place.height() * place.scale();
        float depth = place.depth() * place.scale();

        assertTrue(width <= 1.0f + 1.0e-6f, "The crate is too wide: " + width);
        assertTrue(height <= 1.0f + 1.0e-6f, "The crate is too high: " + height);
        assertTrue(depth <= 1.0f + 1.0e-6f, "The crate is too deep: " + depth);
    }

    @Test
    @DisplayName("A crate is centred in the cube of an item")
    void aCrateIsCentredInTheCubeOfAnItem() {
        Rac1CrateFit.Place place = Rac1CrateFit.place(cube(-0.5f, 0.5f, 0.0f, 1.0f, -0.5f, 0.5f));

        // The cube of an item of the game of blocks stands in the middle of the place of the
        // item, so the crate has to stand in the middle of that cube along every axis.
        assertEquals(0.0f, place.offsetX(), 1.0e-6f);
        assertEquals(0.0f, place.offsetZ(), 1.0e-6f);
        assertEquals(-0.5f, place.itemOffsetY(), 1.0e-6f,
                "The crate has to stand in the middle of the cube of the item");
        float lowest = place.itemOffsetY();
        float highest = place.itemOffsetY() + place.height() * place.scale();
        assertEquals(0.5f, highest, 1.0e-6f,
                "The crate has to stand in the middle of the cube of the item");
        assertEquals(-0.5f, lowest, 1.0e-6f,
                "The crate has to stand in the middle of the cube of the item");
    }

    /**
     * A mesh of a box that stands on the ground.
     *
     * @param minX the smallest place along the first axis
     * @param maxX the largest place along the first axis
     * @param minY the smallest place along the second axis
     * @param maxY the largest place along the second axis
     * @param minZ the smallest place along the third axis
     * @param maxZ the largest place along the third axis
     * @return the mesh of the box
     */
    private static Rac1Mesh cube(float minX, float maxX, float minY, float maxY, float minZ,
                                 float maxZ) {
        float[] positions = {
                minX, minY, minZ,
                maxX, minY, minZ,
                maxX, maxY, minZ,
                minX, maxY, minZ,
                minX, minY, maxZ,
                maxX, minY, maxZ,
                maxX, maxY, maxZ,
                minX, maxY, maxZ };
        float[] normals = new float[positions.length];
        float[] uvs = new float[16];
        int[] indices = {
                0, 1, 2, 0, 2, 3,
                4, 6, 5, 4, 7, 6,
                0, 4, 5, 0, 5, 1,
                3, 2, 6, 3, 6, 7,
                1, 5, 6, 1, 6, 2,
                0, 3, 7, 0, 7, 4 };
        int[] materials = new int[indices.length / 3];
        return new Rac1Mesh(positions, normals, uvs, indices, materials);
    }
}
