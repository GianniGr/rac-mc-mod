package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.asset.Rac1VendorAsset;
import com.github.giannigr.racmc.asset.Rac1VendorPose;
import com.github.giannigr.racmc.block.Rac1VendorFit;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Actual retail vertices through Minecraft's item transforms; no GPU, asset files or client. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscVendorItemTransformTest {
    @Test
    void recognizableMainBodyMatchesBlockAndCompleteGuiMeshStaysInsideSlot() throws Exception {
        try (var outcome = new Rac1DiscValidator().inspect(
                Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported(), outcome.result().message());
            var level = Rac1LevelAssets.load(outcome.image(), Rac1AssetExtractor.LEVEL);
            var vendor = new Rac1VendorAsset(Rac1AssetExtractor.readAnimatedAsset(level, 11),
                    Rac1AssetExtractor.readAnimatedAsset(level, 1143));
            var mesh = vendor.body().mesh();
            float[] closed = Rac1VendorPose.closed(vendor).positions();
            var item = Rac1VendorFit.forItem(mesh, closed);
            var old = Rac1VendorFit.of(closed);
            assertEquals(.853442f, item.scale(), .001);
            assertEquals(1.965393f, item.scale() / old.scale(), .001);
            assertEquals(.5f, item.x(), .001);
            assertEquals(.5f, item.z(), .001);
            float[] body = new float[closed.length];
            boolean[] selected = new boolean[mesh.vertexCount()];
            int triangles = 0;
            for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
                if (mesh.materials()[triangle] == 1 || mesh.materials()[triangle] == 2) {
                    triangles++;
                    for (int corner = 0; corner < 3; corner++) {
                        selected[mesh.indices()[triangle * 3 + corner]] = true;
                    }
                }
            }
            assertEquals(1016, triangles, "Verified enclosure selection, not antenna clipping");
            int count = 0;
            for (int vertex = 0; vertex < selected.length; vertex++) {
                if (selected[vertex]) {
                    System.arraycopy(closed, vertex * 3, body, count, 3);
                    count += 3;
                }
            }
            body = java.util.Arrays.copyOf(body, count);
            var json = JsonParser.parseString(Files.readString(Path.of(
                    System.getProperty("racmc.projectDir"),
                    "src/main/resources/assets/racmc/models/item/gadgetron_vendor.json")))
                    .getAsJsonObject();
            var gson = new GsonBuilder().registerTypeAdapter(ItemTransform.class,
                    new ItemTransform.Deserializer()).create();
            float[] cube = cube();
            for (String context : new String[] {"gui", "ground", "fixed", "thirdperson_righthand",
                    "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand"}) {
                var transform = gson.fromJson(json.getAsJsonObject("display").get(context),
                        ItemTransform.class);
                for (boolean left : new boolean[] {false, true}) {
                    PoseStack vanilla = chain(transform, left);
                    PoseStack placed = chain(transform, left);
                    Rac1VendorDrawing.place(placed, item);
                    float[] fullBox = box(placed, closed);
                    float[] bodyBox = box(placed, body);
                    float[] cubeBox = box(vanilla, cube);
                    for (int axis = 0; axis < 2; axis++) {
                        float ratio = span(bodyBox, axis) / span(cubeBox, axis);
                        assertTrue(ratio >= .85f && ratio <= 1.10f,
                                context + " body must be comparable to a vanilla block, ratio=" + ratio);
                    }
                    if (context.equals("gui")) {
                        assertTrue(fullBox[0] >= -.5f && fullBox[1] <= .5f
                                && fullBox[2] >= -.5f && fullBox[3] <= .5f,
                                "All original antennas/cap must fit the 16px slot, not only the body");
                        assertTrue(Math.abs(fullBox[0] + fullBox[1]) / 2 <= 1f / 16
                                && Math.abs(fullBox[2] + fullBox[3]) / 2 <= 1f / 16,
                                "Complete GUI icon center must be within one pixel of slot center");
                    }
                    System.out.printf(Locale.ROOT,
                            "%s %s left=%s itemScale=%.6f enlargement=%.6f "
                            + "bodyXY=%.3fx%.3fpx fullXY=%.3fx%.3fpx "
                            + "fullBoundsXY=[%.3f,%.3f;%.3f,%.3f]px%n",
                            outcome.result().revision(), context, left, item.scale(),
                            item.scale() / old.scale(), span(bodyBox, 0) * 16, span(bodyBox, 1) * 16,
                            span(fullBox, 0) * 16, span(fullBox, 1) * 16,
                            fullBox[0] * 16, fullBox[1] * 16, fullBox[2] * 16, fullBox[3] * 16);
                }
            }
        }
    }

    private static PoseStack chain(ItemTransform transform, boolean left) {
        PoseStack pose = new PoseStack();
        transform.apply(left, pose);
        pose.translate(-.5f, -.5f, -.5f);
        return pose;
    }

    private static float[] cube() {
        float[] points = new float[24];
        for (int corner = 0; corner < 8; corner++) {
            points[corner * 3] = corner & 1;
            points[corner * 3 + 1] = (corner >> 1) & 1;
            points[corner * 3 + 2] = (corner >> 2) & 1;
        }
        return points;
    }

    private static float span(float[] box, int axis) {
        return box[axis * 2 + 1] - box[axis * 2];
    }

    private static float[] box(PoseStack pose, float[] positions) {
        float[] box = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int at = 0; at < positions.length; at += 3) {
            var point = pose.last().pose().transformPosition(
                    new Vector3f(positions[at], positions[at + 1], positions[at + 2]));
            box[0] = Math.min(box[0], point.x);
            box[1] = Math.max(box[1], point.x);
            box[2] = Math.min(box[2], point.y);
            box[3] = Math.max(box[3], point.y);
        }
        return box;
    }
}
