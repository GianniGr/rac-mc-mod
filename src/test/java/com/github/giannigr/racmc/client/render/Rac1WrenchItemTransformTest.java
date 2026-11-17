package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class Rac1WrenchItemTransformTest {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer()).create();

    private static ItemTransform transform(String context) throws Exception {
        Path model = Path.of(System.getProperty("racmc.projectDir"),
                "src/main/resources/assets/racmc/models/item/omniwrench.json");
        var display = JsonParser.parseString(Files.readString(model)).getAsJsonObject()
                .getAsJsonObject("display");
        assertTrue(display.has(context));
        return GSON.fromJson(display.get(context), ItemTransform.class);
    }

    @Test
    void vanillaCubeCorrectionRestoresSocketForEveryContextAndHand() throws Exception {
        for (String context : new String[] { "gui", "ground", "fixed",
                "firstperson_righthand", "firstperson_lefthand",
                "thirdperson_righthand", "thirdperson_lefthand" }) {
            var poses = new PoseStack();
            transform(context).apply(context.endsWith("lefthand"), poses);
            Matrix4f expected = new Matrix4f(poses.last().pose());
            poses.translate(-0.5, -0.5, -0.5); // vanilla ItemRenderer
            Rac1ImportedItemDrawing.restoreSocketOrigin(poses);
            assertTrue(expected.equals(poses.last().pose(), 1e-5f), context);
            var origin = poses.last().pose().transformPosition(new Vector3f());
            assertEquals(0, origin.distance(expected.transformPosition(new Vector3f())), 1e-5);
        }
    }

    @Test
    void guiFitsAnOffCenterNonCubeWithoutMovingHeldOrigin() throws Exception {
        float[] points = { -0.3f, -0.1f, -0.4f, 1, 0.1f, 0.65f };
        var poses = new PoseStack();
        transform("gui").apply(false, poses);
        poses.translate(-0.5, -0.5, -0.5);
        Rac1ImportedItemDrawing.restoreSocketOrigin(poses);
        Rac1ImportedItemDrawing.fitGui(poses, points);
        var center = poses.last().pose().transformPosition(new Vector3f(0.35f, 0, 0.125f));
        assertEquals(0, center.length(), 1e-5);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    var point = poses.last().pose().transformPosition(new Vector3f(
                            points[x * 3], points[y * 3 + 1], points[z * 3 + 2]));
                    assertTrue(Math.abs(point.x) <= 0.40001);
                    assertTrue(Math.abs(point.y) <= 0.40001);
                    assertTrue(Math.abs(point.z) <= 0.40001);
                }
            }
        }
    }
}
