package com.github.giannigr.racmc.loot;

import java.util.Random;

/** RaC1 one HP/cluster; fixed Minecraft balance adaptation: 5 HP (2.5 hearts), not XP. */
public final class Rac1NanotechRules {
    public static final float HEAL = 5;
    public static final int ORBS = 8;
    private Rac1NanotechRules() {}
    public static boolean eligible(float health, float max, int incoming) {
        return Float.isFinite(health) && Float.isFinite(max) && health > 0 && max > 0
                && incoming >= 0 && health + HEAL * incoming < max;
    }
    public static float arrivalHealth(float health, float max) {
        if (!Float.isFinite(health) || !Float.isFinite(max) || health <= 0 || max <= 0)
            throw new IllegalArgumentException("Invalid living health");
        return Math.min(max, health + HEAL);
    }
    /** Inclusive20..80 canonical60Hz units is an explicit MC timing definition, not PAL RNG replication. */
    public static int duration(long seed, int orb) {
        if (orb < 0 || orb >= ORBS) throw new IllegalArgumentException("Invalid orb");
        Random random = new Random(seed);
        int duration = 20;
        for (int i = 0; i <= orb; i++) duration = 20 + random.nextInt(61);
        return duration;
    }
    public static int firstArrival(long seed) {
        int min = 80;
        for (int i = 0; i < ORBS; i++) min = Math.min(min, duration(seed, i));
        return (min + 2) / 3;
    }
    public static boolean inRange(double squaredDistance) {
        return Double.isFinite(squaredDistance) && squaredDistance >= 0 && squaredDistance < 100;
    }
}
