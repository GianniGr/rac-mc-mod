package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.github.giannigr.racmc.item.weapon.bomb.BombGlovePose;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Phase;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.VisualPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Standing Novalis throw landmarks adapted to one Steve arm, not retail key playback. */
final class BombGloveStevePose {
    private static final float PIXEL = 1f / 16;
    private static final float PALM_SIDE = .022f;
    static final float WRIST_Y = 10;
    private static final float CUFF_OVERHANG = 1.75f;
    // The standing route spends roughly the first half entering the loaded pose.
    // Retimed to Minecraft's existing commit marker; these are Steve parameters.
    private static final double SET = .54;
    private BombGloveStevePose() {}
    record Motion(double back, double follow, double toRelease) {
        double stance() { return Math.max(toRelease, Math.max(back, follow)); }
        boolean idle() { return stance() == 0; }
    }

    static Motion motion(VisualPhase phase, long tick, float partial) {
        if (phase == null) return new Motion(0, 0, 0);
        double elapsed = Math.max(0, (double) tick - phase.tick() + Math.clamp(partial, 0, 1));
        if (phase.phase() == Phase.WINDUP) {
            // The native use callback's elapsed count begins at zero. Reach the palm
            // by its earliest release marker; only a committed update starts recovery.
            double t = Math.clamp(elapsed / (BombGlovePose.WINDUP_TICKS - 1), 0, 1);
            double swing = smooth((t - SET) / (1 - SET));
            double back = t < SET ? smooth(t / SET) : 1 - swing;
            return new Motion(back, 0, swing);
        }
        if (phase.phase() == Phase.RELEASED) {
            double t = Math.clamp(elapsed / 6, 0, 1);
            return new Motion(0, t == 1 ? 0 : Math.sin(Math.PI * t), 1 - smooth(t));
        }
        if (phase.phase() == Phase.ABORTED) {
            return new Motion(.45 * (1 - smooth(elapsed / 3)), 0, 0);
        }
        return new Motion(0, 0, 0);
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Explicit proper frame. Local Y points to fingers; projected right fixes twist. */
    static Quaternionf frame(Vec3 longitudinal, Vec3 right) {
        Vec3 y = longitudinal.normalize();
        Vec3 x = right.subtract(y.scale(right.dot(y))).normalize();
        Vec3 z = x.cross(y);
        return new Matrix3f().setColumn(0, vector(x)).setColumn(1, vector(y)).setColumn(2, vector(z))
                .getNormalizedRotation(new Quaternionf());
    }

    /** World rig only: X outward on the selected side, Y up, Z forward. */
    static Vec3 castOffset(Motion m) {
        // Load back and low; drive along the outside; after commit the hand
        // continues forward, lifts and crosses toward the body's center.
        var outside = 4 * m.back * m.toRelease;
        return new Vec3(.065 * m.back + .115 * outside - .24 * m.follow,
                -.065 * m.back + .24 * m.follow, -.27 * m.back + .14 * m.follow);
    }

    static Quaternionf roll(Quaternionf arm, boolean right, Motion m) {
        // The source wrist turns from lateral to upward in recovery. Keep a
        // smaller twist on Steve so the palm remains readable around class 121.
        double outward = .48 * m.back + .18 * m.toRelease - .18 * m.follow;
        return arm.rotateY((float) ((right ? 1 : -1) * outward));
    }

    static Vec3 cameraLocal(Vec3 worldOffset, Quaternionf cameraToWorld) {
        var p = new Quaternionf(cameraToWorld).conjugate().transform(vector(worldOffset));
        return new Vec3(p.x, p.y, p.z);
    }

    static Vector3f vector(Vec3 p) { return new Vector3f((float) p.x, (float) p.y, (float) p.z); }

    static float shellClearance(float[] vertices) {
        double radiusSquared = 0;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            double x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
            radiusSquared = Math.max(radiusSquared, x * x + y * y + z * z);
        }
        return (float) Math.sqrt(radiusSquared) + .003f;
    }

    /**
     * Arm-frame offset from the class-121 center. +Y is wrist-to-fingers, +Z is
     * dorsal; shift the shell dorsally, not the held/committed bomb center.
     */
    static Vector3f armOrigin(float centerPixels, float clearance) {
        return new Vector3f(-centerPixels * PIXEL, -WRIST_Y * PIXEL - clearance, PALM_SIDE);
    }

    /**
     * Owner-observed cable/wrist end is at imported -Z, fingertip end at +Z.
     * Map source (X,Y,Z) to arm (-X,+Z,+Y): proper rotation, not an extra reflection.
     * Imported +Y keeps the dorsal cross-section facing arm +Z independently of length.
     */
    static Matrix4f fittedShell(BombGloveWornDrawing.Bounds b, BombGloveCuff.ArmFit fit, float cuff,
                                boolean right) {
        return new Matrix4f().translate(fit.centerX() * PIXEL, cuff * PIXEL, 0)
                .scale(shellWidth(fit) * PIXEL / b.width(),
                        (WRIST_Y - cuff) * PIXEL / b.length(), shellDepth() * PIXEL / b.depth())
                .rotateY((float) Math.PI)
                .rotateX((float) (-Math.PI / 2))
                .translate(right ? -b.centerX() : b.centerX(), -b.centerY(), -b.minZ());
    }

    static float shellWidth(BombGloveCuff.ArmFit fit) { return fit.width + .5f + CUFF_OVERHANG; }
    static float shellDepth() { return 4 + .5f + CUFF_OVERHANG; }

    static Rac1Mesh mesh(Rac1SkinnedMesh source, boolean right) {
        float[] p = source.positions(), n = source.normals();
        int[] indices = source.indices();
        if (!right) {
            p = p.clone();
            n = n.clone();
            indices = indices.clone();
            for (int i = 0; i < p.length; i += 3) {
                p[i] = -p[i];
                n[i] = -n[i];
            }
            for (int i = 0; i < indices.length; i += 3) {
                int b = indices[i + 1];
                indices[i + 1] = indices[i + 2];
                indices[i + 2] = b;
            }
        }
        return new Rac1Mesh(p, n, source.uvs(), indices, source.materials());
    }
}
