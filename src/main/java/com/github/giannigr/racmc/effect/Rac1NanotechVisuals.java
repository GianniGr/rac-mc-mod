package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.*;
import java.util.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;

/** Class806 presentation, independently authored from R09/R10 permitted facts.
 * Eight cosmetic paths, never eight pickups/HP. */
public final class Rac1NanotechVisuals {
    public record Vertex(Point position, float u, float v, int rgba) {}
    public record Group(int fx, EffectMaterial material, List<Vertex> vertices) {}
    private Rac1NanotechVisuals() {}
    private static final Point X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);
    public static double bob(double updates) { return .06 * Math.sin(updates * Math.PI / 90); }
    public static double bob(double updates, long seed) {
        return .06 * Math.sin(updates * Math.PI / 90 + new Random(seed).nextDouble() * Math.PI);
    }
    public static List<Point> orbs(Point crateBase, double updates, long seed) {
        Random random = new Random(seed);
        random.nextDouble(); // independent local initial phase draw, shared with bob()
        double yaw = random.nextDouble() * Math.PI * 2, pitch = (random.nextDouble() - .5) * Math.PI;
        Point tiltAxis = new Point(Math.cos(yaw) * Math.cos(pitch), Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch));
        double tilt = Math.PI / 4 + random.nextDouble() * Math.PI / 4;
        if (random.nextBoolean()) tilt = -tilt;
        Point[] directions = {X, Y, Z, add(X, Z), add(X, Y), sub(Y, Z), sub(X, Z), sub(X, Y)};
        Point[] axes = {Z, X, Y, cross(Y, directions[3]), cross(Z, directions[4]),
                cross(X, directions[5]), cross(Y, directions[6]), cross(Z, directions[7])};
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Point source = rotate(scale(unit(directions[i]), .25), unit(axes[i]), updates * Math.PI / 45);
            source = rotate(source, tiltAxis, tilt);
            points.add(crateBase.add(source.x(), .5 + bob(updates, seed) + source.z(), -source.y()));
        }
        return List.copyOf(points);
    }
    public static List<Point> freeOrbs(Point base, double updates, long seed) {
        double radiusScale = (.25 + .15 * Math.sin(updates * Math.PI / 90)) / .25;
        Point center = base.add(0, .5 + bob(updates, seed), 0);
        return orbs(base, updates, seed).stream().map(p ->
                add(center, scale(sub(p, center), radiusScale))).toList();
    }
    public static List<Group> geometry(NanotechTables tables, Point base, Point camera, double updates) {
        return geometry(tables, base, camera, updates, 0);
    }
    public static List<Group> geometry(NanotechTables tables, Point base, Point camera, double updates, long seed) {
        Point center = base.add(tables.offset()[0], .5 + bob(updates, seed) + tables.offset()[2], -tables.offset()[1]);
        Point forward = unit(sub(camera, center));
        Point right = unit(cross(Y, forward));
        if (right.distanceSquared(new Point(0, 0, 0)) < .5) right = X;
        Point up = cross(forward, right);
        List<Vertex> sphere = new ArrayList<>();
        for (int[] strip : new int[][]{{0, 148}, {146, 255}, {254, 290}})
            for (int at = strip[0] + 2; at < strip[1]; at++) {
                int a = at - 2, b = at - 1;
                if (((at - strip[0]) & 1) != 0) { int swap = a; a = b; b = swap; }
                for (int index : new int[]{a, b, at, a})
                    sphere.add(vertex(tables.hemisphere(), tables.st(), tables.colours(), index,
                            center, forward, right, up, 1));
            }
        List<Vertex> halo = new ArrayList<>();
        int alpha = Math.min(128, Math.max(0, (int) (bob(updates, seed) / .06 * 64 + 128)));
        for (int q = 0; q < 32; q++)
            for (int corner = 0; corner < 4; corner++) {
                int index = tables.haloQuads()[q * 4 + corner];
                Point position = transform(tables.halo(), index, center, forward, right, up,
                        corner < 2 ? .3 : .3 * 1.125);
                halo.add(new Vertex(position, .5f, corner < 2 ? .5f : 1,
                        EffectMaterial.rgba(corner < 2 ? alpha << 24 | 0x802020 : 0)));
            }
        List<Vertex> glass = new ArrayList<>();
        for (int q = 0; q < 4; q++)
            for (int corner = 0; corner < 4; corner++) {
                int index = tables.glassQuads()[q * 4 + corner] * 3;
                Point position = new Point(center.x() + tables.glass()[index],
                        base.y() + tables.glass()[index + 2], center.z() - tables.glass()[index + 1]);
                Point view = scale(unit(sub(position, camera)), 2);
                glass.add(new Vertex(position, wrap(view.x() * .5 + .5), wrap(view.y() * .5 + .5),
                        EffectMaterial.rgba(0x107f7f7f)));
            }
        return List.of(new Group(21, EffectMaterial.NORMAL, List.copyOf(sphere)),
                new Group(11, EffectMaterial.ADDITIVE, quadOrder(halo)),
                new Group(21, EffectMaterial.NORMAL_REPEAT, quadOrder(glass)));
    }
    /** R10 cubic fan, moving chest target. Canonical60Hz MC timing, not exact PAL nested ticks. */
    public static List<Point> attracting(Point base, Point target, double age, long seed) {
        List<Point> result = new ArrayList<>(8);
        var initial = orbs(base, 0, seed);
        double yaw = new Random(seed).nextDouble() * Math.PI * 2;
        for (int orb = 0; orb < 8; orb++) {
            double t = age / com.github.giannigr.racmc.loot.Rac1NanotechRules.duration(seed, orb);
            if (t >= 1) { result.add(null); continue; }
            t = Math.max(0, t);
            double angle = yaw + orb * .44854963;
            Point a = initial.get(orb), b = a.add(Math.cos(angle) * 5, 1, Math.sin(angle) * 5);
            Point c = target.add(Math.cos(angle) * 2, .5, Math.sin(angle) * 2);
            double u = 1 - t;
            result.add(new Point(u*u*u*a.x() + 3*u*u*t*b.x() + 3*u*t*t*c.x() + t*t*t*target.x(),
                    u*u*u*a.y() + 3*u*u*t*b.y() + 3*u*t*t*c.y() + t*t*t*target.y(),
                    u*u*u*a.z() + 3*u*u*t*b.z() + 3*u*t*t*c.z() + t*t*t*target.z()));
        }
        return Collections.unmodifiableList(result);
    }
    private static List<Vertex> quadOrder(List<Vertex> source) {
        List<Vertex> result = new ArrayList<>(source.size());
        // Original GS strip triangles 0,1,2 and 1,2,3 -> MC quad 1,0,2,3.
        for (int at = 0; at < source.size(); at += 4)
            for (int corner : new int[]{1, 0, 2, 3}) result.add(source.get(at + corner));
        return List.copyOf(result);
    }
    private static float wrap(double value) { return (float) (value - Math.floor(value / 8) * 8); }
    private static Vertex vertex(float[] positions, float[] st, int[] colours, int index,
                                 Point center, Point f, Point r, Point u, double scale) {
        return new Vertex(transform(positions, index, center, f, r, u, scale),
                st[index * 2], st[index * 2 + 1], EffectMaterial.rgba(colours[index]));
    }
    private static Point transform(float[] values, int index, Point p, Point f, Point r, Point u, double s) {
        int at = index * 3;
        return p.add((f.x() * values[at] + r.x() * values[at + 1] + u.x() * values[at + 2]) * s,
                (f.y() * values[at] + r.y() * values[at + 1] + u.y() * values[at + 2]) * s,
                (f.z() * values[at] + r.z() * values[at + 1] + u.z() * values[at + 2]) * s);
    }
    private static Point add(Point a, Point b) { return a.add(b.x(), b.y(), b.z()); }
    private static Point sub(Point a, Point b) { return a.add(-b.x(), -b.y(), -b.z()); }
    private static Point scale(Point a, double s) { return new Point(a.x() * s, a.y() * s, a.z() * s); }
    private static Point unit(Point a) {
        double length = Math.sqrt(a.distanceSquared(new Point(0, 0, 0)));
        return length < 1.0e-9 ? new Point(0, 0, 0) : scale(a, 1 / length);
    }
    private static Point cross(Point a, Point b) {
        return new Point(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }
    private static Point rotate(Point p, Point axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle), d = p.x() * axis.x() + p.y() * axis.y() + p.z() * axis.z();
        Point cross = cross(axis, p);
        return new Point(p.x() * c + cross.x() * s + axis.x() * d * (1 - c),
                p.y() * c + cross.y() * s + axis.y() * d * (1 - c),
                p.z() * c + cross.z() * s + axis.z() * d * (1 - c));
    }
}
