package com.github.giannigr.racmc.effect;

import java.util.UUID;

/** Validated semantic envelope only: never contains asset bytes, file paths or audio IDs.
 * Lifecycle UUID includes spawn generation; numeric entity IDs alone are insufficient. */
public record EffectSignal(Semantic semantic, String dimension, UUID lifecycle, long sequence,
                           long serverTick, long seed, Op op, Point anchor, Impact impact) {
    public enum Semantic { CRATE_TNT_BLAST, BLASTER_SHOT, BLASTER_IMPACT, BLASTER_END }
    public enum Op { START, STOP }
    public record Impact(Point tangent, Point normal) {
        public Impact {
            if (tangent == null || normal == null)
                throw new IllegalArgumentException("Missing impact vectors");
            Point zero = new Point(0, 0, 0);
            double length = normal.distanceSquared(zero);
            if (length < .99 || length > 1.01 || tangent.distanceSquared(zero) > 256)
                throw new IllegalArgumentException("Invalid impact normal/tangent");
        }
    }
    public EffectSignal(Semantic semantic, String dimension, UUID lifecycle, long sequence,
                        long serverTick, long seed, Op op, Point anchor) {
        this(semantic, dimension, lifecycle, sequence, serverTick, seed, op, anchor, null);
    }
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000 || Math.abs(y) > 30_000_000 || Math.abs(z) > 30_000_000)
                throw new IllegalArgumentException("Invalid effect anchor");
        }
        public Point add(double dx, double dy, double dz) { return new Point(x + dx, y + dy, z + dz); }
        public double distanceSquared(Point point) {
            double dx = x - point.x, dy = y - point.y, dz = z - point.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public EffectSignal {
        if (semantic == null || dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                || dimension.length() > 128 || lifecycle == null || sequence < 0 || serverTick < 0
                || op == null || anchor == null
                || ((semantic == Semantic.BLASTER_IMPACT && op == Op.START) != (impact != null)))
            throw new IllegalArgumentException("Invalid effect signal");
    }
}
