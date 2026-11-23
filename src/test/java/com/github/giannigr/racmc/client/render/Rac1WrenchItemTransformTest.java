package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.github.giannigr.racmc.actions.WrenchActionGeometry;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
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
    void allHandDisplaysScaleAfterCubeCorrectionAndWristAdjustmentAboutUnchangedGrip() throws Exception {
        for (var context : new ItemDisplayContext[] {ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.GUI,
                ItemDisplayContext.GROUND, ItemDisplayContext.FIXED}) {
            PoseStack poses = new PoseStack();
            poses.translate(2, 3, 4);
            boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            transform(context.getSerializedName()).apply(left, poses);
            poses.translate(-0.5, -0.5, -0.5);
            Rac1ImportedItemDrawing.restoreSocketOrigin(poses);
            boolean held = context != ItemDisplayContext.GUI && context != ItemDisplayContext.GROUND
                    && context != ItemDisplayContext.FIXED;
            Matrix4f adjustment = held ? new Matrix4f().rotateY(.41F) : null;
            Matrix4f baseline = new Matrix4f(poses.last().pose());
            if (adjustment != null) baseline.mul(adjustment);
            Rac1WrenchItemRenderer.applyItemSocket(poses, context, adjustment);
            Matrix4f actual = poses.last().pose();
            assertTrue(actual.getTranslation(new Vector3f()).equals(
                    baseline.getTranslation(new Vector3f()), 1.0e-6F));
            float scale = held ? WrenchActionGeometry.STEVE_MESH_SCALE : 1;
            assertTrue(actual.equals(baseline.scale(scale), 1.0e-6F), context.name());
        }
    }

    @Test
    void managedMeshEnlargesAllAxesUniformlyInsideCallerScope() {
        PoseStack poses = new PoseStack();
        poses.translate(3, 7, 4);
        Matrix4f socket = new Matrix4f(poses.last().pose()).rotateY(0.71F).scale(0.9375F);
        poses.mulPose(new Matrix4f().rotateY(0.71F).scale(0.9375F));
        poses.pushPose();
        Rac1WrenchItemRenderer.scaleSteveMesh(poses);
        assertTrue(poses.last().pose().equals(new Matrix4f(socket).scale(1.18F), 1.0e-6F));
        assertTrue(poses.last().pose().getTranslation(new Vector3f())
                .equals(socket.getTranslation(new Vector3f()), 1.0e-6F));
        poses.popPose();
        assertTrue(poses.last().pose().equals(socket, 1.0e-6F));
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
