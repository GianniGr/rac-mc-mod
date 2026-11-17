package com.github.giannigr.racmc.loot;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.function.DoubleSupplier;

/** Independent, asset-free NTSC 60 Hz translation. Velocities are units per virtual frame. */
public final class Rac1BoltMotion {
    private Rac1BoltMotion() {}
    public static final int SUBSTEPS = 3;
    public enum Phase { FALLING, SETTLING, IDLE, ATTRACTING }
    public record V(double x, double y, double z) {
        public static final V ZERO = new V(0, 0, 0), UP = new V(0, 1, 0);
        public V add(V b) { return new V(x + b.x, y + b.y, z + b.z); }
        public V sub(V b) { return new V(x - b.x, y - b.y, z - b.z); }
        public V mul(double k) { return new V(x * k, y * k, z * k); }
        public double dot(V b) { return x * b.x + y * b.y + z * b.z; }
        public double length() { return Math.sqrt(dot(this)); }
        public V unit() { double n = length(); return n == 0 ? ZERO : mul(1 / n); }
        public V lerp(V b, double t) { return add(b.sub(this).mul(t)); }
    }
    public record State(V position, V velocity, Phase phase, double startY, V rest, V up,
                        int total, int remaining, int settleFrames, int idleFrames,
                        double spin, double secondary, int direction, double flySpeed, UUID target) {}
    public record Contact(V point, V separation, V normal) {}
    public record Movement(V position, List<Contact> contacts) {}
    @FunctionalInterface public interface Collision {
        Movement move(V from, V displacement, double radius);
    }
    /** Candidate chosen by the server; foot position is used for range, body for homing. */
    public record Hero(UUID id, V feet, V body) {}
    public record Step(State state, boolean collect) {}

