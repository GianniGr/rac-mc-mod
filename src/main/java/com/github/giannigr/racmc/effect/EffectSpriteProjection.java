package com.github.giannigr.racmc.effect;

/** Independently authored type0 source projection geometry (512x416 reference framebuffer).
 * Adapts the original camera-tangent calculation to the current Minecraft perspective.
 * Depth+0.5, diamond corner/ST order, 1.0625 vertical correction and fixed12 fades are
 * source behavior, not a mesh import. Display-byte blending/GS overbright remain separate limits. */
public final class EffectSpriteProjection {
    public record Quad(float[] xy, float[] uv, double alpha) {}
    private EffectSpriteProjection() {}
    public static Quad project(float size, double angle, double depth, double aspect, double near, double far) {
        if (!Float.isFinite(size) || size < 0 || !Double.isFinite(angle) || !Double.isFinite(depth)
                || !Double.isFinite(aspect) || aspect <= 0 || near < 0 || far <= near) return null;
        long z12 = (long) (depth * 4096), near12 = (long) (near * 4096);
        long far12 = (long) (Math.min(500, far) * 4096);
        if (z12 <= near12 || z12 >= far12) return null;
        double radius = size / 210000.0 * depth / (depth + .5);
        double vertical = (512.0 / 416) * 1.0625 / aspect;
        double ax = Math.cos(angle) * radius, ay = -Math.sin(angle) * radius * vertical;
        double bx = -Math.sin(angle) * radius, by = -Math.cos(angle) * radius * vertical;
        // GS strips a,b,-b,-a -> MC quad b,a,-b,-a.
        float[] xy = {(float) bx, (float) by, (float) ax, (float) ay,
                (float) -bx, (float) -by, (float) -ax, (float) -ay};
        double fade = Math.min(4096, Math.min((far12 - z12) >> 4, z12 - near12)) / 4096.0;
        return new Quad(xy, new float[]{1, 0, 0, 0, 0, 1, 1, 1}, Math.max(0, fade));
    }
}
