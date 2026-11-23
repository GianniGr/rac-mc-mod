package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Phase;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.VisualPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BombGloveLandmarkTest {
    // Retained class-192 Novalis bbox, x/z/-y import mapping. Landmark names are
    // end labels from the owner fit report, not claimed retail joint IDs.
    private static final BombGloveWornDrawing.Bounds BOX = new BombGloveWornDrawing.Bounds(
            -.11222f, -.10610f, -.03133f, .26575f, .21077f, .55855f);
    private static final float RADIUS = .105f;
    private static final float CLEARANCE = RADIUS + .003f;
    private static final BombGloveFirstPersonFrame.Lens LENS = new BombGloveFirstPersonFrame.Lens(.8, 1.43, 0, 0);

    @Test void cuffFingersAndDorsalLandmarksRemainOrderedForEachFitAndSide() {
        for (var fit : BombGloveCuff.ArmFit.values()) {
            boolean right = fit.name().endsWith("RIGHT");
            var x = right ? BOX.centerX() : -BOX.centerX();
            var cuff = BOX.cuff(fit.width);
            var transform = BombGloveStevePose.fittedShell(BOX, fit, cuff, right);
            assertTrue(transform.determinant() > 0); // separate left mesh winding, never a negative draw transform
            var wrist = transform.transformPosition(x, BOX.centerY(), BOX.minZ(), new Vector3f());
            var tips = transform.transformPosition(x, BOX.centerY(), BOX.maxZ(), new Vector3f());
            var back = transform.transformPosition(x, BOX.maxY(), BOX.maxZ(), new Vector3f());
            assertEquals(cuff / 16, wrist.y, 1e-6);
            assertEquals(10f / 16, tips.y, 1e-6);
            assertTrue(tips.y > wrist.y);
            assertTrue(back.z > tips.z);
        }
    }

    @Test void class121StaysOutwardAndPalmSideInCameraAndWorldAnimatedFrames() {
        for (var fit : BombGloveCuff.ArmFit.values()) {
            boolean right = fit.name().endsWith("RIGHT");
            for (boolean camera : new boolean[]{false, true}) {
                for (var phase : new Phase[]{Phase.READY, Phase.WINDUP, Phase.RELEASED}) {
                    var motion = BombGloveStevePose.motion(new VisualPhase(phase, 10), 12, 0);
                    var snap = BombGloveWornDrawing.sample(Vec3.ZERO, 1.62, 65, right, motion);
                    var view = BombGloveFirstPersonFrame.sample(LENS, right, new Vec3(right ? .27 : -.27, -.38, -.64), motion);
                    var rotation = camera ? view.rotation() : snap.armRotation();
                    Vec3 anchor = camera ? view.palm() : snap.palm();
                    Vector3f ball = BombGloveStevePose.vector(anchor);
                    var frame = new Matrix4f().translate(ball).rotate(rotation)
                            .translate(BombGloveStevePose.armOrigin(fit.centerX(), CLEARANCE))
                            .mul(BombGloveStevePose.fittedShell(BOX, fit, BOX.cuff(fit.width), right));
                    var tip = frame.transformPosition(right ? BOX.centerX() : -BOX.centerX(), BOX.centerY(),
                            BOX.maxZ(), new Vector3f());
                    var y = rotation.transform(new Vector3f(0, 1, 0));
                    var back = rotation.transform(new Vector3f(0, 0, 1));
                    var offset = ball.sub(tip, new Vector3f());
                    assertTrue(offset.dot(y) > RADIUS);
                    assertTrue(offset.dot(back) < 0);
                    if (camera) assertTrue(tip.z > ball.z); // fingers do not get in front of the camera-facing ball
                }
            }
        }
    }

    @Test void bothViewsLowerTheArmAtRestAndAfterRecovery() {
        for (var fit : BombGloveCuff.ArmFit.values()) {
            boolean right = fit.name().endsWith("RIGHT");
            for (var phase : new Phase[]{Phase.READY, Phase.RELEASED, Phase.ABORTED}) {
                var motion = BombGloveStevePose.motion(new VisualPhase(phase, 10), 30, 0);
                assertTrue(motion.idle());
                var view = BombGloveFirstPersonFrame.sample(LENS, right, Vec3.ZERO, motion);
                assertTrue(view.palm().y < -.30);
                assertTrue(view.rotation().transform(new Vector3f(0, 1, 0)).y < 0);
                var world = BombGloveWornDrawing.sample(Vec3.ZERO, 1.62, 20, right, motion);
                assertTrue(world.armRotation().transform(new Vector3f(0, 1, 0)).y < -.9f);
            }
            var reaching = new BombGloveStevePose.Motion(0, 0, 1);
            var reach = BombGloveFirstPersonFrame.sample(LENS, right, new Vec3(.33, -.38, -.64), reaching)
                    .rotation().transform(new Vector3f(0, 1, 0));
            assertTrue((right ? -reach.x : reach.x) > .8f && reach.z < 0);
        }
    }

    @Test void worldBlendStartsAtLiveVanillaGripAndNeverMovesTheReleaseSocket() {
        for (var fit : BombGloveCuff.ArmFit.values()) {
            boolean right = fit.name().endsWith("RIGHT");
            var target = BombGloveWornDrawing.sample(new Vec3(4, 2, 3), 1.62, 20, right,
                    new BombGloveStevePose.Motion(0, 0, 1));
            var nativeArm = new Matrix4f().translate(right ? .31f : -.31f, 1.35f, .05f)
                    .rotateZ(.1f).rotateX(2.9f);
            var nativeGrip = nativeArm.transformPosition(BombGloveStevePose.armOrigin(fit.centerX(), CLEARANCE).negate());
            var start = BombGloveWornDrawing.blendCarry(target, nativeArm, fit.centerX(), CLEARANCE, 0);
            assertEquals(target.feet().add(nativeGrip.x, nativeGrip.y, nativeGrip.z), start.palm());
            var approaching = BombGloveWornDrawing.blendCarry(target, nativeArm, fit.centerX(), CLEARANCE, .249);
            assertTrue(approaching.palm().distanceTo(target.palm()) < .001);
            assertSame(target, BombGloveWornDrawing.blendCarry(target, nativeArm, fit.centerX(), CLEARANCE, 1));
        }
    }

    @Test void regionalStandingLandmarksBecomeLowOutsideLoadAndRisingInwardRecoveryInBothViews() {
        var load = BombGloveStevePose.motion(new VisualPhase(Phase.WINDUP, 10), 12, .7f);
        var commit = BombGloveStevePose.motion(new VisualPhase(Phase.RELEASED, 15), 15, 0);
        var arc = BombGloveStevePose.motion(new VisualPhase(Phase.RELEASED, 15), 17, 0);
        for (var fit : BombGloveCuff.ArmFit.values()) {
            boolean right = fit.name().endsWith("RIGHT");
            int side = right ? 1 : -1;
            var firstSocket = new Vec3(side * .33, -.38, -.64);
            var firstIdle = BombGloveFirstPersonFrame.sample(LENS, right, firstSocket, new BombGloveStevePose.Motion(0, 0, 0)).palm();
            var firstLoad = BombGloveFirstPersonFrame.sample(LENS, right, firstSocket, load).palm();
            var firstFollow = BombGloveFirstPersonFrame.sample(LENS, right, firstSocket, arc).palm();
            assertTrue(firstLoad.z >= firstIdle.z && LENS.y() * firstLoad.y / -firstLoad.z < -.40);
            assertTrue(side * firstLoad.x > side * firstIdle.x);
            assertEquals(firstSocket, BombGloveFirstPersonFrame.sample(LENS, right, firstSocket, commit).palm());
            assertTrue(side * firstFollow.x < side * firstIdle.x && firstFollow.y > firstIdle.y);
            var low = BombGloveWornDrawing.sample(Vec3.ZERO, 1.62, 0, right);
            var worldLoad = BombGloveWornDrawing.sample(Vec3.ZERO, 1.62, 0, right, load);
            var worldRelease = BombGloveWornDrawing.sample(Vec3.ZERO, 1.62, 0, right, commit);
            var worldFollow = BombGloveWornDrawing.sample(Vec3.ZERO, 1.62, 0, right, arc);
            assertTrue(worldLoad.palm().z < low.palm().z && worldLoad.palm().y < low.palm().y);
            assertTrue(-side * worldLoad.palm().x > -side * low.palm().x);
            assertTrue(-side * worldFollow.palm().x < -side * worldRelease.palm().x);
            assertTrue(worldFollow.palm().y > worldRelease.palm().y);
            var loadDorsal = BombGloveFirstPersonFrame.sample(LENS, right, firstSocket, load).rotation().transform(new Vector3f(0, 0, 1));
            assertTrue(loadDorsal.y > .85); // caster's back remains upright in the low lane
        }
    }
}
