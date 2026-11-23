package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.client.bomb.BombGloveCuff.ArmFit;
import com.github.giannigr.racmc.client.bomb.BombGloveCuff.Vertex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** A vanilla player-arm replacement draw. Never changes a ModelPart, armor, or a BufferSource. */
final class BombGloveArmMask {
    private static final float PIXELS = 16;

    private BombGloveArmMask() {}

    record Face(List<Vertex> vertices, float nx, float ny, float nz) {}

    record UpperArm(List<Face> faces) {
        /** Inherited skin/sleeve pose, light, overlay and tint; no separate lower-arm draw. */
        void draw(ModelPart part, PoseStack poses, VertexConsumer target, int light, int overlay, int color) {
            if (faces.isEmpty()) return;
            poses.pushPose();
            try {
                part.translateAndRotate(poses);
                drawLocal(poses, target, light, overlay, color);
            } finally {
                poses.popPose();
            }
        }

        void drawLocal(PoseStack poses, VertexConsumer target, int light, int overlay, int color) {
            var pose = poses.last();
            var point = new Vector3f();
            var normal = new Vector3f();
            for (Face face : faces) {
                pose.transformNormal(face.nx(), face.ny(), face.nz(), normal);
                for (Vertex v : face.vertices()) {
                    pose.pose().transformPosition(v.x() / PIXELS, v.y() / PIXELS, v.z() / PIXELS, point);
                    target.addVertex(point.x, point.y, point.z, color, v.u(), v.v(), overlay, light,
                            normal.x, normal.y, normal.z);
                }
            }
        }
    }

    /**
     * Call only on the selected vanilla skin arm/sleeve after the glove itself is ready.
     * Empty Optional: leave the normal part render untouched. Present with zero faces:
     * the specific vanilla skin part is already hidden (e.g. sleeve disabled).
     */
    static Optional<UpperArm> prepare(ModelPart part, ArmFit fit, boolean sleeve, float cuffY) {
        if (!part.visible || part.skipDraw) return Optional.of(new UpperArm(List.of()));
        var parts = part.getAllParts().limit(2).toList();
        if (parts.size() != 1) return Optional.empty();
        var cubes = new ArrayList<ModelPart.Cube>(1);
        var local = new PoseStack();
        part.visit(local, (pose, path, index, cube) -> cubes.add(cube));
        if (cubes.size() != 1) return Optional.empty();
        var c = cubes.getFirst();
        if (c.minX != fit.minX || c.maxX != fit.minX + fit.width || c.minY != -2 || c.maxY != 10
                || c.minZ != -2 || c.maxZ != 2) return Optional.empty();

        // Compile the known vanilla cuboid at identity: retains its UV atlas and actual dilation.
        var capture = new CubeFaces();
        c.compile(local.last(), capture, 0, 0, -1);
        if (capture.faces.size() != 6 || !capture.quad.isEmpty()) return Optional.empty();
        var clipped = new ArrayList<Face>(5);
        float growth = sleeve ? .25f : 0;
        for (Face face : capture.faces) {
            for (Vertex v : face.vertices()) {
                if ((v.x() != fit.minX - growth && v.x() != fit.minX + fit.width + growth)
                        || (v.y() != -2 - growth && v.y() != 10 + growth)
                        || (v.z() != -2 - growth && v.z() != 2 + growth)) return Optional.empty();
            }
            var faceAboveCuff = BombGloveCuff.upperFace(face.vertices(), cuffY);
            if (!faceAboveCuff.isEmpty()) clipped.add(new Face(faceAboveCuff, face.nx(), face.ny(), face.nz()));
        }
        return Optional.of(new UpperArm(List.copyOf(clipped)));
    }

    private static final class CubeFaces implements VertexConsumer {
        private final ArrayList<Face> faces = new ArrayList<>(6);
        private final ArrayList<Vertex> quad = new ArrayList<>(4);

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            quad.add(new Vertex(x * PIXELS, y * PIXELS, z * PIXELS, u, v));
            if (quad.size() == 4) {
                faces.add(new Face(List.copyOf(quad), nx, ny, nz));
                quad.clear();
            }
        }

        // ModelPart.Cube.compile uses the complete-vertex method above.
        @Override public VertexConsumer addVertex(float x, float y, float z) { throw incomplete(); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw incomplete(); }
        @Override public VertexConsumer setUv(float u, float v) { throw incomplete(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw incomplete(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw incomplete(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw incomplete(); }
        private static UnsupportedOperationException incomplete() {
            return new UnsupportedOperationException("Expected a vanilla cuboid vertex");
        }
    }
}
