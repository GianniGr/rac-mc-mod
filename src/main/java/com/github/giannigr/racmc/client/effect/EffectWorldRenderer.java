package com.github.giannigr.racmc.client.effect;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.effect.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Quaternionf;

/** Bounded world backend; caller supplies camera-relative view pose before projection.
 * Sprite width = source size / 210000 is explicitly a world-scale approximation, NOT
 * PS2 pixel-accurate projection. Actual-texture fixture + owner acceptance remain mandatory.
 * Stable normal sorting/interleaving is preserved by flushing each draw; additive follows.
 * Mesh material routing reuses Rac1MeshRenderer, but preserves tint/alpha and soft depth state. */
public final class EffectWorldRenderer {
    private EffectWorldRenderer() {}
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers,
                              EffectGpuCatalog catalog, List<EffectRuntime.Draw> draws,
                              EffectSignal.Point camera, Quaternionf cameraOrientation) {
        if (draws.size() > 4096) throw new IllegalArgumentException("Draw budget exceeded");
        for (EffectRuntime.Draw draw : draws) {
            pose.pushPose();
            try {
                pose.translate(draw.position().x() - camera.x(), draw.position().y() - camera.y(),
                        draw.position().z() - camera.z());
                if (draw.geometry() == EffectRuntime.Geometry.BILLBOARD) {
                    RenderType type = catalog.type(draw.asset(), draw.material());
                    if (type == null) continue;
                    pose.mulPose(cameraOrientation);
                    pose.mulPose(new Quaternionf().rotationZ((float) draw.rotation().z()));
                    float half = draw.size() / 420000;
                    VertexConsumer vertex = buffers.getBuffer(type);
                    put(vertex, pose, -half, -half, 0, 0, 1, draw.rgba());
                    put(vertex, pose, half, -half, 0, 1, 1, draw.rgba());
                    put(vertex, pose, half, half, 0, 1, 0, draw.rgba());
                    put(vertex, pose, -half, half, 0, 0, 0, draw.rgba());
                    buffers.endBatch(type);
                } else {
                    var asset = catalog.mesh(draw.asset());
                    if (asset == null) continue;
                    pose.mulPose(new Quaternionf().rotationXYZ((float) draw.rotation().x(),
                            (float) draw.rotation().y(), (float) draw.rotation().z()));
                    pose.scale(draw.size(), draw.size(), draw.size());
                    Rac1Mesh mesh = asset.mesh();
                    for (var run : Rac1MeshRenderer.materialRuns(mesh.indices(), mesh.materials(),
                            asset.textures().size())) {
                        RenderType type = catalog.meshType(draw.asset(), run.slot(), draw.material());
                        if (type == null) continue;
                        VertexConsumer vertex = buffers.getBuffer(type);
                        for (int triangle : run.triangles())
                            for (int corner = 0; corner < 4; corner++) {
                                int id = mesh.indices()[triangle * 3 + corner % 3];
                                put(vertex, pose, mesh.positions()[id * 3], mesh.positions()[id * 3 + 1],
                                        mesh.positions()[id * 3 + 2], mesh.uvs()[id * 2],
                                        mesh.uvs()[id * 2 + 1], draw.rgba());
                            }
                        buffers.endBatch(type);
                    }
                }
            } finally {
                pose.popPose();
            }
        }
    }
    private static void put(VertexConsumer vertex, PoseStack pose, float x, float y, float z,
                             float u, float v, int rgba) {
        vertex.addVertex(pose.last(), x, y, z).setUv(u, v)
                .setColor(rgba >>> 24, rgba >>> 16 & 255, rgba >>> 8 & 255, rgba & 255);
    }
}
