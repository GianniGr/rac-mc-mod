package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the grouping of the triangles of the renderer.
 *
 * <p>The renderer of the game of blocks holds one buffer for every kind of triangle, and a buffer
 * that was left while another kind of triangle was written to it cannot be used any more. The
 * triangles of the classes of the game follow each other in the order of the packets of the class
 * and their materials follow each other again and again, so the renderer has to group them.
 */
class Rac1MeshRendererTest {

    /**
     * The materials of the triangles of a mesh of a character of the game: the materials of the
     * packets of the class follow each other again and again.
     *
     * @return the materials of the triangles
     */
    private static int[] materials() {
        return new int[] { 3, 0, 1, 0, 1, 0, 1, 0, 2, 0, 2, 0, 2, 0, 2, 1, 3 };
    }

    @Test
    @DisplayName("The triangles of a mesh are grouped by their material")
    void theTrianglesAreGroupedByTheirMaterial() {
        int[] triangles = materials();
        int[] indices = new int[triangles.length * 3];
        for (int triangle = 0; triangle < triangles.length; triangle++) {
            indices[triangle * 3] = triangle;
            indices[triangle * 3 + 1] = triangle;
            indices[triangle * 3 + 2] = triangle;
        }

        List<Rac1MeshRenderer.MaterialRun> runs =
                Rac1MeshRenderer.materialRuns(indices, triangles, 4);

        assertEquals(4, runs.size(), "The mesh has four materials");
        int covered = 0;
        for (Rac1MeshRenderer.MaterialRun run : runs) {
            covered += run.triangles().length;
        }
        assertEquals(triangles.length, covered, "Every triangle belongs to a run");
        for (int index = 1; index < runs.size(); index++) {
            assertTrue(runs.get(index - 1).slot() != runs.get(index).slot(),
                    "Every material has a run of its own");
        }
    }

    @Test
    @DisplayName("The triangles of a run keep their order")
    void theTrianglesOfARunKeepTheirOrder() {
        int[] triangles = materials();
        int[] indices = new int[triangles.length * 3];

        List<Rac1MeshRenderer.MaterialRun> runs =
                Rac1MeshRenderer.materialRuns(indices, triangles, 4);

        for (Rac1MeshRenderer.MaterialRun run : runs) {
            for (int triangle : run.triangles()) {
                assertEquals(run.slot(), triangles[triangle],
                        "The run of the material does not hold the triangles of the material");
            }
        }
    }

    @Test
    @DisplayName("A material that is out of range does not read outside of the textures")
    void aMaterialThatIsOutOfRangeIsHeldApart() {
        int[] triangles = { -1, 0, 7, 0 };
        int[] indices = new int[triangles.length * 3];

        List<Rac1MeshRenderer.MaterialRun> runs =
                Rac1MeshRenderer.materialRuns(indices, triangles, 2);

        assertEquals(2, runs.size(), "The materials that are out of range have a run of their own");
        int missing = 0;
        for (Rac1MeshRenderer.MaterialRun run : runs) {
            if (run.slot() < 0) {
                missing += run.triangles().length;
            }
        }
        assertEquals(2, missing, "The two triangles without a texture are in the run of the "
                + "missing texture");
    }

    @Test
    @DisplayName("A mesh without triangles has no run")
    void aMeshWithoutTrianglesHasNoRun() {
        List<Rac1MeshRenderer.MaterialRun> runs =
                Rac1MeshRenderer.materialRuns(new int[0], new int[0], 4);

        assertTrue(runs.isEmpty(), "A mesh without triangles has no run");
    }

    @Test
    @DisplayName("A material that is missing does not make the renderer read outside of the array")
    void aMissingMaterialDoesNotReadOutsideOfTheArray() {
        int[] triangles = { 5 };
        int[] indices = new int[3];

        List<Rac1MeshRenderer.MaterialRun> runs =
                Rac1MeshRenderer.materialRuns(indices, triangles, 2);

        assertEquals(1, runs.size());
        assertEquals(-1, runs.get(0).slot(), "The triangle has no texture");
    }
}
