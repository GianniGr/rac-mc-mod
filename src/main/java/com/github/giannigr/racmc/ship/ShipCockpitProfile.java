package com.github.giannigr.racmc.ship;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared Minecraft geometry contract, in blocks at ship yaw zero (+Z is the bow).
 * The scale, body anchor, sill and outer envelope are independent measurements, not proportions.
 */
public record ShipCockpitProfile(double blocksPerSourceUnit, float width, float height,
                                 Vec3 seatLocal, Vec3 sillLocal) {
    /** Retained baseline until a measured cockpit fit is agreed with presentation. */
    public static final ShipCockpitProfile CURRENT =
            new ShipCockpitProfile(.20, 1.8F, 2.45F, new Vec3(0, .54, 0), new Vec3(0, .65, .20));

    private static final double CLEARANCE = 1e-6;
    private static final double EXIT_GAP = .20;

    public ShipCockpitProfile {
        if (!Double.isFinite(blocksPerSourceUnit) || blocksPerSourceUnit <= 0
                || !Float.isFinite(width) || width <= 0 || !Float.isFinite(height) || height <= 0
                || !finite(seatLocal) || !finite(sillLocal)
                || seatLocal.y < 0 || seatLocal.y >= height || seatLocal.horizontalDistance() >= width / 2.0) {
            throw new IllegalArgumentException("Invalid ship cockpit profile");
        }
    }

    /** The ship might yaw after boarding; prove the centered world-axis envelope at every angle. */
    public boolean fitsAtAllHeadings(EntityDimensions standing) {
        return seatLocal.horizontalDistance() + standing.width() / 2.0 <= width / 2.0 + CLEARANCE
                && seatLocal.y + standing.height() <= height + CLEARANCE;
    }

    static boolean containsPilot(AABB hull, Vec3 seat, EntityDimensions standing) {
        AABB pilot = standing.makeBoundingBox(seat);
        return pilot.minX >= hull.minX - CLEARANCE && pilot.maxX <= hull.maxX + CLEARANCE
                && pilot.minZ >= hull.minZ - CLEARANCE && pilot.maxZ <= hull.maxZ + CLEARANCE
                && pilot.minY >= hull.minY - CLEARANCE && pilot.maxY <= hull.maxY + CLEARANCE;
    }

    static double exitDistance(AABB hull, EntityDimensions standing) {
        return Math.max(hull.getXsize(), hull.getZsize()) / 2 + standing.width() / 2.0 + EXIT_GAP;
    }

    static boolean outsideHull(AABB hull, Vec3 feet, EntityDimensions standing) {
        return !hull.intersects(standing.makeBoundingBox(feet));
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
