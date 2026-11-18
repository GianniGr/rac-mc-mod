package com.github.giannigr.racmc.actions;

import java.util.ArrayList;
import java.util.List;

/** Exact narrowphase tests. A broadphase box is not permission to damage everything in it. */
public final class WrenchContact {
    private WrenchContact() {}

    public record Box(ActionVector min, ActionVector max) {
        public Box {
            if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
                throw new IllegalArgumentException("Inverted box");
            }
        }
    }

    public record Segment(ActionVector from, ActionVector to) {}

    public static boolean sphereIntersects(ActionVector center, double radius, Box box) {
        if (!Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("Invalid radius");
        }
        double x = distance(center.x(), box.min.x(), box.max.x());
        double y = distance(center.y(), box.min.y(), box.max.y());
        double z = distance(center.z(), box.min.z(), box.max.z());
        return x * x + y * y + z * z <= radius * radius;
    }

    private static double distance(double value, double min, double max) {
        return Math.max(Math.max(min - value, value - max), 0);
    }

    /** Returns first intersection fraction, or positive infinity for a miss. */
    public static double intersection(Segment segment, Box box) {
        double lo = 0, hi = 1;
        double[] from = {segment.from.x(), segment.from.y(), segment.from.z()};
        ActionVector delta = segment.to.subtract(segment.from);
        double[] dir = {delta.x(), delta.y(), delta.z()};
        double[] min = {box.min.x(), box.min.y(), box.min.z()};
        double[] max = {box.max.x(), box.max.y(), box.max.z()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1.0e-12) {
                if (from[axis] < min[axis] || from[axis] > max[axis]) {
                    return Double.POSITIVE_INFINITY;
                }
            } else {
                double a = (min[axis] - from[axis]) / dir[axis];
                double b = (max[axis] - from[axis]) / dir[axis];
                lo = Math.max(lo, Math.min(a, b));
                hi = Math.min(hi, Math.max(a, b));
                if (lo > hi) return Double.POSITIVE_INFINITY;
            }
        }
        return lo;
    }

    /** Exact Euclidean swept-sphere/AABB contact; inflated-box corners are NOT the final test. */
    public static double sweptSphere(Segment segment, double radius, Box box) {
        if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Bad radius");
        double[] a = {segment.from.x(), segment.from.y(), segment.from.z()};
        ActionVector delta = segment.to.subtract(segment.from);
        double[] d = {delta.x(), delta.y(), delta.z()};
        double[] lo = {box.min.x(), box.min.y(), box.min.z()};
        double[] hi = {box.max.x(), box.max.y(), box.max.z()};
        java.util.List<Double> cuts = new java.util.ArrayList<>(java.util.List.of(0.0, 1.0));
        for (int axis = 0; axis < 3; axis++) if (Math.abs(d[axis]) > 1.0e-12) {
            for (double plane : new double[] {lo[axis], hi[axis]}) {
                double t = (plane - a[axis]) / d[axis];
                if (t > 0 && t < 1) cuts.add(t);
            }
        }
        cuts.sort(Double::compare);
        for (int part = 1; part < cuts.size(); part++) {
            double start = cuts.get(part - 1), end = cuts.get(part), middle = (start + end) / 2;
            double aa = 0, bb = 0, cc = -radius * radius;
            for (int axis = 0; axis < 3; axis++) {
                double p = a[axis] + d[axis] * middle;
                if (p >= lo[axis] && p <= hi[axis]) continue;
                double offset = a[axis] - (p < lo[axis] ? lo[axis] : hi[axis]);
                aa += d[axis] * d[axis];
                bb += d[axis] * offset;
                cc += offset * offset;
            }
            if (aa * start * start + 2 * bb * start + cc <= 1.0e-12) return start;
            double disc = bb * bb - aa * cc;
            if (aa > 1.0e-18 && disc >= 0) {
                double t = (-bb - Math.sqrt(disc)) / aa;
                if (t >= start - 1.0e-12 && t <= end + 1.0e-12) return Math.max(start, t);
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    /** Five original-style interpolated hand-to-head segments, extended by 0.17 units. */
    public static List<Segment> sweep(ActionVector oldHand, ActionVector oldHead,
            ActionVector hand, ActionVector head) {
        List<Segment> result = new ArrayList<>(5);
        for (int sample = 1; sample <= 5; sample++) {
            double fraction = sample / 5.0;
            ActionVector a = oldHand.lerp(hand, fraction);
            ActionVector b = oldHead.lerp(head, fraction);
            result.add(new Segment(a, b.add(b.subtract(a).normalized().multiply(0.17))));
        }
        return List.copyOf(result);
    }
}
