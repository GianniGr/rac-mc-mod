package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.item.weapon.bomb.BombGlovePose;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Phase;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.VisualPhase;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Projection diagnostic using the retained mesh bounds; no assets or window required. */
class BombGloveProjectionTest {
    private static final BombGloveWornDrawing.Bounds BOX = new BombGloveWornDrawing.Bounds(
            -.11222f, -.10610f, -.03133f, .26575f, .21077f, .55855f);
    private static final float RADIUS = .105f, CLEARANCE = RADIUS + .003f;

    @Test void shellGrowsAroundTheSameWristForClassicAndSlimWithoutSpendingClearance() {
        for (var fit : BombGloveCuff.ArmFit.values()) {
            boolean right = fit.name().endsWith("RIGHT");
            float cuff = BOX.cuff(fit.width);
            var shell = BombGloveStevePose.fittedShell(BOX, fit, cuff, right);
            float x = right ? BOX.centerX() : -BOX.centerX();
            var wrist = shell.transformPosition(x, BOX.centerY(), BOX.maxZ(), new Vector3f());
            var edge = shell.transformPosition(x + BOX.width()/2, BOX.maxY(), BOX.minZ(), new Vector3f());
            assertEquals(fit.centerX()/16, wrist.x, 1e-6);
            assertEquals(.625, wrist.y, 1e-6);
            assertEquals(0, wrist.z, 1e-6);
            assertEquals(cuff/16, edge.y, 1e-6);
            assertTrue(wrist.y - edge.y > 6 / 16f); // past the old maximum sleeve coverage
            assertEquals((fit.width + 2.25f)/32, Math.abs(edge.x - wrist.x), 1e-6);
            assertEquals(6.25/32, edge.z, 1e-6);
            var ballToOrigin = BombGloveStevePose.armOrigin(fit.centerX(), CLEARANCE);
            assertEquals(-CLEARANCE, wrist.y + ballToOrigin.y, 1e-6);
        }
    }

