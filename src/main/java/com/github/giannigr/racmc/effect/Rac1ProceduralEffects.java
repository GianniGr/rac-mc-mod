package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.*;
import java.util.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;
import static com.github.giannigr.racmc.effect.EffectRuntime.*;

/** Independently authored crate type11 + static debris controllers from permitted behavior facts.
 * All objects are cosmetic CPU state, not MC entities. Flags not used by crate type11 are excluded.
 * Original frame-load inputs are replaced explicitly by a bounded visual-pool pressure proxy. */
public final class Rac1ProceduralEffects {
    public record Hit(Point position, Point normal) {}
    public interface Collision { Hit sweep(Point old, Point next, double radius); }
    public static final Collision NO_COLLISION = (old, next, radius) -> null;
    private static final Point ZERO = new Point(0, 0, 0);
    private final EffectRuntime runtime;
    private final String sha1;
    private final Rac1BlastTables tables;
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();
    private Point camera = ZERO;
    private Collision collision = NO_COLLISION;
    private static final class Spark {
        long handle; UUID owner; Point position, velocity; Random random;
        int timer, length, generation, c1, c2, t1; boolean smoke, normal;
        double childSpeed, rotation; float size;
    }
    private static final class Debris {
        long handle; Point position, velocity, angles, spin;
        Random random; double birthY; int age, bounces, alpha = 128;
        float size; boolean fade;
    }
    public Rac1ProceduralEffects(EffectRuntime runtime, String sha1, Rac1BlastTables tables) {
        this.runtime = runtime; this.sha1 = sha1; this.tables = tables;
    }
    public void context(Point camera, Collision collision) {
        this.camera = camera; this.collision = collision;
    }
    public int sparks() { return sparks.size(); }
    public int debris() { return debris.size(); }
    public void clear() {
        sparks.forEach(spark -> runtime.stop(spark.handle)); sparks.clear();
        debris.forEach(piece -> runtime.stop(piece.handle)); debris.clear();
    }
    public void blast(UUID owner, Point origin, Point camera, long seed, boolean suppressed) {
        Random random = new Random(seed);
        double distance = Math.sqrt(origin.distanceSquared(camera));
        int count = distance < 8 ? (int) distance + 2 : 10;
        if (suppressed) count = Math.min(6, count);
        for (int i = 0; i < count; i++)
            spark(owner, origin.add(0, .5, 0), ZERO, (8 + random.nextDouble() * 2 - Math.max(0, 7 - distance))
                            / runtime.profile().hz(), runtime.profile().ticks(15 + random.nextInt(6)),
                    runtime.profile().ticks(25 + random.nextInt(6)), 0,
                    tables.sparkA()[random.nextInt(6)], tables.sparkB()[random.nextInt(6)], random, sparks);
        debris(owner, origin, camera, seed ^ 0x112L, 363);
    }
    public void debris(UUID owner, Point origin, Point camera, long seed, int classId) {
        if (classId != 348 && classId != 357 && classId != 363) return;
        Random random = new Random(seed);
        int free = runtime.remaining();
        int n = free > 200 ? 4 : free > 100 ? 2 : 1;
        int chunks = free > 200 ? 4 : free > 150 ? 2 : free > 100 ? 1 : 0;
        double a = Math.atan2(camera.x() - origin.x(), -(camera.z() - origin.z()));
        double angle = (int) ((a + Math.PI) / 1.57) * 1.57;
        angle = Math.IEEEremainder(angle, Math.PI * 2);
        Point lastAngles = ZERO;
        for (int i = 0; i < n; i++) {
            int at = i * 3;
            double x = tables.debrisOffsets()[at], y = tables.debrisOffsets()[at + 1];
            double dx = x * Math.cos(angle) - y * Math.sin(angle), dz = -(x * Math.sin(angle) + y * Math.cos(angle));
            Point position = origin.add(dx, tables.debrisOffsets()[at + 2], dz);
            double horizontal = Math.hypot(dx, dz), speed = (1 + random.nextDouble() * 4) / runtime.profile().hz();
            Point velocity = new Point(horizontal == 0 ? 0 : dx / horizontal * speed,
                    (4 + random.nextDouble() * 5) / runtime.profile().hz(),
                    horizontal == 0 ? 0 : dz / horizontal * speed);
            lastAngles = new Point(tables.debrisRotations()[at], tables.debrisRotations()[at + 2] + angle,
                    -tables.debrisRotations()[at + 1]);
            piece(owner, classId, position, velocity, lastAngles, 1, random.nextInt(2), random);
        }
        for (int i = 0; i < chunks; i++) {
            Point velocity = randomUnit(random);
            double speed = (6 + random.nextDouble() * 6) / runtime.profile().hz();
            velocity = new Point(velocity.x() * speed, Math.abs(velocity.y()) * speed, velocity.z() * speed);
            Point position = origin.add(random.nextDouble() - .5, random.nextDouble(), random.nextDouble() - .5);
            Point angles = new Point(random.nextDouble() * Math.PI * 2, random.nextDouble() * Math.PI * 2,
                    lastAngles.z());
            int bounces = random.nextInt(6) == 0 ? 3 + random.nextInt(3) : 1;
            piece(owner, classId + 1, position, velocity, angles, .25f + random.nextFloat() * .25f, bounces, random);
        }
    }
    private void piece(UUID owner, int id, Point position, Point velocity, Point angles,
                       float size, int bounces, Random random) {
        if (debris.size() >= 128) return;
        long handle = runtime.spawn(new Spawn(owner, new EffectAssetRef(sha1, 1, EffectAssetRef.Bank.MOBY, id),
                Geometry.MESH, Program.OWNER_MESH, position, ZERO, size, 1, angles, 0, 0x80808080,
                600, 0, EffectMaterial.NORMAL_REPEAT));
        if (handle == 0) return;
        Debris piece = new Debris();
        piece.handle = handle; piece.position = position; piece.velocity = velocity; piece.angles = angles;
        piece.size = size; piece.bounces = bounces; piece.birthY = position.y(); piece.random = random;
        piece.spin = spin(random); debris.add(piece);
    }
    private Point spin(Random random) {
        double factor = Math.PI * 2 / runtime.profile().hz();
        return new Point(0, (random.nextDouble() * 2 - 1) * factor, (random.nextDouble() * 2 - 1) * factor);
    }
    private void spark(UUID owner, Point position, Point baseVelocity, double speed, int life, int t1,
                       int generation, int c1, int c2, Random random, List<Spark> destination) {
        if (sparks.size() + (destination == sparks ? 0 : destination.size()) >= 512
                || runtime.remaining() < 1 || life < 1) return;
        double load = runtime.size() / (double) (runtime.size() + runtime.remaining());
        if ((load > .85 && random.nextInt(3) == 0) || (load > .9 && random.nextInt(2) == 0)) return;
        boolean normal = random.nextInt(100) < 20;
        double rotation = random.nextDouble() * Math.PI * 2;
        long handle = runtime.spawn(new Spawn(owner, new EffectAssetRef(sha1, 1, EffectAssetRef.Bank.PARTICLE, 0),
                Geometry.BILLBOARD, Program.OWNER_SPRITE, position, ZERO, 1, 1, new Point(0, 0, rotation),
                0, c1, 600, 0, normal ? EffectMaterial.NORMAL : EffectMaterial.ADDITIVE));
        if (handle == 0) return;
        Spark spark = new Spark();
        runtime.bounds(handle, 1, 320);
        spark.owner = owner; spark.handle = handle; spark.position = position; spark.random = random;
        Point direction = randomUnit(random);
        spark.velocity = baseVelocity.add(direction.x() * speed, direction.y() * speed, direction.z() * speed);
        spark.timer = spark.length = Math.max(1, life); spark.t1 = t1; spark.generation = generation;
        spark.c1 = c1; spark.c2 = c2; spark.childSpeed = speed * .75; spark.normal = normal; spark.rotation = rotation;
        destination.add(spark);
    }
    public void step() {
        double dt2 = 1.0 / runtime.profile().hz() / runtime.profile().hz();
        Iterator<Debris> pieces = debris.iterator();
        while (pieces.hasNext()) {
            Debris piece = pieces.next();
            if (!runtime.contains(piece.handle)) { pieces.remove(); continue; }
            piece.age++;
            piece.velocity = piece.velocity.add(0, -20 * dt2, 0);
            Point next = piece.position.add(piece.velocity.x(), piece.velocity.y(), piece.velocity.z());
            if (!piece.fade && next.y() < piece.birthY - 3) piece.fade = true;
            if (!piece.fade && piece.age > runtime.profile().ticks(20) && piece.bounces > 0) {
                Hit hit = collision.sweep(piece.position, next, piece.size / 4);
                if (hit != null) {
                    Point normal = hit.normal();
                    double dot = dot(piece.velocity, normal);
                    if (dot < 0) {
                        next = hit.position();
                        piece.velocity = scale(piece.velocity.add(-2 * dot * normal.x(),
                                -2 * dot * normal.y(), -2 * dot * normal.z()), .75);
                        piece.spin = spin(piece.random); piece.bounces--;
                    }
                }
            }
            piece.position = next;
            piece.angles = piece.angles.add(piece.spin.x(), piece.spin.y(), piece.spin.z());
            if (piece.fade) { piece.size *= .9f; piece.alpha -= 4; }
            if (piece.alpha < 4) { runtime.stop(piece.handle); pieces.remove(); continue; }
            runtime.pose(piece.handle, piece.position, piece.size, piece.angles, piece.alpha << 24 | 0x808080);
        }
        List<Spark> children = new ArrayList<>();
        Iterator<Spark> iterator = sparks.iterator();
        while (iterator.hasNext()) {
            Spark spark = iterator.next();
            if (!runtime.contains(spark.handle)) { iterator.remove(); continue; }
            spark.rotation += Math.PI * 2 / 256;
            int colour;
            if (spark.smoke) {
                spark.velocity = spark.velocity.add(0, 3 * dt2, 0);
                spark.position = spark.position.add(spark.velocity.x(), spark.velocity.y(), spark.velocity.z());
                spark.velocity = scale(spark.velocity, .98);
                int start = (int) (spark.length * .75) + 1, span = (int) (spark.length * .25) + 1;
                if (spark.timer > start) colour = tween(spark.c2, spark.c1, (spark.timer - start) / (double) span);
                else if (!spark.normal) { runtime.stop(spark.handle); iterator.remove(); continue; }
                else colour = (spark.c2 & 0xffffff) | (15 * spark.timer / start) << 24;
            } else {
                spark.position = spark.position.add(spark.velocity.x(), spark.velocity.y(), spark.velocity.z());
                spark.velocity = scale(spark.velocity, .8);
                if (spark.timer == (int) (spark.length * .85) && spark.generation < 2)
                    for (int i = 0; i < 5; i++)
                        spark(spark.owner, spark.position, spark.velocity,
                                spark.childSpeed * (.85 + spark.random.nextDouble() * .15),
                                (int) (spark.length * (.9 + spark.random.nextDouble() * .2)),
                                spark.t1, spark.generation + 1, spark.c1, spark.c2, spark.random, children);
                spark.size = (float) curve(400000, spark.length, spark.timer);
                double distance = Math.sqrt(spark.position.distanceSquared(camera));
                if (distance < 10) spark.size *= (float) (distance * .075 + .25);
                colour = tween(spark.c2, spark.c1, spark.timer / (double) spark.length);
                spark.t1 = Math.max(0, spark.t1 - 1);
            }
            runtime.pose(spark.handle, spark.position, spark.size, new Point(0, 0, spark.rotation), colour);
            if (--spark.timer <= 0) {
                if (spark.smoke) { runtime.stop(spark.handle); iterator.remove(); }
                else {
                    spark.smoke = true; spark.c1 = spark.c2; spark.c2 = spark.normal ? 0x0f2f3f3f : 0;
                    spark.timer = spark.length = spark.length * 2 + spark.random.nextInt(spark.length + 1);
                }
            }
        }
        sparks.addAll(children);
    }
    public static double curve(double size, int length, int timer) {
        double b = (8 * length - 3 * length * size) / (8 * size * size + 8 * size);
        double a = (3 * length + 8 * b * size) / (-8 * size);
        double discriminant = Math.max(0, b * b - 4 * a * (length - timer));
        return Math.max(0, Math.max((-b - Math.sqrt(discriminant)) / (2 * a),
                (-b + Math.sqrt(discriminant)) / (2 * a)));
    }
    private static int tween(int a, int b, double t) {
        int value = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int x = a >>> shift & 255, y = b >>> shift & 255;
            value |= Math.max(0, Math.min(255, (int) (x + (y - x) * t))) << shift;
        }
        return value;
    }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Point scale(Point a, double s) { return new Point(a.x() * s, a.y() * s, a.z() * s); }
    private static Point randomUnit(Random random) {
        Point value = new Point(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
        double length = Math.sqrt(dot(value, value));
        return length < 1.0e-9 ? new Point(0, 1, 0) : scale(value, 1 / length);
    }
}
