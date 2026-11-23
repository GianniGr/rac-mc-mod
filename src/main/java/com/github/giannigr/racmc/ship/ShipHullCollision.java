package com.github.giannigr.racmc.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Measured, rotated outer footprint for people, not the square terrain sweep. */
public final class ShipHullCollision {
    static final double STERN = -4.278368, BOW = 5.006094, TOP = 3.994919;
    private static final double STRIP_DEPTH = .20;
    private static final double CORNER_RADIUS = Math.hypot(ShipSideAccess.HALF_BEAM, Math.max(BOW, -STERN));
    private record ExceptionFor(Player pilot, Rac1TravelShip ship) {}
    private static final ThreadLocal<ExceptionFor> ACCESS = new ThreadLocal<>();

    static boolean checkOwnAccess(Player pilot, Rac1TravelShip ship, BooleanSupplier check) {
        var previous = ACCESS.get();
        ACCESS.set(new ExceptionFor(pilot, ship));
        try {
            return check.getAsBoolean();
        } finally {
            if (previous == null) ACCESS.remove();
            else ACCESS.set(previous);
        }
    }

    public static List<VoxelShape> appendPlayerShapes(EntityGetter level, Entity mover, AABB area,
                                                     List<VoxelShape> vanilla) {
        if (!(mover instanceof Player pilot) || pilot.isSpectator() || area.getSize() < 1e-7) return vanilla;
        var exception = ACCESS.get();
        // Native section indexing anchors entities at their origin and normally scans
        // only 2 blocks beyond the query horizontally. The ratio hull spans more.
        var ships = level.getEntitiesOfClass(Rac1TravelShip.class, area.inflate(CORNER_RADIUS, TOP, CORNER_RADIUS),
                ship -> ship.hasRefitCockpit() && !ship.isRemoved() && pilot.getVehicle() != ship
                        && (exception == null || exception.pilot != pilot || exception.ship != ship));
        if (ships.isEmpty()) return vanilla;
        var shapes = new ArrayList<>(vanilla);
        // noCollision treats every returned entity shape as an overlap. Exact face
        // contact is a safe stand, while an inward sweep still intersects the inset.
        var interior = area.deflate(1e-7);
        for (var ship : ships) {
            for (AABB part : ship.onFootHull()) {
                if (part.intersects(interior)) shapes.add(Shapes.create(part));
            }
        }
        return List.copyOf(shapes);
    }

    /** Check the moving hull against stationary walkers, without changing the native block solver. */
    static boolean hitsWalker(ServerLevel level, Rac1TravelShip ship, AABB hull, Vec3 delta, float fromYaw, float toYaw) {
        float halfTurn = Mth.wrapDegrees(toYaw - fromYaw) / 2;
        if (!ship.hasRefitCockpit() || (delta.lengthSqr() == 0 && halfTurn == 0)) return false;
        double turnMargin = CORNER_RADIUS * Math.toRadians(Math.abs(halfTurn));
        double extra = Math.max(0, CORNER_RADIUS - hull.getXsize() / 2) + turnMargin;
        var players = level.getEntitiesOfClass(Player.class, hull.inflate(extra, 0, extra).expandTowards(delta),
                p -> p.isAlive() && !p.isSpectator() && p.getVehicle() != ship);
        if (players.isEmpty()) return false;
        Vec3 origin = new Vec3((hull.minX + hull.maxX) / 2, hull.minY, (hull.minZ + hull.maxZ) / 2);
        // Each point's arc from the midheading is bounded by radius × half-angle.
        // Include intermediate rotation, not just the two heading endpoints.
        var parts = halfTurn == 0 && fromYaw == ship.getYRot() && origin.distanceToSqr(ship.position()) < 1e-12
                ? ship.onFootHull() : boxes(origin, fromYaw + halfTurn);
        for (var part : parts) {
            var sweep = part.inflate(turnMargin, 0, turnMargin).expandTowards(delta);
            for (var player : players) {
                if (sweep.intersects(player.getBoundingBox())) return true;
            }
        }
        return false;
    }

    /** Narrow world-Z strips of an oriented measured rectangle: no empty yaw-square corners. */
    static List<AABB> boxes(Vec3 origin, float yaw) {
        double r = Math.toRadians(yaw);
        double sin = Math.sin(r), cos = Math.cos(r);
        double x = ShipSideAccess.HALF_BEAM;
        double[] xs = {-x, x, x, -x}, zs = {STERN, STERN, BOW, BOW};
        double zMin = Double.POSITIVE_INFINITY, zMax = Double.NEGATIVE_INFINITY;
        double xMin = Double.POSITIVE_INFINITY, xMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            double tx = cos * xs[i] - sin * zs[i], tz = sin * xs[i] + cos * zs[i];
            xs[i] = tx;
            zs[i] = tz;
            zMin = Math.min(zMin, tz);
            zMax = Math.max(zMax, tz);
            xMin = Math.min(xMin, tx);
            xMax = Math.max(xMax, tx);
        }
        if (Math.abs(sin) < 1e-7 || Math.abs(cos) < 1e-7) {
            return List.of(new AABB(xMin, 0, zMin, xMax, TOP, zMax).move(origin));
        }
        int rows = (int)Math.ceil((zMax - zMin) / STRIP_DEPTH);
        var boxes = new ArrayList<AABB>(rows);
        for (int row = 0; row < rows; row++) {
            double start = zMin + row * STRIP_DEPTH, end = Math.min(zMax, start + STRIP_DEPTH);
            double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                if (zs[i] >= start && zs[i] <= end) {
                    left = Math.min(left, xs[i]);
                    right = Math.max(right, xs[i]);
                }
                int j = (i + 1) % 4;
                for (double boundary : new double[]{start, end}) {
                    if (boundary > Math.min(zs[i], zs[j]) && boundary < Math.max(zs[i], zs[j])) {
                        double q = xs[i] + (xs[j] - xs[i]) * (boundary - zs[i]) / (zs[j] - zs[i]);
                        left = Math.min(left, q);
                        right = Math.max(right, q);
                    }
                }
            }
            if (left < right) boxes.add(new AABB(left, 0, start, right, TOP, end).move(origin));
        }
        return List.copyOf(boxes);
    }

    private ShipHullCollision() {}
}
