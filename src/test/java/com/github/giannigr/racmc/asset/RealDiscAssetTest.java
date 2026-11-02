package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.iso.Rac1IsoSelectionResult;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** The size of the tolerance of the checks. */
    private static final float TOLERANCE = 1.0e-3f;

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
    @DisplayName("The chain of the hand of the hero is read out of the class of the hero")
    void theChainOfTheHandIsRead() throws Exception {
        int[] chain = Rac1AssetExtractor.readHandChain(level());

        assertEquals(12, chain.length, "The chain of the hand of the hero has twelve joints");
        assertEquals(0, chain[0], "The chain starts at the root of the skeleton");
        assertEquals(56, chain[chain.length - 1],
                "The chain of the hand ends at the joint 56, the joint of the OmniWrench");
    }

    @Test
    @DisplayName("The pose of the hero holds the size of the hero")
    void thePoseOfTheHeroHoldsTheSizeOfTheHero() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), MOBY_CLASS);
        Rac1Animation animation = asset.animations()[0];

        float[] matrices = Rac1AnimationEvaluator.evaluate(asset.skeleton(), animation, 0.0f, 1.0f);
        float[] positions = new float[asset.mesh().positions().length];
        float[] normals = new float[asset.mesh().normals().length];
        Rac1AnimationEvaluator.pose(asset.mesh(), matrices, positions, normals);

        float height = 0.0f;
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (int index = 1; index < positions.length; index += 3) {
            lowest = Math.min(lowest, positions[index]);
            highest = Math.max(highest, positions[index]);
        }
        height = highest - lowest;
        assertEquals(1.4159f, height, 0.1f,
                "The hero has to keep the size of the model of the disc");
    }

    @Test
    @DisplayName("The skinning of the hero does not tear the model apart")
    void theSkinningDoesNotTearTheModelApart() throws Exception {
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
        assertTrue(highest - lowest < 2.0f,
                "The hero was torn apart by the skinning: the model is "
                        + (highest - lowest) + " units high");
        assertTrue(highest - lowest > 1.0f,
                "The hero was shrunk by the skinning: the model is "
                        + (highest - lowest) + " units high");
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
}
