package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.block.Rac1VendorFit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class Rac1VendorItemTransformTest {
    private static final float[] CLOSED = {-1.1515f, -.002f, -1.1515f,
            1.1515f, 1.262f, 1.1515f};

    @Test
    void footprintIsOneBlockAndHeightKeepsOriginalProportions() {
        var fit = Rac1VendorFit.of(CLOSED);
        assertEquals(1 / 2.303f, fit.scale(), 1e-6);
        assertEquals(1, 2.303f * fit.scale(), 1e-6);
        assertEquals(1.264f / 2.303f, 1.264f * fit.scale(), 1e-6);
        assertEquals(0, fit.y() + CLOSED[1] * fit.scale(), 1e-6);
        assertEquals(.5, fit.x(), 1e-6);
        assertEquals(.5, fit.z(), 1e-6);
    }

    @Test
    void actualDrawingFitMatchesVanillaItemCubeForEveryContextAndHand() throws Exception {
        Path model = Path.of(System.getProperty("racmc.projectDir"),
                "src/main/resources/assets/racmc/models/item/gadgetron_vendor.json");
        var json = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
        assertEquals("builtin/entity", json.get("parent").getAsString());
        Gson gson = new GsonBuilder().registerTypeAdapter(ItemTransform.class,
                new ItemTransform.Deserializer()).create();
        var fit = Rac1VendorFit.of(CLOSED);
        for (String context : new String[] {"gui", "ground", "fixed", "thirdperson_righthand",
                "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand"}) {
            ItemTransform transform = gson.fromJson(
                    json.getAsJsonObject("display").get(context), ItemTransform.class);
            for (boolean left : new boolean[] {false, true}) {
                PoseStack vendor = new PoseStack(), vanilla = new PoseStack();
                transform.apply(left, vendor);
                transform.apply(left, vanilla);
                vendor.translate(-.5f, -.5f, -.5f);
                vanilla.translate(-.5f, -.5f, -.5f);
                Rac1VendorDrawing.place(vendor, fit);
                for (int at = 0; at < CLOSED.length; at += 3) {
                    Vector3f actual = vendor.last().pose().transformPosition(
                            new Vector3f(CLOSED[at], CLOSED[at + 1], CLOSED[at + 2]));
                    // An equivalent vanilla box occupies [0,1] X/Z, [0,originalHeight/width] Y.
                    Vector3f expected = vanilla.last().pose().transformPosition(new Vector3f(
                            at == 0 ? 0 : 1, at == 0 ? 0 : 1.264f / 2.303f, at == 0 ? 0 : 1));
                    assertEquals(expected.x, actual.x, 1e-5, context);
                    assertEquals(expected.y, actual.y, 1e-5, context);
                    assertEquals(expected.z, actual.z, 1e-5, context);
                }
            }
        }
    }

    @Test
    void asymmetricClosedMeshIsCenteredAndGroundedInBlockSpace() {
        var fit = Rac1VendorFit.of(new float[] {-3, -1, -2, 1, 1, 0});
        assertEquals(.25f, fit.scale());
        assertEquals(.75f, fit.x());
        assertEquals(.25f, fit.y());
        assertEquals(.75f, fit.z());
    }

    @Test
    void invalidMeshesAreRejectedRatherThanProducingInfiniteRenderTransforms() {
        assertThrows(IllegalArgumentException.class, () -> Rac1VendorFit.of(new float[0]));
        assertThrows(IllegalArgumentException.class, () -> Rac1VendorFit.of(new float[] {0}));
        assertThrows(IllegalArgumentException.class, () -> Rac1VendorFit.of(new float[] {0, 0, 0}));
        assertThrows(IllegalArgumentException.class,
                () -> Rac1VendorFit.of(new float[] {Float.NaN, 0, 0}));
    }
}
