package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.block.Rac1VendorFit;
import com.github.giannigr.racmc.cache.Rac1CacheCodec;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.cache.Rac1CacheStore;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Read-only, opt-in real-disc check. No assets or cache files are written. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscVendorTest {
    @Test
    void importsClosedVendorAndLogoThroughSharedPipelineAndCache() throws Exception {
        try (var outcome = new Rac1DiscValidator().inspect(
                Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported(), outcome.result().message());
            Rac1LevelAssets assets = Rac1LevelAssets.load(outcome.image(), Rac1AssetExtractor.LEVEL);
            var pair = new Rac1VendorAsset(Rac1AssetExtractor.readAnimatedAsset(assets, 11),
                    Rac1AssetExtractor.readAnimatedAsset(assets, 1143));
            assertEquals(2327, pair.body().mesh().vertexCount());
            assertEquals(2128, pair.body().mesh().triangleCount());
            assertEquals(45, pair.body().skeleton().jointCount());
            assertEquals(5, pair.body().animations().length);
            assertEquals(3, pair.body().textures().length);
            assertEquals(741, pair.hologram().mesh().vertexCount());
            assertEquals(826, pair.hologram().mesh().triangleCount());
            assertEquals(2, pair.hologram().textures().length);
            var closed = Rac1VendorPose.closed(pair);
            var fit = Rac1VendorFit.of(closed.positions());
            assertEquals(1 / 2.303f, fit.scale(), .001);
            assertEquals(.5f, fit.x(), .001);
            assertEquals(.5f, fit.z(), .001);
            for (float value : closed.positions()) {
                assertTrue(Float.isFinite(value));
            }
            float[] logo = Rac1VendorPose.hologram(pair, 0).positions();
            float[] spun = Rac1VendorPose.hologram(pair, 1).positions();
            double changed = 0;
            for (int at = 0; at < spun.length; at++) {
                assertTrue(Float.isFinite(spun[at]));
                changed += Math.abs(logo[at] - spun[at]);
            }
            assertTrue(changed > 1, "The original logo geometry must actually spin");
            var fixture = SyntheticVendor.content();
            var content = new Rac1CacheContent("real-vendor-test", fixture.staticAsset(),
                    fixture.animatedAsset(), fixture.blaster(), 0, fixture.crates(), pair);
            byte[] bytes = Rac1CacheCodec.write(content, Rac1CacheStore.SCHEMA_VERSION);
            var decoded = Rac1CacheCodec.read(bytes, content.discKey(), Rac1CacheStore.SCHEMA_VERSION);
            assertArrayEquals(closed.positions(), Rac1VendorPose.closed(decoded.vendor()).positions());
        }
    }
}
