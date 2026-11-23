package com.github.giannigr.racmc.actions;

/**
 * Read-only renderer contract. Sampling never advances simulation or emits events.
 * Sequence IDs belong to different assets: body43/child10, body26/child6.
 * Spin is about the imported wrench's local source Z (MC local Y), not world yaw.
 */
public record WrenchActionPresentation(long serial, Phase phase, int bodySequence,
        int wrenchSequence, double clipSeconds, double wrenchClipSeconds,
        double bodyBlendWeight, double wrenchBlendWeight, boolean heldVisible,
        ActionVector detachedPosition, double localSpinRadians, ActionRotation detachedRotation) {
    public WrenchActionPresentation {
        if (serial < 0 || phase == null || detachedPosition == null || detachedRotation == null
                || !Double.isFinite(clipSeconds) || clipSeconds < 0
                || !Double.isFinite(wrenchClipSeconds) || wrenchClipSeconds < 0
                || !Double.isFinite(bodyBlendWeight) || bodyBlendWeight < 0 || bodyBlendWeight > 1
                || !Double.isFinite(wrenchBlendWeight) || wrenchBlendWeight < 0
                || wrenchBlendWeight > 1
                || !Double.isFinite(localSpinRadians)) {
            throw new IllegalArgumentException("Invalid wrench presentation");
        }
    }

    public enum Phase {
        IDLE, SLAM_WINDUP, SLAM_DESCENT, RECOVERY,
        THROW_WINDUP, DETACHED_OUT, DETACHED_BACK, CATCH
    }

    public static boolean inFlight(Phase phase) {
        return phase == Phase.DETACHED_OUT || phase == Phase.DETACHED_BACK;
    }

    public static WrenchActionPresentation idle() {
        return new WrenchActionPresentation(0, Phase.IDLE, -1, 1, 0, 0, 1, 1, true,
                ActionVector.ZERO, 0, ActionRotation.IDENTITY);
    }
}
