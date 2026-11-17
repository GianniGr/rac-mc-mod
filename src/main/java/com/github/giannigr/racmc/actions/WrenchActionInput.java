package com.github.giannigr.racmc.actions;

/** Target-agnostic action mapping. The crosshair is presentation, never authority to hit a target. */
public final class WrenchActionInput {
    private WrenchActionInput() {}
    public enum Target { MISS, ENTITY, BLOCK }
    public enum Route { NATIVE, SLAM, THROW }

    public static Route route(boolean available, boolean grounded, boolean crouching,
            int airborneTicks, boolean descending, double clearance, Target observedTarget) {
        if (observedTarget == null || !Double.isFinite(clearance)) {
            throw new IllegalArgumentException("Invalid input observation");
        }
        if (!available) return Route.NATIVE;
        if (crouching) return Route.THROW;
        // Original near-ground/state-age eligibility remains; no crosshair/entity requirement.
        return !grounded && airborneTicks >= 4 && clearance >= (descending ? 0.57 : 0.1)
                ? Route.SLAM : Route.NATIVE;
    }
}
