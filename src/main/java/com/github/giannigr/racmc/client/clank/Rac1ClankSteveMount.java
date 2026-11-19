package com.github.giannigr.racmc.client.clank;

import org.joml.Matrix4f;

/** Fixed MC host adapter, not original Ratchet joint5. Consumes an established native body frame. */
public final class Rac1ClankSteveMount {
    public static final float LOCAL_SCALE = 16f / 15f;
    private Rac1ClankSteveMount() {}
    public static Matrix4f axisRotation() {
        // Columns. Proper B rotation imported(x,y,z) -> (-z,-y,-x), determinant +1.
        return new Matrix4f().set(0, 0, -1, 0, 0, -1, 0, 0,
                -1, 0, 0, 0, 0, 0, 0, 1);
    }
    public static Matrix4f local() {
        return new Matrix4f().translation(0, 6f / 16, 2f / 16)
                .scale(LOCAL_SCALE).mul(axisRotation());
    }
}
