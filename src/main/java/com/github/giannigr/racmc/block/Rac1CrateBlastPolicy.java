package com.github.giannigr.racmc.block;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Owner-approved entity balance; independent of crate-chain geometry and vanilla TNT damage. */
public final class Rac1CrateBlastPolicy {
    public static final float BASE_DAMAGE = 5.0F;
    public static final double PLAYER_RADIUS = 2.0;
    public static final double MOB_RADIUS = 3.0;
    public static final float TNT_KNOCKBACK_POWER = 4.0F;
    public static final double KNOCKBACK_RADIUS = 8.0;

    private Rac1CrateBlastPolicy() {
    }

    public static double radius(boolean player) {
        return player ? PLAYER_RADIUS : MOB_RADIUS;
    }

    public static double distance(Vec3 center, AABB bounds) {
        double x = Math.clamp(center.x, bounds.minX, bounds.maxX);
        double y = Math.clamp(center.y, bounds.minY, bounds.maxY);
        double z = Math.clamp(center.z, bounds.minZ, bounds.maxZ);
        return center.distanceTo(new Vec3(x, y, z));
    }

    public static boolean inRange(Vec3 center, AABB bounds, boolean player) {
        return distance(center, bounds) <= radius(player);
    }

    public static float damage(double exposure) {
        return (float) (BASE_DAMAGE * exposure(exposure));
    }

    /** Vanilla TNT uses entity-origin distance, not the damage query's closest AABB point. */
    public static double knockback(double distance, double exposure) {
        if (!Double.isFinite(distance) || distance < 0 || distance > KNOCKBACK_RADIUS) {
            return 0;
        }
        return (1.0 - distance / KNOCKBACK_RADIUS) * exposure(exposure);
    }

    private static double exposure(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 0.0;
    }
}
