package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.block.Rac1VendorFit;
import com.github.giannigr.racmc.cache.Rac1CacheCodec;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.cache.Rac1CacheStore;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import java.util.Locale;
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
            // Independently verified T11 retail facts: PAL has fewer high/low-LOD vertices,
            // but the same triangle count. Do not silently generalize the NTSC count to PAL.
            int logoVertices = switch (outcome.result().revision()) {
                case NTSC_U_V1_00 -> 741;
                case PAL_V2_00 -> 724;
            };
            assertEquals(logoVertices, pair.hologram().mesh().vertexCount());
            assertEquals(826, pair.hologram().mesh().triangleCount());
            assertEquals(2, pair.hologram().textures().length);
            var closed = Rac1VendorPose.closed(pair);
            var fit = Rac1VendorFit.of(closed.positions());
            assertEquals(1 / 2.303f, fit.scale(), .001);
            assertEquals(.5f, fit.x(), .001);
            assertEquals(.5f, fit.z(), .001);
            assertEquals(fit.scale() * 2, fit.inWorld().scale(), 1e-6);
            // Material 1 is the panel geometry; material 2 is the corner/enclosure geometry.
            // Antenna arms/tips are material 0, so neither measurement clips vertices by radius.
            float[] panels = boundsOfMaterials(pair.body().mesh(), closed.positions(), 1);
            float[] enclosure = boundsOfMaterials(pair.body().mesh(), closed.positions(), 1, 2);
            assertEquals(.999859f, panels[1] - panels[0], .001);
            assertEquals(.999779f, panels[5] - panels[4], .001);
            assertEquals(1.171726f, enclosure[1] - enclosure[0], .001);
            assertEquals(1.161938f, enclosure[5] - enclosure[4], .001);
            float worldScale = fit.inWorld().scale();
            float worldWidth = (enclosure[1] - enclosure[0]) * worldScale;
            float worldDepth = (enclosure[5] - enclosure[4]) * worldScale;
            assertEquals(1.017609f, worldWidth, .001);
            assertEquals(1.009109f, worldDepth, .001);
            System.out.printf(Locale.ROOT,
                    "%s vendor: baselineScale=%.6f worldScale=%.6f antennaFootprint=2.000000 "
                    + "enclosureWorld=%.6fx%.6fx%.6f panelWorld=%.6fx%.6fx%.6f%n",
                    outcome.result().revision(), fit.scale(), worldScale,
                    worldWidth, (enclosure[3] - enclosure[2]) * worldScale, worldDepth,
                    (panels[1] - panels[0]) * worldScale,
                    (panels[3] - panels[2]) * worldScale,
                    (panels[5] - panels[4]) * worldScale);
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

    private static float[] boundsOfMaterials(Rac1SkinnedMesh mesh, float[] positions,
                                              int... materials) {
        float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        int selected = 0;
        for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
            boolean include = false;
            for (int material : materials) {
                include |= mesh.materials()[triangle] == material;
            }
            if (include) {
                selected++;
                for (int corner = 0; corner < 3; corner++) {
                    int vertex = mesh.indices()[triangle * 3 + corner];
                    for (int axis = 0; axis < 3; axis++) {
                        float value = positions[vertex * 3 + axis];
                        bounds[axis * 2] = Math.min(bounds[axis * 2], value);
                        bounds[axis * 2 + 1] = Math.max(bounds[axis * 2 + 1], value);
                    }
                }
            }
        }
        assertEquals(materials.length == 1 ? 552 : 1016, selected,
                "Panel/enclosure selection must retain the verified retail surfaces");
        return bounds;
    }
}