    @Test void carryAndCastProjectIntoLowSideLaneAtNormalAndWideFovWithoutMovingTheWorldRelease() {
        long start = 100, marker = start + BombGlovePose.WINDUP_TICKS - 1;
        var windup = new VisualPhase(Phase.WINDUP, start);
        var released = new VisualPhase(Phase.RELEASED, marker);
        var idle = BombGloveStevePose.motion(null, start, 0);
        for (var fit : BombGloveCuff.ArmFit.values()) for (int fov : new int[]{65, 70, 90, 105, 110})
            for (double aspect : new double[]{16/9., 21/9.}) for (int pitch : new int[]{-35, 0, 35, 72, 85}) {
                boolean right = fit.name().endsWith("RIGHT");
                int sign = right ? 1 : -1;
                var proj = new Matrix4f().perspective((float) Math.toRadians(fov), (float) aspect, .05f, 128);
                var lens = BombGloveFirstPersonFrame.Lens.of(proj);
                var camera = new Quaternionf().rotationYXZ((float) Math.PI, (float) Math.toRadians(-pitch), 0);
                var worldPalm = BombGlovePose.palm(Vec3.ZERO, 1.62, 0, right ? HumanoidArm.RIGHT : HumanoidArm.LEFT)
                        .subtract(0, 1.62, 0);
                var physical = BombGloveStevePose.cameraLocal(worldPalm, camera);
                String where = fit + " fov=" + fov + " pitch=" + pitch + " aspect=" + aspect;
                for (var motion : new BombGloveStevePose.Motion[]{idle,
                        BombGloveStevePose.motion(windup, start + 2, .6f),
                        BombGloveStevePose.motion(windup, start + 4, 0),
                        BombGloveStevePose.motion(released, marker + 2, 0),
                        BombGloveStevePose.motion(released, marker + 4, 0)}) {
                    var frame = BombGloveFirstPersonFrame.sample(lens, right, physical, motion);
                    var palm = BombGloveStevePose.vector(frame.palm());
                    var ball = new Extent();
                    for (float x : signs(RADIUS)) for (float y : signs(RADIUS)) for (float z : signs(RADIUS))
                        ball.add(project(proj, new Vector3f(palm).add(x,y,z)), sign);
                    assertTrue(ball.x0 > .19 && ball.x1 < .90 && ball.y0 > -.75 && ball.y1 < -.06, where + " ball " + ball);
                    var glove = new Extent();
                    var geometry = geometry(fit, right, frame);
                    for (float x : new float[]{BOX.minX(), BOX.maxX()})
                        for (float y : new float[]{BOX.minY(), BOX.maxY()})
                            for (float z : new float[]{BOX.minZ(), BOX.maxZ()}) {
                                var v = geometry.transformPosition(new Vector3f(x * sign, y, z));
                                glove.add(project(proj, v), sign);
                            }
                    assertTrue(glove.x0 > .15 && glove.y1 < .05, where + " glove " + glove);
                    float sy = (BOX.minY() + BOX.maxY()) / 2;
                    var cuff = project(proj, geometry.transformPosition(new Vector3f(BOX.centerX() * sign, sy, BOX.minZ())));
                    var tips = project(proj, geometry.transformPosition(new Vector3f(BOX.centerX() * sign, sy, BOX.maxZ())));
                    assertTrue(sign * cuff.x > sign * tips.x && cuff.y < tips.y, where + " sleeve direction");
                    var mid = project(proj, geometry.transformPosition(new Vector3f(BOX.centerX() * sign, sy,
                            (BOX.minZ()+BOX.maxZ())/2)));
                    assertTrue(sign * mid.x < .94 && mid.y > -.78, where + " shell midpoint " + mid);
                    assertTrue(Math.min(glove.x1, 1) - glove.x0 > .22, where + " shell width " + glove);
                    assertStableRoll(frame, where);
                    if (right && pitch == 72 && aspect < 2 && motion == idle)
                        System.out.printf("%s: bomb %s, glove %s, cuff (%.2f, %.2f)%n", where, ball, glove, cuff.x, cuff.y);
                }
                // Terminal handoff is tested in world space, including pitches at
                // which even the original outgoing trajectory is outside the viewport.
                for (var phase : new VisualPhase[]{windup, released}) {
                    var release = BombGloveFirstPersonFrame.sample(lens, right, physical,
                            BombGloveStevePose.motion(phase, marker, 0));
                    assertEquals(physical, release.palm(), where);
                    assertTrue(camera.transform(BombGloveStevePose.vector(release.palm()))
                            .distance(BombGloveStevePose.vector(worldPalm)) < 1e-5, where);
                    assertStableRoll(release, where);
                    var shell = geometry(fit, right, release);
                    for (float x : new float[]{BOX.minX(), BOX.maxX()})
                        for (float y : new float[]{BOX.minY(), BOX.maxY()})
                            for (float z : new float[]{BOX.minZ(), BOX.maxZ()})
                                assertTrue(shell.transformPosition(new Vector3f(sign*x,y,z)).z < -.05f,
                                        where + " release near plane");
                }
            }
    }

    private static Matrix4f geometry(BombGloveCuff.ArmFit fit, boolean right, BombGloveFirstPersonFrame.Frame frame) {
        return new Matrix4f().translate(BombGloveStevePose.vector(frame.palm())).rotate(frame.rotation())
                .translate(BombGloveStevePose.armOrigin(fit.centerX(), CLEARANCE))
                .mul(BombGloveStevePose.fittedShell(BOX, fit, BOX.cuff(fit.width), right));
    }

    private static void assertStableRoll(BombGloveFirstPersonFrame.Frame frame, String where) {
        var arm = frame.rotation().transform(new Vector3f(0,1,0));
        var transverse = frame.rotation().transform(new Vector3f(1,0,0));
        var stable = new Vector3f(1,0,0).fma(-arm.x,arm).normalize();
        assertTrue(transverse.dot(stable) > .965f, where + " wrist roll");
    }

    private static float[] signs(float x) { return new float[]{-x,x}; }

    private static Vector3f project(Matrix4f m, Vector3f p) {
        assertTrue(p.z < -.05f, "inside near plane: " + p);
        return m.transformProject(p);
    }

    private static final class Extent {
        float x0 = 2, x1 = -2, y0 = 2, y1 = -2;
        void add(Vector3f p, int sign) {
            x0 = Math.min(x0, sign*p.x); x1 = Math.max(x1, sign*p.x);
            y0 = Math.min(y0,p.y); y1 = Math.max(y1,p.y);
        }
        @Override public String toString() {
            return String.format(java.util.Locale.ROOT, "[%.2f..%.2f, %.2f..%.2f]", x0, x1, y0, y1);
        }
    }
}
