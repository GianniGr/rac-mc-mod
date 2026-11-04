package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.client.render.Rac1PreviewLayout;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.iso.Rac1IsoSelectionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The check of the conversion of the assets of a real disc image.
 *
 * <p>This test is opt-in and is skipped unless the path of a disc image is given on the command
 * line:
 *
 * <pre>
 * .\gradlew.bat test --rerun -Dracmc.isoImage=C:/path/to/the/disc.iso
 * </pre>
 *
 * <p>An optional second property names the revision the image is expected to have:
 *
 * <pre>
 * -Dracmc.isoExpectedRevision="NTSC-U v1.00"
 * </pre>
 *
 * <p>The image is only read. It is never copied, moved, changed or written to, and no part of it
 * ends up in the repository or in a report of the test run.
 */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscAssetTest {

    /** The number of the class of the scenery of the first level. */
    private static final int TIE_CLASS = 452;

    /** The number of the class of the hero of the first level. */
    private static final int MOBY_CLASS = 0;

    /** The number of the class of the Blaster of the hero. */
    private static final int BLASTER_CLASS = 168;

    /**
     * The data of the level of the disc image.
     *
     * @return the data of the level
     */
    private static Rac1LevelAssets level() throws Exception {
        String image = System.getProperty("racmc.isoImage");
        assertNotNull(image, "The path of the disc image must be given");
        Path path = Path.of(image);
        assertTrue(Files.isRegularFile(path), "The given path must be a regular file");
        Rac1DiscValidator validator = new Rac1DiscValidator();
        try (Rac1IsoSelectionResult outcome = validator.inspect(path)) {
            assertTrue(outcome.isSupported(), outcome.result().message());
            return Rac1LevelAssets.load(outcome.image(), Rac1AssetExtractor.LEVEL);
        }
    }

    @Test
    @DisplayName("The scenery of the first level is converted")
    void theSceneryIsConverted() throws Exception {
        Rac1StaticAsset asset = Rac1AssetExtractor.readStaticAsset(level(), TIE_CLASS);

        assertEquals(TIE_CLASS, asset.classNumber());
        assertEquals("tie_" + TIE_CLASS, asset.name());
        assertEquals(125, asset.mesh().vertexCount(), "The class 452 holds 125 vertices");
        assertEquals(144, asset.mesh().triangleCount(), "The class 452 holds 144 triangles");
        assertEquals(1, asset.textures().length, "The class 452 holds one texture");
        assertEquals(128, asset.textures()[0].width());
        assertEquals(128, asset.textures()[0].height());
    }

    @Test
    @DisplayName("The scenery of the first level has the size it was measured with")
    void theSceneryHasTheMeasuredSize() throws Exception {
        Rac1StaticAsset asset = Rac1AssetExtractor.readStaticAsset(level(), TIE_CLASS);

        // The class 452 of the first level is a piece of the scenery of the level of Novalis:
        // it is 4.52 units high, 1.78 units wide and 0.69 units deep, and its lowest point is
        // a little below its own origin. The box around it is the box the preview of the assets
        // measures, so the scenery is framed the way it was measured on the disc.
        float[] box = Rac1PreviewLayout.box(asset.mesh());

        assertNotNull(box, "The scenery of the first level has triangles");
        assertEquals(1.7844f, box[3] - box[0], 0.05f,
                "The scenery of the first level is not 1.78 units wide");
        assertEquals(4.5177f, box[4] - box[1], 0.05f,
                "The scenery of the first level is not 4.52 units high");
        assertEquals(0.6876f, box[5] - box[2], 0.05f,
                "The scenery of the first level is not 0.69 units deep");
        assertTrue(box[1] > -0.5f && box[1] < 0.0f,
                "The scenery of the first level does not stand below its own origin: " + box[1]);
    }

    @Test
    @DisplayName("The hero of the first level is converted")
    void theHeroIsConverted() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);

        assertEquals(MOBY_CLASS, asset.classNumber());
        assertEquals("moby_" + MOBY_CLASS, asset.name());
        assertEquals(5583, asset.mesh().vertexCount(), "The class 0 holds 5,583 vertices");
        assertEquals(6856, asset.mesh().triangleCount(), "The class 0 holds 6,856 triangles");
        assertEquals(111, asset.skeleton().jointCount(), "The class 0 holds 111 joints");
        assertEquals(88, asset.animations().length, "The class 0 holds 88 sequences");
        assertEquals(4, asset.textures().length, "The class 0 holds four textures");
    }

    @Test
    @DisplayName("The Blaster of the hero is converted")
    void theBlasterIsConverted() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readGadgetAsset(level(), BLASTER_CLASS);

        assertEquals(BLASTER_CLASS, asset.classNumber());
        assertEquals(923, asset.mesh().vertexCount(), "The Blaster holds 923 vertices");
        assertEquals(962, asset.mesh().triangleCount(), "The Blaster holds 962 triangles");
        assertEquals(18, asset.skeleton().jointCount(), "The Blaster holds 18 joints");
        assertEquals(5, asset.animations().length, "The Blaster holds five sequences");
        assertEquals(1, asset.textures().length, "The Blaster holds one texture");
        assertTrue(asset.animations()[4].keyCount() > 1,
                "The fourth sequence of the Blaster is the sequence of the shot");
    }

    @Test
    @DisplayName("Every vertex of the hero is used by a triangle of the hero")
    void everyVertexOfTheHeroIsUsed() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);

        int[] indices = asset.mesh().indices();
        assertEquals(asset.mesh().triangleCount() * 3, indices.length,
                "Every triangle has three indices");
        boolean[] used = new boolean[asset.mesh().vertexCount()];
        for (int index : indices) {
            assertTrue(index >= 0 && index < asset.mesh().vertexCount(),
                    "The index " + index + " names no vertex of the hero");
            used[index] = true;
        }
        int unused = 0;
        for (boolean vertex : used) {
            if (!vertex) {
                unused++;
            }
        }
        assertEquals(0, unused,
                "The vertices of the hero that no triangle uses are the vertices of a packet "
                        + "whose indices were not moved onto the vertices of the class");
    }

    @Test
    @DisplayName("Every vertex of the Blaster is used by a triangle of the Blaster")
    void everyVertexOfTheBlasterIsUsed() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readGadgetAsset(level(), BLASTER_CLASS);

        int[] indices = asset.mesh().indices();
        assertEquals(asset.mesh().triangleCount() * 3, indices.length);
        boolean[] used = new boolean[asset.mesh().vertexCount()];
        for (int index : indices) {
            assertTrue(index >= 0 && index < asset.mesh().vertexCount(),
                    "The index " + index + " names no vertex of the Blaster");
            used[index] = true;
        }
        int unused = 0;
        for (boolean vertex : used) {
            if (!vertex) {
                unused++;
            }
        }
        assertEquals(0, unused, "The Blaster has vertices that no triangle uses");
    }

    @Test
    @DisplayName("The triangles of the hero cover the whole model of the hero")
    void theTrianglesOfTheHeroCoverTheModel() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);

        float[] places = asset.mesh().positions();
        int[] indices = asset.mesh().indices();
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int index : indices) {
            minX = Math.min(minX, places[index * 3]);
            maxX = Math.max(maxX, places[index * 3]);
            minY = Math.min(minY, places[index * 3 + 1]);
            maxY = Math.max(maxY, places[index * 3 + 1]);
            minZ = Math.min(minZ, places[index * 3 + 2]);
            maxZ = Math.max(maxZ, places[index * 3 + 2]);
        }
        // The hero is 1.42 units tall and stands on the ground of the model. The arms of the
        // model of the disc are spread out, so the model is wider than it is deep.
        assertEquals(1.4159f, maxY - minY, 0.02f, "The triangles do not cover the hero");
        assertTrue(minY > -0.02f && maxY < 1.45f,
                "The hero is not where the model of the disc has him: " + minY + " to " + maxY);
        assertEquals(1.1037f, maxX - minX, 0.03f, "The triangles do not cover the hero");
        assertEquals(1.8121f, maxZ - minZ, 0.03f, "The triangles do not cover the hero");
    }

    @Test
    @DisplayName("The triangles of the Blaster cover the whole weapon")
    void theTrianglesOfTheBlasterCoverTheWeapon() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readGadgetAsset(level(), BLASTER_CLASS);

        float[] places = asset.mesh().positions();
        int[] indices = asset.mesh().indices();
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int index : indices) {
            minX = Math.min(minX, places[index * 3]);
            maxX = Math.max(maxX, places[index * 3]);
            minY = Math.min(minY, places[index * 3 + 1]);
            maxY = Math.max(maxY, places[index * 3 + 1]);
            minZ = Math.min(minZ, places[index * 3 + 2]);
            maxZ = Math.max(maxZ, places[index * 3 + 2]);
        }
        // The weapon is 0.71 units wide, 0.74 units long and 0.31 units high.
        assertEquals(0.71f, maxX - minX, 0.02f, "The triangles do not cover the Blaster");
        assertEquals(0.73f, maxZ - minZ, 0.02f, "The triangles do not cover the Blaster");
        assertEquals(0.31f, maxY - minY, 0.02f, "The triangles do not cover the Blaster");
    }

    @Test
    @DisplayName("The hero of the game stands upright in the axes of the game of blocks")
    void theHeroStandsUpright() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);

        // The model of the game is Z-up and the converter turns the axes around, so the height
        // of the hero runs along the second axis of the game of blocks and his feet stand on
        // the ground.
        float[] places = asset.mesh().positions();
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        float widest = 0.0f;
        float deepest = 0.0f;
        for (int vertex = 0; vertex + 2 < places.length; vertex += 3) {
            lowest = Math.min(lowest, places[vertex + 1]);
            highest = Math.max(highest, places[vertex + 1]);
            widest = Math.max(widest, Math.abs(places[vertex]));
            deepest = Math.max(deepest, Math.abs(places[vertex + 2]));
        }
        assertEquals(1.4159f, highest - lowest, 0.02f,
                "The hero is not 1.42 units high in the axes of the game of blocks");
        assertTrue(lowest > -0.02f, "The feet of the hero are below the ground: " + lowest);
        assertTrue(widest < 1.0f, "The hero is too wide: " + widest);
        assertTrue(deepest < 1.5f, "The hero is too deep: " + deepest);
    }

    @Test
    @DisplayName("The triangles of the game do not have a consistent facing")
    void theTrianglesDoNotHaveAConsistentFacing() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);

        // The triangles of the classes come out of the strips of the packets in the order of the
        // strips, and their facing is not consistent: the game draws them on both sides, and so
        // does the renderer of the mod.
        float[] places = asset.mesh().positions();
        int[] indices = asset.mesh().indices();
        int oneWay = 0;
        int theOther = 0;
        for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
            int a = indices[triangle] * 3;
            int b = indices[triangle + 1] * 3;
            int c = indices[triangle + 2] * 3;
            float area = (places[b] - places[a]) * (places[c + 1] - places[a + 1])
                    - (places[c] - places[a]) * (places[b + 1] - places[a + 1]);
            if (area > 0) {
                oneWay++;
            } else if (area < 0) {
                theOther++;
            }
        }
        assertTrue(oneWay > 100 && theOther > 100,
                "The triangles of the hero all face the same way: " + oneWay + " and " + theOther);
    }

    @Test
    @DisplayName("The skinning of the hero does not tear the model apart")
    void theSkinningDoesNotTearTheModelApart() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);
        Rac1Animation animation = asset.animations()[0];
        float[] matrices = Rac1AnimationEvaluator.evaluate(asset.skeleton(), animation, 0.0f, 1.0f);
        float[] posed = new float[asset.mesh().positions().length];
        float[] normals = new float[asset.mesh().normals().length];
        Rac1AnimationEvaluator.pose(asset.mesh(), matrices, posed, normals);

        // The triangles of the model keep their size: a skinning that tears the model apart
        // stretches them.
        float[] rest = asset.mesh().positions();
        int[] indices = asset.mesh().indices();
        double before = 0;
        double after = 0;
        int stretched = 0;
        int counted = 0;
        for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
            int a = indices[triangle];
            int b = indices[triangle + 1];
            int c = indices[triangle + 2];
            double size = edge(rest, a, b) + edge(rest, b, c) + edge(rest, c, a);
            if (size < 1.0e-6) {
                continue;
            }
            double posedSize = edge(posed, a, b) + edge(posed, b, c) + edge(posed, c, a);
            before += size;
            after += posedSize;
            counted++;
            if (posedSize > size * 2.0) {
                stretched++;
            }
        }
        assertTrue(counted > 1000, "The model of the hero has too few triangles");
        assertEquals(1.0, after / before, 0.05, "The skinning changes the size of the model");
        assertTrue(stretched < counted / 100,
                "The skinning stretches " + stretched + " of the " + counted + " triangles");
    }

    @Test
    @DisplayName("The chain of the hand of the hero is read out of the class of the hero")
    void theChainOfTheHandIsRead() throws Exception {
        int[] chain = Rac1AssetExtractor.readHandChain(level());

        assertEquals(12, chain.length, "The chain of the hand of the hero has twelve joints");
        assertEquals(0, chain[0], "The chain starts at the root of the skeleton");
        assertEquals(56, chain[chain.length - 1],
                "The chain of the hand ends at the joint 56, the joint of the OmniWrench");
    }

    @Test
    @DisplayName("The joint lists of the hero name the arm that holds the weapon")
    void theJointListsOfTheHeroNameTheArmOfTheWeapon() throws Exception {
        Rac1LevelAssets assets = level();
        MobyClassReader reader = MobyClassReader.of(
                assets.classBlob(assets.coreIndex().mobyClass(MOBY_CLASS).offset()));
        List<MobyClassReader.JointList> lists = reader.readJointLists();

        assertEquals(31, lists.size(), "The class of the hero holds thirty-one joint lists");

        // The first list is the chain of the hand: it ends at the joint 56, and the second list
        // of that entry names the joints that follow it, the fingers of the hand.
        byte[] hand = lists.get(0).chain();
        assertEquals(56, hand[hand.length - 1] & 0xFF,
                "The chain of the hand does not end at the joint 56");
        byte[] fingers = lists.get(0).target();
        assertEquals(56, fingers[0] & 0xFF, "The fingers of the hand do not start at the joint 56");
        assertEquals(70, fingers[fingers.length - 1] & 0xFF,
                "The fingers of the hand do not end at the joint 70");

        // The twelfth list is the arm of the weapon: the game evaluates it for the layer of the
        // arm of the hero. It ends at the joint 52, the shoulder of the same arm as the chain of
        // the hand.
        byte[] arm = lists.get(12).chain();
        assertEquals(52, arm[arm.length - 1] & 0xFF,
                "The chain of the arm of the weapon does not end at the joint 52");

        // The other arm of the hero holds the joints 72 to 91, and it is not part of the chain of
        // the hand: the weapon of the hero is in the arm of the chain of the hand.
        for (byte joint : hand) {
            assertTrue((joint & 0xFF) < 72,
                    "The chain of the hand holds a joint of the other arm");
        }
    }

    @Test
    @DisplayName("The hero faces the first axis and the weapon of the hero is held in his right arm")
    void theHeroFacesTheFirstAxisAndTheWeaponIsInHisRightArm() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);

        // The foot of the hero points the way he faces: the joint 94 is the heel of the foot and
        // the joint 97 is the toe of it, and the toe stands further along the first axis.
        float[] pose = Rac1AnimationEvaluator.evaluateChain(asset.skeleton(),
                asset.animations()[0], 0.0f);
        float heel = pose[94 * 16 + 12];
        float toe = pose[97 * 16 + 12];
        assertTrue(toe > heel + 0.1f,
                "The foot of the hero does not point the way he faces: " + heel + " and " + toe);

        // The arm of the weapon is on the side of the third axis that the right hand of the hero
        // is on: the hero faces the first axis and stands upright, so his right hand is on the
        // side of the third axis that the chain of the hand reaches.
        int[] chain = Rac1AssetExtractor.readHandChain(level());
        float hand = pose[chain[chain.length - 1] * 16 + 14];
        assertTrue(hand > 0.0f,
                "The hand of the hero is not on the side of the right hand: " + hand);
    }

    @Test
    @DisplayName("The weapon of the hero has a sequence for the pose it is held in and one for the shot")
    void theWeaponHasASequenceForTheHeldPoseAndOneForTheShot() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readGadgetAsset(level(), BLASTER_CLASS);

        Rac1Animation held = null;
        Rac1Animation fire = null;
        for (Rac1Animation animation : asset.animations()) {
            if ("sequence_1".equals(animation.name())) {
                held = animation;
            }
            if ("sequence_4".equals(animation.name())) {
                fire = animation;
            }
        }
        assertNotNull(held, "The Blaster has no sequence for the pose it is held in");
        assertNotNull(fire, "The Blaster has no sequence for the shot");
        assertEquals(1, held.keyCount(),
                "The sequence of the pose the Blaster is held in holds a single key");
        assertTrue(fire.keyCount() > 1, "The sequence of the shot of the Blaster holds more keys");
        assertEquals(0.5f, fire.times()[fire.times().length - 1], 0.01f,
                "The shot of the Blaster lasts half a second");
    }

    @Test
    @DisplayName("The wrist of the hero points the weapon the way he faces in the stance of the weapon")
    void theWristPointsTheWeaponTheWayTheHeroFaces() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);
        Rac1Animation stance = null;
        for (Rac1Animation animation : asset.animations()) {
            if ("ratchet_sequence_56".equals(animation.name())) {
                stance = animation;
            }
        }
        assertNotNull(stance, "The hero of the game has no stance for the weapon");

        // The third axis of the wrist of the hero is the axis of the weapon, and in the stance of
        // the weapon it points the way the hero faces, which is the first axis.
        float[] pose = Rac1AnimationEvaluator.evaluateChain(asset.skeleton(), stance, 0.0f);
        float x = pose[56 * 16 + 8];
        float y = pose[56 * 16 + 9];
        float z = pose[56 * 16 + 10];
        assertTrue(x > 0.95f, "The weapon does not point the way the hero faces: " + x + ", " + y
                + ", " + z);
        assertTrue(Math.abs(y) < 0.1f && Math.abs(z) < 0.1f,
                "The weapon does not point the way the hero faces: " + x + ", " + y + ", " + z);
    }

    @Test
    @DisplayName("The joint of the hand of the hero holds the place of the hand")
    void theJointOfTheHandHoldsThePlaceOfTheHand() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);
        int[] chain = Rac1AssetExtractor.readHandChain(level());
        int joint = chain[chain.length - 1];

        float[] pose = Rac1AnimationEvaluator.evaluateChain(asset.skeleton(), asset.animations()[0],
                0.0f);

        float place = pose[joint * 16 + 13];
        assertTrue(Math.abs(place) < 3.0f,
                "The joint of the hand is " + place + " units above the middle of the model");
    }

    @Test
    @DisplayName("The pose of the hero holds the size of the model")
    void thePoseOfTheHeroHoldsTheSizeOfTheModel() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);
        Rac1Animation animation = asset.animations()[0];

        float[] matrices = Rac1AnimationEvaluator.evaluate(asset.skeleton(), animation, 0.0f, 1.0f);
        float[] positions = new float[asset.mesh().positions().length];
        float[] normals = new float[asset.mesh().normals().length];
        Rac1AnimationEvaluator.pose(asset.mesh(), matrices, positions, normals);

        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (int index = 1; index < positions.length; index += 3) {
            lowest = Math.min(lowest, positions[index]);
            highest = Math.max(highest, positions[index]);
        }
        assertTrue(highest - lowest > 1.0f && highest - lowest < 1.6f,
                "The hero is " + (highest - lowest) + " units high when he is posed");
    }

    /**
     * The length of the edge between two vertices of a mesh.
     *
     * @param positions the places of the vertices
     * @param a the first vertex
     * @param b the second vertex
     * @return the length of the edge
     */
    private static double edge(float[] positions, int a, int b) {
        double sum = 0;
        for (int axis = 0; axis < 3; axis++) {
            double d = positions[a * 3 + axis] - positions[b * 3 + axis];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
