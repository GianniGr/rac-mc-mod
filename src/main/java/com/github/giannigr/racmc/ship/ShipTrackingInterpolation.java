package com.github.giannigr.racmc.ship;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Tick-owned client rendering of vanilla tracker targets; no prediction or outbound transforms. */
final class ShipTrackingInterpolation {
    private Vec3 target;
    private float targetYaw;
    private int remaining;
    private boolean rebase;

    record Frame(Vec3 position, float yaw, boolean rebase) {}

    boolean accept(Vec3 current, Vec3 nextTarget, float yaw) {
        Vec3 delta = nextTarget.subtract(target(current));
        // Vanilla position deltas are signed shorts in units of 1/4096 block per axis.
        // Compare authoritative targets, never a rendered position deliberately behind them.
        rebase |= Math.abs(delta.x) > 8 || Math.abs(delta.y) > 8 || Math.abs(delta.z) > 8;
        target = nextTarget;
        targetYaw = yaw;
        remaining = 2;
        return rebase;
    }

    Frame advance(Vec3 current, float yaw) {
        if (remaining == 0) return new Frame(current, yaw, false);
        if (rebase) {
            remaining = 0;
            rebase = false;
            return new Frame(target, targetYaw, true);
        }
        double fraction = 1.0 / remaining--;
        return new Frame(current.lerp(target, fraction), Mth.rotLerp((float) fraction, yaw, targetYaw), false);
    }

    Vec3 target(Vec3 fallback) { return target == null ? fallback : target; }
    float targetYaw(float fallback) { return target == null ? fallback : targetYaw; }
}
