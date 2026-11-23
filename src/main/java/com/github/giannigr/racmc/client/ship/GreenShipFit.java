package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipHullPose;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Scalar cockpit measurements from the existing NTSC lease, never an asset export or a seat key. */
final class GreenShipFit {
    private GreenShipFit() {}

    static String measure(GreenShipGlassPass.Table glass, GreenShipHullPose hull) {
        var vertices = GreenShipGlassPass.project(glass, new Matrix4f().set(hull.closedGlass()), Vec3.ZERO)
                .stream().map(GreenShipGlassPass.Vertex::blocks).toList();
        double minZ = vertices.stream().mapToDouble(Vec3::z).min().orElseThrow();
        double maxZ = vertices.stream().mapToDouble(Vec3::z).max().orElseThrow();
        double maxY = vertices.stream().mapToDouble(Vec3::y).max().orElseThrow();
        return "closed glass [zMin,zMax,yMax]=" + List.of(minZ, maxZ, maxY)
                + "; center roof [production origin, source hatch station]="
                + List.of(roof(glass.quads(), vertices, 0, 0),
                        roof(glass.quads(), vertices, 0, GreenShipHullPose.BLOCKS_PER_UNIT));
    }

    // Upward probe against the same triangle split as the submitted quad perimeter.
    static double roof(List<GreenShipGlassPass.Quad> quads, List<Vec3> vertices, double x, double z) {
        double y = Double.NEGATIVE_INFINITY;
        for (var q : quads) {
            var a = vertices.get(q.a());
            var b = vertices.get(q.b());
            var c = vertices.get(q.c());
            var d = vertices.get(q.d());
            y = Math.max(y, Math.max(height(a, b, d, x, z), height(a, d, c, x, z)));
        }
        return y;
    }

    private static double height(Vec3 a, Vec3 b, Vec3 c, double x, double z) {
        double area = (b.z - c.z) * (a.x - c.x) + (c.x - b.x) * (a.z - c.z);
        if (Math.abs(area) < 1e-9) return Double.NEGATIVE_INFINITY;
        double u = ((b.z - c.z) * (x - c.x) + (c.x - b.x) * (z - c.z)) / area;
        double v = ((c.z - a.z) * (x - c.x) + (a.x - c.x) * (z - c.z)) / area;
        double w = 1 - u - v;
        return Math.min(u, Math.min(v, w)) < -1e-8 ? Double.NEGATIVE_INFINITY : u * a.y + v * b.y + w * c.y;
    }
}
