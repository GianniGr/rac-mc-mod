package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the way the renderer writes the triangles of a mesh into the buffer of the game.
 *
 * <p>The kinds of triangles of the game of blocks are drawn in quads: they take four vertices for
 * every triangle and cut them into two triangles. A triangle that is written as a list of three
 * vertices only would be cut in two, and the second triangle of the quad would join the last
 * vertex of the triangle with the first vertex of the next triangle of the mesh. The model would
 * then be covered with thin shards.
 *
 * <p>The renderer of the game of blocks holds one buffer for every kind of triangle, and a buffer
 * that was left while another kind of triangle was written to it cannot be used any more. The
 * triangles of the classes of the game follow each other in the order of the packets of the class
 * and their materials follow each other again and again, so the renderer has to group them.
 */
class Rac1MeshRendererTest {

    /** The places of the three vertices of the triangle of the checks. */
    private static final float[] POSITIONS = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f };

    /** The normals of the three vertices of the triangle of the checks. */
    private static final float[] NORMALS = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f };

    /** The texture coordinates of the three vertices of the triangle of the checks. */
    private static final float[] UVS = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f };

    /** The indices of the vertices of the triangle of the checks. */
    private static final int[] INDICES = { 0, 1, 2 };

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

    @Test
    @DisplayName("A triangle is written as a quad of four vertices")
    void aTriangleIsWrittenAsAQuadOfFourVertices() {
        Recording consumer = new Recording();
        PoseStack pose = new PoseStack();

        Rac1MeshRenderer.writeTriangle(consumer, pose, POSITIONS, NORMALS, UVS, INDICES, 0);

        assertEquals(4, consumer.vertices.size(),
                "The triangle is not written as a quad of four vertices");
    }

    @Test
    @DisplayName("The fourth vertex of the quad is the first vertex of the triangle")
    void theFourthVertexOfTheQuadIsTheFirstVertexOfTheTriangle() {
        Recording consumer = new Recording();
        PoseStack pose = new PoseStack();

        Rac1MeshRenderer.writeTriangle(consumer, pose, POSITIONS, NORMALS, UVS, INDICES, 0);

        float[] first = consumer.vertices.get(0);
        float[] fourth = consumer.vertices.get(3);
        for (int part = 0; part < first.length; part++) {
            assertEquals(first[part], fourth[part], 1.0e-6f,
                    "The fourth vertex of the quad is not the first vertex of the triangle");
        }
    }

    @Test
    @DisplayName("The three first vertices of the quad are the vertices of the triangle")
    void theThreeFirstVerticesOfTheQuadAreTheVerticesOfTheTriangle() {
        Recording consumer = new Recording();
        PoseStack pose = new PoseStack();

        Rac1MeshRenderer.writeTriangle(consumer, pose, POSITIONS, NORMALS, UVS, INDICES, 0);

        for (int corner = 0; corner < 3; corner++) {
            float[] written = consumer.vertices.get(corner);
            assertEquals(POSITIONS[corner * 3], written[0], 1.0e-6f,
                    "The place of the vertex is not the place of the triangle");
            assertEquals(POSITIONS[corner * 3 + 1], written[1], 1.0e-6f,
                    "The place of the vertex is not the place of the triangle");
            assertEquals(POSITIONS[corner * 3 + 2], written[2], 1.0e-6f,
                    "The place of the vertex is not the place of the triangle");
            assertEquals(UVS[corner * 2], written[3], 1.0e-6f,
                    "The texture coordinate of the vertex is not the one of the triangle");
            assertEquals(UVS[corner * 2 + 1], written[4], 1.0e-6f,
                    "The texture coordinate of the vertex is not the one of the triangle");
        }
    }

    /**
     * A buffer that keeps the vertices that are written into it.
     */
    private static final class Recording implements VertexConsumer {

        /** The vertices that were written, five floats each. */
        private final List<float[]> vertices = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.vertices.add(new float[] { x, y, z, 0.0f, 0.0f });
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            float[] vertex = this.vertices.get(this.vertices.size() - 1);
            vertex[3] = u;
            vertex[4] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
