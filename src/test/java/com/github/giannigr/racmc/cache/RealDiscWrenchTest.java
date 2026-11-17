package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import com.github.giannigr.racmc.client.render.Rac1ImportedItemDrawing;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Read-only opt-in extraction; no image, mesh, texture or cache file is written. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscWrenchTest {
    @Test
    void standaloneGadget71ExtractsSamplesAndRoundTripsInMemory() throws Exception {
        try (var disc = new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(disc.isSupported(), disc.result().message());
            var assets = Rac1LevelAssets.load(disc.image(), Rac1AssetExtractor.LEVEL);
            var wrench = Rac1AssetExtractor.readGadgetAsset(assets, Rac1AssetExtractor.WRENCH_CLASS);
            assertEquals(71, wrench.classNumber());
            assertEquals(837, wrench.mesh().vertexCount());
            assertEquals(922, wrench.mesh().triangleCount());
            assertEquals(12, wrench.skeleton().jointCount());
            assertEquals(20, wrench.animations().length);
            assertEquals(1, wrench.textures().length);
            assertEquals(128, wrench.textures()[0].width());
            assertEquals(128, wrench.textures()[0].height());
            for (int sequence : new int[] { 1, 3, 4, 5, 6, 10 }) {
                for (float time : new float[] { 0, 0.2f, 0.5f, 1 }) {
                    var pose = Rac1ImportedItemPose.sample(wrench, sequence, time);
                    assertEquals(837 * 3, pose.positions().length);
                    for (float value : pose.positions()) {
                        assertTrue(Float.isFinite(value));
                    }
                }
            }
            var model = JsonParser.parseString(Files.readString(Path.of(
                    System.getProperty("racmc.projectDir"),
                    "src/main/resources/assets/racmc/models/item/omniwrench.json")))
                    .getAsJsonObject();
            var gson = new GsonBuilder().registerTypeAdapter(ItemTransform.class,
                    new ItemTransform.Deserializer()).create();
            var gui = gson.fromJson(model.getAsJsonObject("display").get("gui"),
                    ItemTransform.class);
            var poses = new PoseStack();
            gui.apply(false, poses);
            poses.translate(-0.5, -0.5, -0.5);
            Rac1ImportedItemDrawing.restoreSocketOrigin(poses);
            float[] held = Rac1ImportedItemPose.sample(wrench, 1, 0).positions();
            Rac1ImportedItemDrawing.fitGui(poses, held);
            float[] transformed = new float[held.length];
            for (int i = 0; i < held.length; i += 3) {
                var p = poses.last().pose().transformPosition(
                        new Vector3f(held[i], held[i + 1], held[i + 2]));
                assertTrue(Math.abs(p.x) <= 0.4001);
                assertTrue(Math.abs(p.y) <= 0.4001);
                transformed[i] = p.x;
                transformed[i + 1] = p.y;
                transformed[i + 2] = p.z;
            }
            float[] guiBox = Rac1ImportedItemPose.bounds(transformed);
            assertTrue(guiBox[3] - guiBox[0] > 0.1, "The icon must not be edge-on");
            assertTrue(guiBox[4] - guiBox[1] > 0.1, "The icon must not be edge-on");
            var fixture = SyntheticAssets.content("real-wrench-memory-only");
            var input = new Rac1CacheContent(fixture.discKey(), fixture.staticAsset(),
                    fixture.animatedAsset(), fixture.blaster(), fixture.handJoint(),
                    fixture.crates(), wrench);
            var output = Rac1CacheCodec.read(Rac1CacheCodec.write(input,
                    Rac1CacheStore.SCHEMA_VERSION), input.discKey(), Rac1CacheStore.SCHEMA_VERSION);
            assertArrayEquals(wrench.mesh().positions(), output.wrench().mesh().positions());
            assertArrayEquals(wrench.textures()[0].pixels(), output.wrench().textures()[0].pixels());
            assertArrayEquals(Rac1ImportedItemPose.sample(wrench, 10, 0.3f).positions(),
                    Rac1ImportedItemPose.sample(output.wrench(), 10, 0.3f).positions());
        }
    }
}
