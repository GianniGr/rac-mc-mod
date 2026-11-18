package com.github.giannigr.racmc.actions;

/** Immutable world orientation of the cosmetic attachment, not an original joint/pose array. */
public record ActionRotation(float x, float y, float z, float w) {
    public static final ActionRotation IDENTITY = new ActionRotation(0, 0, 0, 1);
    public ActionRotation {
        double norm = x * x + y * y + z * z + w * w;
        if (!Double.isFinite(norm) || Math.abs(norm - 1) > 0.01) {
            throw new IllegalArgumentException("Non-unit attachment orientation");
        }
    }
}
