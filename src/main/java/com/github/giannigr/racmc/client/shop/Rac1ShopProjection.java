package com.github.giannigr.racmc.client.shop;

import org.joml.Matrix4fc;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Original corner/opposite-corner projection and camera source facts; no chosen MC camera policy. */
public final class Rac1ShopProjection {
    private Rac1ShopProjection() {}
    /** Original local eye(3.8,0,1.5), level facing negativeX, after (x,z,-y) conversion. */
    public static Vector3f originalLocalEye() { return new Vector3f(3.8f, 1.5f, 0); }
    public static Vector3f originalLocalForward() { return new Vector3f(-1, 0, 0); }
    public static final int TARGET_WIDTH = 512, TARGET_HEIGHT = 128;

    /** Caller supplies the measured/approved world-to-clip matrix. No guessed FOV/letterboxing. */
    public static Rac1ShopLayout.Rectangle project(Rac1ShopLayout.Quad quad, Matrix4fc toClip, int width, int height) {
        if (width < 1 || height < 1 || width > 16384 || height > 16384)
            throw new IllegalArgumentException("Invalid scene viewport");
        Vector2d first = pixel(quad.corner(), toClip, width, height);
        Vector2d last = pixel(quad.opposite(), toClip, width, height);
        return new Rac1ShopLayout.Rectangle(Math.min(first.x, last.x), Math.min(first.y, last.y),
                Math.abs(last.x - first.x), Math.abs(last.y - first.y));
    }
    private static Vector2d pixel(Rac1ShopLayout.Point point, Matrix4fc matrix, int width, int height) {
        Vector4f p = matrix.transform(new Vector4f((float)point.x(), (float)point.y(), (float)point.z(), 1));
        if (!Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z) || !Float.isFinite(p.w) || p.w <= 0)
            throw new IllegalArgumentException("Original monitor projection is nonfinite/behind camera");
        return new Vector2d((p.x / p.w + 1) * width / 2.0, (1 - p.y / p.w) * height / 2.0);
    }
}
