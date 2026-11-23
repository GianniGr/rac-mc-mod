package com.github.giannigr.racmc.client.belt;

import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import org.joml.Matrix4f;

/** Front-center Minecraft waist adaptation, not a claim about Ratchet's anatomical attachment. */
public final class Rac1BeltWaist {
    private static final float CENTER_Y = 10f / 16;
    private static final float MAX_WIDTH = 4f / 16;
    private static final float MAX_HEIGHT = 3.25f / 16;
    private static final float MAX_DEPTH = 1.8f / 16;
    private static final float SURFACE_EMBED = .08f / 16;

    public enum Clearance {
        SKIN(2f), JACKET(2.25f), LEGGINGS(2.5f), CHESTPLATE(3f);

        private final float front;
        Clearance(float front) { this.front = front; }
    }

    private Rac1BeltWaist() {}

    /** A proper rotation for the renderer's posed body (local +Y down, -Z front). */
    private static Matrix4f axes() {
        // Turn the worn model outward by 180 degrees around body up.
        // Keep its long X axis across the waist and preserve handedness.
        return new Matrix4f().scaling(-1f, -1f, 1f);
    }

    public record Fit(float x, float y, float backZ, float scale) {
        public Matrix4f local(Clearance clearance) {
            // Anchor the backmost face just inside the host surface, not away from the body.
            float hostFront = -clearance.front / 16 + SURFACE_EMBED;
            return new Matrix4f().translation(-x * scale, CENTER_Y - y * scale,
                    hostFront - backZ * scale).scale(scale).mul(axes());
        }
    }

    public static Fit fit(float[] positions) {
        if (positions.length < 9 || positions.length % 3 != 0)
            throw new IllegalArgumentException("Missing worn mesh bounds");
        float[] box = Rac1ImportedItemPose.bounds(positions);
        for (float value : box) if (!Float.isFinite(value))
            throw new IllegalArgumentException("Invalid worn mesh bounds");
        float width = box[3] - box[0], height = box[4] - box[1], depth = box[5] - box[2];
        if (!(width > 0 && height > 0 && depth > 0))
            throw new IllegalArgumentException("Degenerate worn mesh bounds");
        float scale = Math.min(MAX_WIDTH / width, Math.min(MAX_HEIGHT / height, MAX_DEPTH / depth));
        if (!Float.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Invalid worn mesh scale");
        return new Fit(-(box[0] + box[3]) / 2, -(box[1] + box[4]) / 2, box[5], scale);
    }
}
