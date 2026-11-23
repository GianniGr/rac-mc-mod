package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
/** Original §9.3 bone-triplet inset math; no invented fixed screen rectangles or camera. */
public final class Rac1ShopLayout {
    private Rac1ShopLayout() {}
    private static final double[][] MARGINS = {
            {.01, .01, .009, .01}, {.01, .03, .03, .03}, {.03, 0, 0, 0},
            {.02, .06, .04, .035}, {.07, .05, .03, .03}, {.03, .06, .02, .03}
    };
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Nonfinite monitor point");
        }
        Point plus(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        Point minus(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        Point times(double s) { return new Point(x * s, y * s, z * s); }
        double length() { return Math.sqrt(x*x + y*y + z*z); }
    }
    public record Quad(Point corner, Point edgeA, Point edgeB) {
        public Point opposite() { return corner.plus(edgeA).plus(edgeB); }
    }

    /** Low-level flattened point-list palette, not the raw vendor skeleton joint palette. */
    public static Quad monitor(float[] jointPose, int screen) {
        return monitor(jointPose, screen, null);
    }
    public static Quad monitor(float[] jointPose, int screen, float[] importedMargins) {
        if (screen < 0 || screen >= 6 || jointPose.length % 16 != 0 || jointPose.length < (screen * 4 + 3) * 16)
            throw new IllegalArgumentException("Missing original monitor bone triplet");
        Point p0 = joint(jointPose, screen * 4), p1 = joint(jointPose, screen * 4 + 1);
        Point p2 = joint(jointPose, screen * 4 + 2);
        Point a = p1.minus(p0), b = p2.minus(p0);
        double al = a.length(), bl = b.length();
        double[] m = MARGINS[screen];
        if (importedMargins != null) {
            if (importedMargins.length != 24) throw new IllegalArgumentException("Invalid imported monitor margins");
            m = new double[4];
            for (int i = 0; i < 4; i++) {
                m[i] = importedMargins[screen * 4 + i];
                if (!Double.isFinite(m[i]) || m[i] < 0 || m[i] > .25)
                    throw new IllegalArgumentException("Invalid imported monitor margin");
            }
        }
        if (al <= 2 * m[2] || bl <= 2 * m[3] || al == 0 || bl == 0)
            throw new IllegalArgumentException("Degenerate original monitor edges");
        Point unitA = a.times(1 / al), unitB = b.times(1 / bl);
        return new Quad(p0.plus(unitA.times(m[0])).plus(unitB.times(m[1])),
                unitA.times(al - 2 * m[2]), unitB.times(bl - 2 * m[3]));
    }
    /** Original MobyGetBoneMatrix consumes joint-list IDs. Resolve first-list chain endpoints. */
    public static Quad monitorVendor(Rac1AnimatedAsset body, float[] chainPose, int screen, float[] importedMargins) {
        if (body.classNumber() != 11 || screen < 0 || screen >= 6 || chainPose.length != body.skeleton().jointCount() * 16)
            throw new IllegalArgumentException("Invalid original vendor monitor pose");
        float[] points = new float[24 * 16];
        for (int i = 0; i < 3; i++) {
            int list = screen * 4 + i;
            int[] chain = body.attachmentChain(list);
            if (chain.length == 0) throw new IllegalArgumentException("Missing original monitor joint list " + list);
            int joint = chain[chain.length - 1];
            if (joint < 0 || joint >= body.skeleton().jointCount())
                throw new IllegalArgumentException("Invalid original monitor chain endpoint");
            System.arraycopy(chainPose,joint * 16,points,list * 16,16);
        }
        return monitor(points,screen,importedMargins);
    }

    public record Rectangle(double x, double y, double width, double height) {
        public Rectangle {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                    || !Double.isFinite(height) || width < 0 || height < 0)
                throw new IllegalArgumentException("Invalid projected monitor rectangle");
        }
        public Rectangle power(double fraction) {
            if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1)
                throw new IllegalArgumentException("Invalid CRT power fraction");
            return new Rectangle(x + width * (1 - fraction) / 2,
                    y + height * (1 - fraction) / 2, width * fraction, height * fraction);
        }
    }
    private static Point joint(float[] pose, int joint) {
        int at = joint * 16;
        return new Point(pose[at + 12], pose[at + 13], pose[at + 14]);
    }
}
