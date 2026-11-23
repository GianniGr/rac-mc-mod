package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipCanopy;
import com.github.giannigr.racmc.asset.ship.GreenShipHullPose;
import com.github.giannigr.racmc.effect.EffectMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Separate FX21 canopy pass, spatially bound to list 0's terminal pose, not inverse bind skinning. */
public final class GreenShipGlassPass {
    private static final int MAX_POINTS = 256;
    private static final int MAX_QUADS = 256;

    private GreenShipGlassPass() {}

    public record Point(Vec3 position, Vec3 sourceNormal) {}
    /** Source strip notation. Submit corners as perimeter order. */
    public record Quad(int a, int b, int c, int d) {}
    public record Table(List<Point> points, List<Quad> quads, int argb) {
        public Table {
            points = List.copyOf(points);
            quads = List.copyOf(quads);
            if (points.isEmpty() || quads.isEmpty() || points.size() > MAX_POINTS
                    || quads.size() > MAX_QUADS) {
                throw new IllegalArgumentException("Invalid class 531 glass table size");
            }
            for (Point point : points) {
                if (!finite(point.position()) || !finite(point.sourceNormal())) {
                    throw new IllegalArgumentException("Invalid class 531 glass point");
                }
            }
            for (Quad quad : quads) {
                for (int index : new int[] {quad.a(), quad.b(), quad.c(), quad.d()}) {
                    if (index < 0 || index >= points.size()) {
                        throw new IllegalArgumentException("Invalid class 531 glass quad index");
                    }
                }
            }
        }
    }

    /** Convert the bounded GS records before publication; no texture or GPU work here. */
    static Table prepare(GreenShipCanopy.Geometry geometry) {
        var points = new ArrayList<Point>(geometry.points().size());
        for (int i = 0; i < geometry.points().size(); i++) {
            var p = geometry.points().get(i);
            var n = geometry.normals().get(i);
            points.add(new Point(new Vec3(p.x(), p.y(), p.z()), new Vec3(n.x(), n.y(), n.z())));
        }
        var quads = geometry.quads().stream().map(q -> new Quad(q.a(), q.b(), q.c(), q.d())).toList();
        return new Table(points, quads, Integer.rotateRight(EffectMaterial.rgba(geometry.tintGs()), 8));
    }

    /** Both independently verified inputs must be ready or no glass is drawn. */
    public static void draw(Table verifiedTable, RenderType verifiedFx21, GreenShipHullPose hull,
                            boolean closed, Vec3 cameraInShipBlocks, PoseStack pose,
                            MultiBufferSource buffers) {
        if (verifiedTable == null || verifiedFx21 == null) {
            return;
        }
        Matrix4f terminal = new Matrix4f().set(closed ? hull.closedGlass() : hull.parkedGlass());
        Vec3 cameraInSource = new Vec3(cameraInShipBlocks.z, cameraInShipBlocks.x, cameraInShipBlocks.y)
                .scale(1 / GreenShipHullPose.BLOCKS_PER_UNIT);
        var points = project(verifiedTable, terminal, cameraInSource);
        var consumers = buffers.getBuffer(verifiedFx21);
        for (Quad quad : verifiedTable.quads()) {
            for (int index : new int[] {quad.a(), quad.b(), quad.d(), quad.c()}) {
                Vertex v = points.get(index);
                consumers.addVertex(pose.last(), (float) v.blocks().x, (float) v.blocks().y, (float) v.blocks().z)
                        .setColor(verifiedTable.argb()).setUv(v.u(), v.v());
            }
        }
    }

    record Vertex(Vec3 blocks, Vec3 normal, float u, float v) {}

    /** Source callback's live reflection projection, with its translated (w=1) normal. */
    static List<Vertex> project(Table table, Matrix4f terminal, Vec3 cameraInSource) {
        var out = new ArrayList<Vertex>(table.points().size());
        for (Point point : table.points()) {
            Vec3 source = transform(terminal, point.position());
            Vec3 normal = transform(terminal, point.sourceNormal()).normalize();
            Vec3 n = normal.scale(.1);
            Vec3 eye = source.subtract(cameraInSource).normalize();
            Vec3 reflected = eye.subtract(n.scale(2 * n.dot(eye))).normalize();
            double denominator = 2 * Math.sqrt(Math.max(0, 2 * (reflected.z + 1)));
            float u = denominator < .000001 ? .5f : (float) (.5 + reflected.x / denominator);
            float v = denominator < .000001 ? .5f : (float) (.5 + reflected.y / denominator);
            out.add(new Vertex(shipAxes(source).scale(GreenShipHullPose.BLOCKS_PER_UNIT),
                    shipAxes(normal), u, v));
        }
        return List.copyOf(out);
    }

    private static Vec3 transform(Matrix4f terminal, Vec3 vector) {
        Vector3f p = terminal.transformPosition((float) vector.x, (float) vector.y, (float) vector.z,
                new Vector3f());
        return new Vec3(p);
    }

    private static Vec3 shipAxes(Vec3 v) {
        return new Vec3(v.y, v.z, v.x);
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
