package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipHullPose;
import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/** Offline check against an already-loaded windshield; requests no media or texture. */
final class GreenShipCockpitGlassFit {
    // Circumscribes the vanilla inflated hat for arbitrary head rotation, plus 0.025 block clearance.
    static final double HEAD_RADIUS = Math.sqrt(4.5 * 4.5 * 2 + 8.5 * 8.5)
            * GreenShipCockpitInterior.PIXEL;
    private static final double HEAD_PADDING = .025;

    enum Fit { NO_GLASS, BLOCKED, CLEAR }

    record Triangle(Vec3 a, Vec3 b, Vec3 c) {}

    private GreenShipCockpitGlassFit() {}

    static Fit check(GreenShipGlassPass.Table table, float[] joint) {
        if (table == null) return Fit.NO_GLASS; // PAL remains a separate, explicit no-glass presentation.
        double ratio = ShipCockpitRefit.CANDIDATE.blocksPerSourceUnit() / GreenShipHullPose.BLOCKS_PER_UNIT;
        var vertices = GreenShipGlassPass.project(table, new Matrix4f().set(joint), Vec3.ZERO).stream()
                .map(v -> v.blocks().scale(ratio)).toList();
        var triangles = new ArrayList<Triangle>();
        for (var q : table.quads()) {
            var a = vertices.get(q.a());
            var d = vertices.get(q.d());
            triangles.add(new Triangle(a, vertices.get(q.b()), d));
            triangles.add(new Triangle(a, d, vertices.get(q.c())));
        }
        return check(triangles);
    }

    static Fit check(List<Triangle> glass) {
        var seat = ShipCockpitRefit.CANDIDATE.seatLocal();
        var pivot = vector(seat.add(0, GreenShipCockpitInterior.BODY_DATUM, 0));
        var eye = vector(seat.add(0, 1.62, 0));
        var forward = new Vector3d(0, 0, 1);
        var closest = new Vector3d();
        boolean sightline = false;
        double limit = (HEAD_RADIUS + HEAD_PADDING) * (HEAD_RADIUS + HEAD_PADDING);
        for (var triangle : glass) {
            var a = vector(triangle.a());
            var b = vector(triangle.b());
            var c = vector(triangle.c());
            if (!a.isFinite() || !b.isFinite() || !c.isFinite()) return Fit.BLOCKED;
            if (new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a)).lengthSquared() < 1e-18) continue;
            Intersectiond.findClosestPointOnTriangle(a, b, c, pivot, closest);
            if (!closest.isFinite() || closest.distanceSquared(pivot) <= limit) return Fit.BLOCKED;
            double distance = Intersectiond.intersectRayTriangle(eye, forward, a, b, c, 1e-8);
            sightline |= Double.isFinite(distance) && distance > 0;
        }
        return sightline ? Fit.CLEAR : Fit.BLOCKED;
    }

    private static Vector3d vector(Vec3 v) {
        return new Vector3d(v.x, v.y, v.z);
    }
}
