package com.github.giannigr.racmc.client.belt;

import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import org.joml.Matrix4f;

/** Uniform item-cube fit. The display angles/scales are Minecraft adaptations. */
final class Rac1BoltGrabberPickupFit {
    private Rac1BoltGrabberPickupFit() {}

    static Matrix4f transform(float[] positions) {
        if (positions.length < 9 || positions.length % 3 != 0)
            throw new IllegalArgumentException("Missing pickup pose");
        float[] bounds = Rac1ImportedItemPose.bounds(positions);
        for (float value : bounds) if (!Float.isFinite(value))
            throw new IllegalArgumentException("Invalid pickup bounds");
        float dx = bounds[3] - bounds[0], dy = bounds[4] - bounds[1], dz = bounds[5] - bounds[2];
        double size = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        float scale = (float) (.82 / size);
        if (!Float.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Degenerate pickup bounds");
        return new Matrix4f().translation(.5f, .5f, .5f).scale(scale)
                .translate(-(bounds[0] + bounds[3]) / 2, -(bounds[1] + bounds[4]) / 2,
                        -(bounds[2] + bounds[5]) / 2);
    }
}
