package com.github.giannigr.racmc.actions;

/** Original-style downward acceleration expressed in Minecraft blocks/tick at 20TPS. */
public final class WrenchSlamMotion {
    private WrenchSlamMotion() {}

    /** 130 world units/s², capped at -37 units/s. Collision and fall damage remain vanilla-owned. */
    public static double descend(double currentBlocksPerTick) {
        if (!Double.isFinite(currentBlocksPerTick)) {
            throw new IllegalArgumentException("Invalid vertical velocity");
        }
        return Math.max(-37.0 / 20, currentBlocksPerTick - 130.0 / (20 * 20));
    }
}
