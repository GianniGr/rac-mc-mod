package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.ShipEngineState.*;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Read-only evidence for presentation. Never contributes to motion or exit admission. */
final class ShipEnginePhysics {
    static final int LOOKAHEAD_TICKS = 8;
    private static final double CONTACT_PROBE = 1e-5;
    private static final double MOTION_EPS = 1e-7;

    static ShipEngineState contact(ServerLevel level, Entity ship, AABB hull) {
        var probe = hull.expandTowards(0, -CONTACT_PROBE, 0);
        if (!ShipSpace.loaded(level, probe) || !ShipSpace.dryAndSafe(level, probe)
                || !level.noCollision(ship, hull)) return UNVERIFIED;
        AABB sole = sole(hull);
        var floor = level.getBlockCollisions(ship, sole.expandTowards(0, -CONTACT_PROBE, 0));
        double drop = Shapes.collide(Direction.Axis.Y, sole, floor, -CONTACT_PROBE);
        // Nearness alone isn't support. Require the centre sole to be at block contact, not on an entity.
        return Math.abs(drop) <= MOTION_EPS ? SUPPORTED : AIRBORNE;
    }

    static ShipEngineBeat.Kind transition(ShipEngineState before, ShipEngineState after, Vec3 actual) {
        if (before == SUPPORTED && after == AIRBORNE && actual.y > MOTION_EPS) {
            return ShipEngineBeat.Kind.LIFTOFF;
        }
        if (before == AIRBORNE && after == SUPPORTED && actual.y < -MOTION_EPS) {
            return ShipEngineBeat.Kind.TOUCHDOWN;
        }
        return null;
    }

    static boolean verifiedApproach(ServerLevel level, Rac1TravelShip ship, int flags,
                                    Vec3 actual, Vec3 requested) {
        if (actual.y >= -MOTION_EPS || clipped(actual, requested)) return false;
        AABB box = ship.getBoundingBox();
        Vec3 velocity = ship.getDeltaMovement();
        float yaw = ship.getYRot();
        for (int i = 0; i < LOOKAHEAD_TICKS; i++) {
            // The same controller and native shape order as the live zero-step-height ship.
            boolean supported = ShipSpace.solidUnder(level, ship, sole(box));
            var step = ShipFlight.step(flags, yaw, velocity, supported, ship.isBoosted());
            var wanted = step.velocity();
            if (wanted.y >= -MOTION_EPS) return false;
            var sweep = box.expandTowards(wanted);
            if (!ShipSpace.loaded(level, sweep) || !ShipSpace.dryAndSafe(level, sweep)) return false;
            if (ShipHullCollision.hitsWalker(level, ship, box, wanted, yaw, step.yaw())) return false;
            var resolved = Entity.collideBoundingBox(ship, wanted, box, level, level.getEntityCollisions(ship, sweep));
            if (resolved.y >= -MOTION_EPS || Math.abs(resolved.x - wanted.x) > MOTION_EPS
                    || Math.abs(resolved.z - wanted.z) > MOTION_EPS) return false;
            box = box.move(resolved);
            var state = contact(level, ship, box);
            if (state == SUPPORTED) return true;
            if (state != AIRBORNE || Math.abs(resolved.y - wanted.y) > MOTION_EPS) return false;
            velocity = wanted;
            yaw = step.yaw();
        }
        return false;
    }

    private static boolean clipped(Vec3 actual, Vec3 requested) {
        return actual.distanceToSqr(requested) > MOTION_EPS * MOTION_EPS;
    }

    private static AABB sole(AABB hull) {
        double x = (hull.minX + hull.maxX) * .5, z = (hull.minZ + hull.maxZ) * .5;
        return new AABB(x - .10, hull.minY, z - .10, x + .10, hull.minY + .01, z + .10);
    }

    private ShipEnginePhysics() {}
}
