package com.github.giannigr.racmc.client.ship;

import net.minecraft.world.phys.Vec3;

/**
 * Minecraft-only Steve blocking. Supplied points already share the same space and are never
 * written back to the entity, controller or camera.
 */
public final class GreenShipPilotPose {
    private static final Angles STAND = new Angles(0, 0, 0, 0, 0);
    private static final Angles REACH = new Angles(24, -92, -73, -40, 21);
    private static final Angles TUCK = new Angles(31, -66, -59, -91, -77);
    private static final Angles SEATED = new Angles(8, -46, -46, -82, -82);
    private static final Angles UNSEAT = new Angles(34, -71, -65, -50, -57);
    private static final Angles LOWER = new Angles(8, -19, -68, 16, -36);

    private GreenShipPilotPose() {}

    /** Degrees around Steve's local X axis, before the caller applies ship-facing yaw. */
    public record Angles(double torso, double rightArm, double leftArm,
                         double rightLeg, double leftLeg) {}

    public record Sample(Vec3 base, Angles angles, double canopyAccess) {}

    public static Sample board(Vec3 origin, Vec3 sill, Vec3 seat, double progress) {
        double t = clamp(progress);
        if (t < .46) {
            double mix = ease(t / .46);
            return new Sample(origin.lerp(sill, mix), blend(STAND, REACH, mix), ease(t / .22));
        }
        double mix = ease((t - .46) / .54);
        Angles angles = mix < .5 ? blend(REACH, TUCK, mix * 2) : blend(TUCK, SEATED, mix * 2 - 1);
        return new Sample(sill.lerp(seat, mix), angles, 1 - ease((t - .8) / .2));
    }

    public static Sample seated(Vec3 seat) {
        return new Sample(seat, SEATED, 0);
    }

    /** A seat rise and braced step-down, not a reversal of the entry pose or a retail exit. */
    public static Sample exit(Vec3 seat, Vec3 sill, Vec3 destination, double progress) {
        double t = clamp(progress);
        double canopy = ease(t / .18) * (1 - ease((t - .83) / .17));
        if (t < .2) {
            return new Sample(seat, blend(SEATED, UNSEAT, ease(t / .2)), canopy);
        }
        if (t < .58) {
            double mix = ease((t - .2) / .38);
            return new Sample(seat.lerp(sill, mix), blend(UNSEAT, LOWER, mix), canopy);
        }
        double mix = ease((t - .58) / .42);
        return new Sample(sill.lerp(destination, mix), blend(LOWER, STAND, mix), canopy);
    }

    private static Angles blend(Angles a, Angles b, double mix) {
        return new Angles(lerp(a.torso(), b.torso(), mix), lerp(a.rightArm(), b.rightArm(), mix),
                lerp(a.leftArm(), b.leftArm(), mix), lerp(a.rightLeg(), b.rightLeg(), mix),
                lerp(a.leftLeg(), b.leftLeg(), mix));
    }

    private static double lerp(double start, double end, double mix) {
        return start + mix * (end - start);
    }

    private static double ease(double t) {
        double f = clamp(t);
        return f * f * (3 - 2 * f);
    }

    private static double clamp(double t) {
        return Double.isNaN(t) ? 0 : Math.max(0, Math.min(1, t));
    }
}
