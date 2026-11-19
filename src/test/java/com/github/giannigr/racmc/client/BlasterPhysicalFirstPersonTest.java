package com.github.giannigr.racmc.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlasterPhysicalFirstPersonTest {
    @Test void actualJsonBothPhysicalHandsKeepSourceUpForwardGripAndExactVanillaMirrorChain() throws Exception {
        var file = Path.of(System.getProperty("racmc.projectDir"),
                "src/main/resources/assets/racmc/models/item/blaster.json");
        var display = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("display");
        assertTrue(display.has("firstperson_lefthand"), "Physical left needs explicit fitting, not right fallback");
        var gson = new GsonBuilder().registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer()).create();
        Matrix4f[] matrices = new Matrix4f[2];
        for (boolean left : new boolean[]{false, true}) {
            var json = display.getAsJsonObject(left ? "firstperson_lefthand" : "firstperson_righthand");
            var transform = gson.fromJson(json, ItemTransform.class);
            var stack = new PoseStack();
            stack.translate(left ? -.56f : .56f, -.52f, -.72f); // vanilla hand fitting, equip/swing zero
            transform.apply(left, stack); // actual MC mirror, including translation sign and Y/Z signs
            stack.translate(-.5f, -.5f, -.5f);
            stack.translate(.5f, .5f, .5f); // imported renderer's exact item-centering undo
            var actual = new Matrix4f(stack.last().pose());
            matrices[left ? 1 : 0] = actual;
            // Literal independent source-axis oracle: +X up, +Z barrel forward, grip at origin.
            // Both physical hands must send +X to camera +Y and +Z towards camera -Z.
            var up = actual.transformDirection(new Vector3f(1, 0, 0));
            var forward = actual.transformDirection(new Vector3f(0, 0, 1));
            var grip = actual.transformPosition(new Vector3f());
            assertEquals(0, up.x, 1e-6); assertEquals(.55, up.y, 1e-6); assertEquals(0, up.z, 1e-6);
            assertEquals(0, forward.x, 1e-6); assertEquals(0, forward.y, 1e-6); assertEquals(-.55, forward.z, 1e-6);
            assertEquals(left ? -.56 : .56, grip.x, 1e-6);
            assertEquals(-.52, grip.y, 1e-6); assertEquals(-.72, grip.z, 1e-6);
            var expected = new Matrix4d().translate(left ? -.56 : .56, -.52, -.72)
                    .rotateY(left ? -Math.PI : Math.PI).rotateZ(Math.PI / 2).scale(.55);
            double[] oracle = expected.get(new double[16]);
            float[] drawn = actual.get(new float[16]);
            for (int i = 0; i < 16; i++) assertEquals(oracle[i], drawn[i], 1e-6, "Exact vanilla fitting element" + i);
            assertEquals(.55, actual.transformDirection(new Vector3f(0, 1, 0)).length(), 1e-6,
                    "Source units/proportions must not be retuned for left");
        }
        var mirrored = new Matrix4f(matrices[0]).m30(-matrices[0].m30());
        assertTrue(mirrored.equals(matrices[1], 1e-6f), "Only physical side placement differs before fitting");
    }
}
