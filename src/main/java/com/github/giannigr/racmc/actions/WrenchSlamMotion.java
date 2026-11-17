package com.github.giannigr.racmc.actions;

/** Original-style downward acceleration expressed in Minecraft blocks/tick at 20TPS. */
public final class WrenchSlamMotion {
    private WrenchSlamMotion() {}

    /** Original facts in units/second, simulated at 60Hz; native collision/fall logic is not reset. */
    public static double velocity(double current, int ageTicks, double clearance) {
        if (!Double.isFinite(current) || !Double.isFinite(clearance) || ageTicks < 1) {
            throw new IllegalArgumentException("Invalid slam motion observation");
        }
        double speed = current * 20;
        double up = 7.7 * (clearance > 2.25 ? 0.5 : clearance > 1.8 ? 0.7 : 1);
        for (int update = (ageTicks - 1) * 3 + 1; update <= ageTicks * 3; update++) {
            if (update < 9) {
                double change = Math.clamp(up - speed, -150.0 / 60, 150.0 / 60);
                speed += change;
            } else if (update > 14) {
                speed = Math.max(-37, speed - 130.0 / 60);
            }
        }
        return speed / 20;
    }

    /** 130 world units/s², capped at -37 units/s. Collision and fall damage remain vanilla-owned. */
    public static double descend(double currentBlocksPerTick) {
        if (!Double.isFinite(currentBlocksPerTick)) {
            throw new IllegalArgumentException("Invalid vertical velocity");
        }
        return Math.max(-37.0 / 20, currentBlocksPerTick - 130.0 / (20 * 20));
    }
}
