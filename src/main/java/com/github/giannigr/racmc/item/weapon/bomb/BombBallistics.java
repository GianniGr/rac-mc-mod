package com.github.giannigr.racmc.item.weapon.bomb;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class BombBallistics {
    static final int FLIGHT_TICKS = 100;
    static final int DEEP_WATER_TICKS = 7;
    static final int WAVE_STEPS = 5;
    static final double GRAVITY = 11.0 / 400;
    static final double WATER_GRAVITY = 1.1 / 400;
    static final Vec3 SINK = new Vec3(0, -1.5 / 20, 0);

    private BombBallistics() {}

    static Vec3 launch(float yaw, float pitch) {
        double radians = Math.toRadians(Mth.clamp(pitch, -80, 34));
        double up = Mth.clamp(.16 - Math.sin(radians) * .31, -.275, .275);
        return Vec3.directionFromRotation(0, yaw).scale(8.5 / 20).add(0, up, 0);
    }

    static double radius(int step) {
        return .5 + 2.0 * Mth.clamp(step, 0, WAVE_STEPS) / WAVE_STEPS;
    }
}
