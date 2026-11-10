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
 * The check of the conversion of the crates of the game on a real disc image.
 *
 * <p>This test is opt-in and is skipped unless the path of a disc image is given on the command
 * line:
 *
 * <pre>
 * .\gradlew.bat test --rerun -Dracmc.isoImage=C:/path/to/the/disc.iso
 * </pre>
 *
 * <p>The check holds the facts of the crates of the game that were measured on the disc: the
 * numbers of their classes, the size of their geometry and the number of their textures. The
 * image is only read. It is never copied, moved, changed or written to, and no part of it ends
 * up in the repository or in a report of the test run.
 */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscCrateTest {

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
    @DisplayName("The crates of the game are converted")
    void theCratesAreConverted() throws Exception {
        Rac1LevelAssets assets = level();

        Rac1CrateAsset bolt = Rac1AssetExtractor.readCrateAsset(assets, Rac1Crates.BOLT);
        Rac1CrateAsset health = Rac1AssetExtractor.readCrateAsset(assets, Rac1Crates.HEALTH);
        Rac1CrateAsset ammo = Rac1AssetExtractor.readCrateAsset(assets, Rac1Crates.AMMO);
        Rac1CrateAsset explosive = Rac1AssetExtractor.readCrateAsset(assets, Rac1Crates.EXPLOSIVE);

        assertEquals("bolt_crate", bolt.key());
        assertEquals(500, bolt.classNumber());
        assertEquals(168, bolt.mesh().vertexCount(), "The bolt crate holds 168 vertices");
        assertEquals(108, bolt.mesh().triangleCount(), "The bolt crate holds 108 triangles");
        assertEquals(2, bolt.textures().length, "The bolt crate holds two textures");

        assertEquals("health_crate", health.key());
        assertEquals(501, health.classNumber());
        assertEquals(294, health.mesh().vertexCount(), "The health crate holds 294 vertices");
        assertEquals(160, health.mesh().triangleCount(), "The health crate holds 160 triangles");
        assertEquals(2, health.textures().length, "The health crate holds two textures");

        assertEquals("ammo_crate", ammo.key());
        assertEquals(511, ammo.classNumber());
        assertEquals(168, ammo.mesh().vertexCount(), "The ammo crate holds 168 vertices");
        assertEquals(108, ammo.mesh().triangleCount(), "The ammo crate holds 108 triangles");
        assertEquals(3, ammo.textures().length, "The ammo crate holds three textures");

        assertEquals("explosive_crate", explosive.key());
        assertEquals(505, explosive.classNumber());
        assertEquals(540, explosive.mesh().vertexCount(), "The explosive crate holds 540 vertices");
        assertEquals(356, explosive.mesh().triangleCount(),
                "The explosive crate holds 356 triangles");
        assertEquals(3, explosive.textures().length, "The explosive crate holds three textures");
    }

    @Test
    @DisplayName("The crates of the game are one block big and stand on the ground")
    void theCratesAreOneBlockBig() throws Exception {
        Rac1LevelAssets assets = level();

        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            Rac1CrateAsset crate = Rac1AssetExtractor.readCrateAsset(assets, definition);
            float[] box = box(crate.mesh().positions());

            assertEquals(1.0f, box[1] - box[0], 0.01f,
                    "The crate " + definition.key() + " is not one block wide");
            assertEquals(1.0f, box[3] - box[2], 0.01f,
                    "The crate " + definition.key() + " is not one block high");
            assertEquals(1.0f, box[5] - box[4], 0.01f,
                    "The crate " + definition.key() + " is not one block deep");
            assertEquals(0.0f, box[2], 0.01f,
                    "The crate " + definition.key() + " does not stand on the ground");
        }
    }

    /**
     * The box around the places of the vertices of a mesh.
     *
     * @param positions the places of the vertices
     * @return the smallest and the largest place of every axis
     */
    private static float[] box(float[] positions) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int vertex = 0; vertex + 2 < positions.length; vertex += 3) {
            minX = Math.min(minX, positions[vertex]);
            maxX = Math.max(maxX, positions[vertex]);
            minY = Math.min(minY, positions[vertex + 1]);
            maxY = Math.max(maxY, positions[vertex + 1]);
            minZ = Math.min(minZ, positions[vertex + 2]);
            maxZ = Math.max(maxZ, positions[vertex + 2]);
        }
        return new float[] {minX, maxX, minY, maxY, minZ, maxZ};
    }
}
