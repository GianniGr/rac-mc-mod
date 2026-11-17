package com.github.giannigr.racmc.asset;

import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in read-only original mesh/texture import test; no asset files written. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscBoltTest {
    @Test void fourOriginalDenominationsConvertWithoutCrateFitting() throws Exception {
        try (var outcome = new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported(), outcome.result().message());
            var assets = Rac1LevelAssets.load(outcome.image(), Rac1AssetExtractor.LEVEL);
            for (var definition : Rac1Bolts.definitions()) {
                var bolt = Rac1AssetExtractor.readCrateAsset(assets, definition);
                assertEquals(definition.classNumber(), bolt.classNumber());
                assertTrue(bolt.mesh().vertexCount() > 0);
                assertTrue(bolt.mesh().triangleCount() > 0);
                assertTrue(bolt.textures().length > 0);
                for (float position : bolt.mesh().positions()) assertTrue(Float.isFinite(position));
                for (var texture : bolt.textures()) assertTrue(texture.width() > 0 && texture.height() > 0);
            }
        }
    }
}
