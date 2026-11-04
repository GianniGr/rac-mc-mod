package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the layout of the preview of the assets of the game.
 *
 * <p>The preview draws the hero with his Blaster in his hand and the piece of the scenery of the
 * game next to him. The two models are drawn in two places of their own, and every model is
 * measured so that it fills its place as far as the room allows.
 *
 * <p>The places and the boxes around the models are the same in every frame, so that neither
 * model moves on the screen while the demo runs.
 */
class Rac1PreviewLayoutTest {

    /**
     * The area the preview is drawn in, as the screen of the disc image draws it in a window of
     * 854 by 480 pixels.
     */
    private static final int AREA_X = 20;

    private static final int AREA_Y = 90;

    private static final int AREA_WIDTH = 387;

    private static final int AREA_HEIGHT = 120;

    /**
     * The box around the hero with his Blaster in his hand, as it was measured on the disc of the
     * game: he is 1.42 units high, 1.10 units wide and 1.81 units deep.
     */
    private static final float[] HERO_BOX = { -0.55f, 0.0f, -0.90f, 0.55f, 1.42f, 0.90f };

    /**
     * The box around the piece of the scenery of the game, as it was measured on the disc of the
     * game: the class 452 of the first level is 4.52 units high, 1.78 units wide and 0.69 units
     * deep, and its lowest point is 0.17 units below its own origin.
     */
    private static final float[] SCENERY_BOX = { -0.399f, -0.167f, -0.335f, 1.385f, 4.351f, 0.353f };

    /**
     * A mesh that holds two triangles of the checks.
     *
     * @param positions the places of the vertices
     * @param indices the indices of the triangles
     * @return the mesh
     */
    private static Rac1Mesh mesh(float[] positions, int[] indices) {
        float[] normals = new float[positions.length];
        for (int vertex = 0; vertex < normals.length / 3; vertex++) {
            normals[vertex * 3 + 1] = 1.0f;
        }
        float[] uvs = new float[positions.length / 3 * 2];
        int[] materials = new int[indices.length / 3];
        return new Rac1Mesh(positions, normals, uvs, indices, materials);
    }

    @Test
    @DisplayName("The hero keeps the whole area when there is no scenery")
    void theHeroKeepsTheWholeAreaWithoutScenery() {
        Rac1PreviewLayout.View view = Rac1PreviewLayout.heroView(AREA_X, AREA_Y, AREA_WIDTH,
                AREA_HEIGHT, false);

        assertEquals(AREA_X, view.x());
        assertEquals(AREA_Y, view.y());
        assertEquals(AREA_WIDTH, view.width());
        assertEquals(AREA_HEIGHT, view.height());
    }

    @Test
    @DisplayName("The scenery stands next to the hero")
    void theSceneryStandsNextToTheHero() {
        Rac1PreviewLayout.View hero = Rac1PreviewLayout.heroView(AREA_X, AREA_Y, AREA_WIDTH,
                AREA_HEIGHT, true);
        Rac1PreviewLayout.View scenery = Rac1PreviewLayout.sceneryView(AREA_X, AREA_Y, AREA_WIDTH,
                AREA_HEIGHT);

        assertEquals(AREA_X, hero.x());
        assertTrue(hero.x() + hero.width() <= scenery.x(),
                "The place of the hero and the place of the scenery overlap");
        assertEquals(AREA_X + AREA_WIDTH, scenery.x() + scenery.width(),
                "The two places do not fill the area");
        assertEquals(AREA_Y, scenery.y());
        assertEquals(AREA_HEIGHT, scenery.height());
    }

    @Test
    @DisplayName("The hero keeps his size when the scenery is drawn")
    void theHeroKeepsHisSizeWithScenery() {
        float without = Rac1PreviewLayout.scale(HERO_BOX, AREA_WIDTH, AREA_HEIGHT);
        Rac1PreviewLayout.View hero = Rac1PreviewLayout.heroView(AREA_X, AREA_Y, AREA_WIDTH,
                AREA_HEIGHT, true);
        float with = Rac1PreviewLayout.scale(HERO_BOX, hero.width(), hero.height());

        assertTrue(with <= without,
                "The hero is drawn larger than he was drawn before the scenery was added");
        assertEquals(without, with, 1.0e-5f,
                "The height of the area is what limits the size of the hero, so the scenery does "
                        + "not change the way the hero looks");
    }

    @Test
    @DisplayName("The scenery is drawn as large as its place allows")
    void theSceneryIsDrawnAsLargeAsItsPlaceAllows() {
        Rac1PreviewLayout.View view = Rac1PreviewLayout.sceneryView(AREA_X, AREA_Y, AREA_WIDTH,
                AREA_HEIGHT);
        float scale = Rac1PreviewLayout.scale(SCENERY_BOX, view.width(), view.height());

        float height = (SCENERY_BOX[4] - SCENERY_BOX[1]) * scale;
        float width = (SCENERY_BOX[3] - SCENERY_BOX[0]) * scale;
        float depth = (SCENERY_BOX[5] - SCENERY_BOX[2]) * scale;
        assertTrue(height <= view.height(),
                "The scenery is higher than its place: " + height + " and " + view.height());
        assertTrue(Math.max(width, depth) <= view.width(),
                "The scenery is wider than its place: " + width + " and " + view.width());
    }

    @Test
    @DisplayName("The box around the scenery is the box around its triangles")
    void theBoxAroundTheSceneryIsTheBoxAroundItsTriangles() {
        // A mesh of two triangles: the fourth vertex is not used by a triangle, so it does not
        // make the box of the scenery bigger than the scenery is.
        Rac1Mesh mesh = mesh(new float[] { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 9.0f, 9.0f, 9.0f }, new int[] { 0, 1, 2, 1, 3, 2 });

        float[] box = Rac1PreviewLayout.box(mesh);

        assertNotNull(box, "The mesh has triangles, so it has a box");
        assertEquals(0.0f, box[0], 1.0e-6f);
        assertEquals(0.0f, box[1], 1.0e-6f);
        assertEquals(0.0f, box[2], 1.0e-6f);
        assertEquals(1.0f, box[3], 1.0e-6f);
        assertEquals(1.0f, box[4], 1.0e-6f);
        assertEquals(1.0f, box[5], 1.0e-6f);
    }

    @Test
    @DisplayName("A mesh without triangles has no box")
    void aMeshWithoutTrianglesHasNoBox() {
        Rac1Mesh mesh = mesh(new float[] { 0.0f, 0.0f, 0.0f }, new int[0]);

        assertNull(Rac1PreviewLayout.box(mesh), "A mesh without triangles has no box");
        assertNull(Rac1PreviewLayout.box(null), "There is no box without a mesh");
    }

    @Test
    @DisplayName("The box around the scenery does not change from frame to frame")
    void theBoxAroundTheSceneryDoesNotChange() {
        Rac1Mesh mesh = mesh(new float[] { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f },
                new int[] { 0, 1, 2 });

        float[] first = Rac1PreviewLayout.box(mesh);
        float[] second = Rac1PreviewLayout.box(mesh);

        assertNotNull(first);
        assertNotNull(second);
        for (int axis = 0; axis < first.length; axis++) {
            assertEquals(first[axis], second[axis], 0.0f,
                    "The box around the scenery changed from one frame to the next");
        }
    }

    @Test
    @DisplayName("A mesh without a box has no size")
    void aMeshWithoutABoxHasNoSize() {
        assertEquals(0.0f, Rac1PreviewLayout.scale(null, AREA_WIDTH, AREA_HEIGHT), 0.0f);
    }
}
