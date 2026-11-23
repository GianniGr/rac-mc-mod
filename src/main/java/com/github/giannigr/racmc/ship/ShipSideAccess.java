package com.github.giannigr.racmc.ship;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Stationary access beside the measured refit hull, separate from the yaw-safe flight sweep. */
final class ShipSideAccess {
    // P46's 2d55e32 figure for the largest exterior X of the clipped hull, in world-sized blocks.
    static final double HALF_BEAM = 2.883597;
    private static final double CONTACT_TOLERANCE = 1e-5;
    private static final double EXIT_GAP = .30;
    private static final double STATION_HALF_LENGTH = .90;
    private static final double AIM_HALF_LENGTH = 1.10;

    static boolean besideCockpit(Vec3 localFeet, float yaw, float pilotWidth, Vec3 seat) {
        return Math.abs(localFeet.z - seat.z) <= STATION_HALF_LENGTH
                && Math.abs(localFeet.x) + CONTACT_TOLERANCE >= HALF_BEAM + projectedHalfWidth(yaw, pilotWidth);
    }

    static double exitSide(float yaw, float pilotWidth) {
        return HALF_BEAM + projectedHalfWidth(yaw, pilotWidth) + EXIT_GAP;
    }

    private static double projectedHalfWidth(float yaw, float width) {
        double angle = Math.toRadians(yaw);
        // AABB half-width projected onto ship-local X; covers every yaw, including diagonal stands.
        return width / 2.0 * (Math.abs(Math.cos(angle)) + Math.abs(Math.sin(angle)));
    }

    /** Intersection of the server player's look ray with the bounded cockpit-side target. */
    static @Nullable Vec3 aimPoint(Vec3 localEye, Vec3 localDirection, double reach,
                                   double side, Vec3 seat, double hullHeight) {
        double inward = localDirection.x * side;
        if (inward >= -1e-6) return null;
        double t = (side * HALF_BEAM - localEye.x) / localDirection.x;
        if (t <= 0 || t > reach) return null;
        Vec3 hit = localEye.add(localDirection.scale(t));
        return hit.y >= 0 && hit.y <= hullHeight && Math.abs(hit.z - seat.z) <= AIM_HALF_LENGTH ? hit : null;
    }

    private ShipSideAccess() {}
}