    public static double radius(int value) {
        return switch (value) { case 1 -> .15; case 5, 20 -> .23; case 50 -> .25;
            default -> throw new IllegalArgumentException("Invalid bolt value"); };
    }
    public static State launch(V source, RandomGenerator random) {
        State s = launch(source, random.nextDouble() * Math.PI * 2, random.nextDouble() * 3,
                random.nextDouble() * 3, 3.7 + random.nextDouble() * 2.3,
                30 + random.nextInt(10), 10 + random.nextInt(10),
                random.nextDouble() * Math.PI * 2, random.nextBoolean() ? 1 : -1);
        return new State(s.position, s.velocity, s.phase, s.startY, s.rest, s.up, s.total, s.remaining,
                s.settleFrames, s.idleFrames, s.spin, random.nextDouble() * Math.PI * 2,
                s.direction, s.flySpeed, s.target);
    }
    public static State launch(V source, double angle, double a, double b, double upward,
                               int settleFrames, int idleFrames, double spin, int direction) {
        V horizontal = new V(Math.cos(angle) * a, 0, -Math.sin(angle) * b);
        V position = source.add(V.UP.mul(.5)).add(horizontal.unit().mul(.5));
        return new State(position, horizontal.add(V.UP.mul(upward)).mul(1.0 / 60),
                Phase.FALLING, position.y, position, V.UP, 0, 0, settleFrames, idleFrames,
                spin, 0, direction, 0, null);
    }
    /** Legacy records had no launch state. Resume as idle, never reroll a historical reward. */
    public static State legacy(V position) {
        return new State(position, V.ZERO, Phase.IDLE, position.y, position, V.UP,
                0, 0, 30, 10, 0, 0, 1, 0, null);
    }
    public static boolean inRange(V position, V feet) {
        V d = feet.sub(position);
        return d.x * d.x + d.z * d.z < 9 && Math.abs(d.y) < 1.75;
    }
    public static V reflect(V velocity, V separation) {
        V n = separation.unit();
        double into = velocity.dot(n);
        return into < 0 ? velocity.sub(n.mul(2 * into)) : velocity;
    }
    public static boolean ground(V separation, V normal) {
        return separation.unit().y > Math.sqrt(.5) && normal.unit().y > Math.sqrt(.5);
    }
    public static double hop(int total, int remaining, double factor) {
        if (total == 0) return 0;
        double t = (double) remaining / total;
        return t * (1 - t) * total * factor;
    }
    public static V interpolate(V current, V target, int remainingTicks) {
        return current.lerp(target, 1.0 / Math.max(1, remainingTicks));
    }
    public static State relocated(State s, V position) {
        return new State(position, s.velocity, s.phase, s.startY, s.rest, s.up,
                s.total, s.remaining, s.settleFrames, s.idleFrames, s.spin, s.secondary,
                s.direction, s.flySpeed, s.target);
    }
    public static Step step(State s, int value, Collision world, Hero hero, DoubleSupplier random) {
        V p = s.position, v = s.velocity, rest = s.rest, up = s.up;
        Phase phase = s.phase;
        int total = s.total, remaining = s.remaining;
        double spin = s.spin, secondary = s.secondary, speed = s.flySpeed;
        UUID target = s.target;
        boolean collect = false;
        if (phase == Phase.FALLING) {
            v = v.add(new V(0, -10.8 / 3600, 0));
            Movement move = world.move(p, v, radius(value));
            p = move.position;
            for (Contact contact : move.contacts) {
                v = reflect(v, contact.separation);
            }
            boolean fallen = hero != null && p.y < s.startY - 2 && p.y < hero.feet.y - 2;
            if (hero != null && (fallen || (!move.contacts.isEmpty() && inRange(p, hero.feet)))) {
                phase = Phase.ATTRACTING;
                target = hero.id;
                speed = 0;
                if (!fallen) v = v.unit().mul((3 + random.getAsDouble() * 4) / 60);
            } else {
                for (Contact contact : move.contacts) {
                    if (ground(contact.separation, contact.normal)) {
                        phase = Phase.SETTLING;
                        rest = contact.point;
                        up = contact.normal.unit();
                        total = remaining = s.settleFrames;
                        break;
                    }
                }
            }
        } else if (phase == Phase.SETTLING || phase == Phase.IDLE) {
            // Idle range test precedes its visual hop. Settling has no proximity test.
            if (phase == Phase.IDLE && hero != null && inRange(p, hero.feet)) {
                phase = Phase.ATTRACTING;
                target = hero.id;
                speed = 0;
                v = up.mul((3 + random.getAsDouble() * 4) / 60);
            } else {
                spin += spinStep(value) * s.direction;
                secondary += .0125 * s.direction;
                V pose = restPose(rest, up, secondary, value);
                if (phase == Phase.SETTLING) {
                    double t = (double) remaining / total;
                    p = pose.lerp(rest.add(up.mul(.25)), t)
                            .add(V.UP.mul(hop(total, remaining, .025)));
                    if (--remaining == 0) {
                        phase = Phase.IDLE;
                        total = remaining = s.idleFrames;
                    }
                } else {
                    p = pose.add(V.UP.mul(hop(total, remaining, .0125)));
                    if (remaining > 0) remaining--;
                }
            }
        } else if (hero != null && (target == null || target.equals(hero.id))) {
            target = hero.id;
            double length = v.length(), drag = 10.0 / 3600;
            if (length > drag) v = v.mul((length - drag) / length);
            speed = Math.min(48.0 / 60, speed + 16.0 / 3600);
            p = p.add(v);
            V to = hero.body.sub(p);
            collect = to.length() <= speed;
            if (!collect) p = p.add(to.unit().mul(speed));
        } else {
            // Lost/disconnected target: retain value and resume eligibility, not a stuck lock.
            phase = Phase.IDLE;
            target = null;
            rest = p;
            up = V.UP;
            total = remaining = 0;
            speed = 0;
            v = V.ZERO;
        }
        return new Step(new State(p, v, phase, s.startY, rest, up, total, remaining,
                s.settleFrames, s.idleFrames, spin, secondary, s.direction, speed, target), collect);
    }
    private static double spinStep(int value) {
        return switch (value) { case 1 -> .04; case 5 -> .03; case 20 -> .025; default -> .0175; };
    }
    private static V restPose(V rest, V up, double spin, int value) {
        double tilt = switch (value) { case 1 -> -.25; case 5 -> -.5; case 20 -> -.4; default -> -.35; };
        double height = switch (value) { case 1 -> .04875; case 5 -> .0245; case 20 -> .05625; default -> .135; };
        double row = switch (value) { case 1 -> .325; case 5 -> .350; case 20 -> .375; default -> .300; };
        // Stable tangent basis for Minecraft voxel faces, including non-flat test surfaces.
        V tangent = Math.abs(up.y) > .9 ? new V(1, 0, 0) : new V(-up.z, 0, up.x).unit();
        V side = new V(up.y * tangent.z - up.z * tangent.y,
                up.z * tangent.x - up.x * tangent.z, up.x * tangent.y - up.y * tangent.x);
        V row0 = tangent.mul(Math.cos(spin) * Math.cos(tilt))
                .add(side.mul(Math.sin(spin) * Math.cos(tilt))).add(up.mul(-Math.sin(tilt)));
        return rest.add(up.mul(height)).add(row0.mul(row));
    }
}
