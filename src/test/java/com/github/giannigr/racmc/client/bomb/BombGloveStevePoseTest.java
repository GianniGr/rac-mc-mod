package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.github.giannigr.racmc.item.weapon.bomb.BombGlovePose;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Phase;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.VisualPhase;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BombGloveStevePoseTest {
    private static final long START = 100;
    private static final long RELEASE = START + BombGlovePose.WINDUP_TICKS - 1;
    private static final BombGloveStevePose.Motion IDLE = new BombGloveStevePose.Motion(0, 0, 0);
    private static final BombGloveFirstPersonFrame.Lens LENS = new BombGloveFirstPersonFrame.Lens(.8, 1.43, 0, 0);

    @Test void cameraLocalFacingAndRollStayStableAcrossYawPitchAndSide() {
        for (boolean right : new boolean[]{false, true}) {
            var arm = BombGloveStevePose.frame(new Vec3(right ? -.08 : .08, .11, -1), new Vec3(1, 0, 0));
            var baseRight = arm.transform(new Vector3f(1, 0, 0));
            var baseDorsal = arm.transform(new Vector3f(0, 0, 1));
            assertTrue(baseRight.x > .99f);
            assertTrue(baseDorsal.y > .98f);
            for (float yaw : new float[]{-180, -90, 0, 90, 180}) {
                for (float pitch : new float[]{-85, -30, 0, 40, 85}) {
                    var camera = new Quaternionf().rotationYXZ((float) Math.toRadians(180 - yaw),
                            (float) Math.toRadians(-pitch), 0);
                    Vec3 cameraRelativePalm = BombGloveStevePose.cameraLocal(new Vec3(-.33, -.38, .64), camera);
                    var first = BombGloveFirstPersonFrame.sample(LENS, right, cameraRelativePalm, IDLE).palm();
                    assertEquals(right ? .53 : -.53, LENS.x() * first.x / -first.z, 1e-12);
                    assertEquals(-.40, LENS.y() * first.y / -first.z, 1e-12);
                    assertTrue(.20 / -first.z < .20 / .64); // Smaller idle apparent ball, same mesh size at release.
                    assertTrue(new Quaternionf(camera).conjugate().transform(camera.transform(new Vector3f(baseDorsal)))
                            .distance(baseDorsal) < 1e-5);
                    var atRelease = BombGloveFirstPersonFrame.sample(LENS, right, cameraRelativePalm,
                            BombGloveStevePose.motion(new VisualPhase(Phase.RELEASED, RELEASE), RELEASE, 0)).palm();
                    assertTrue(camera.transform(BombGloveStevePose.vector(atRelease))
                            .distance(-.33f, -.38f, .64f) < 1e-5);
                }
            }
        }
    }

    @Test void backswingAndFollowThroughUseDifferentAuthoritativePhasesAndEndpoints() {
        var target = new Vec3(.33, -.38, -.64);
        var windup = new VisualPhase(Phase.WINDUP, START);
        var backswing = BombGloveStevePose.motion(windup, START + 2, .5f);
        assertTrue(backswing.back() > .9);
        assertEquals(0, backswing.follow());
        var end = BombGloveStevePose.motion(windup, RELEASE, 0);
        assertEquals(target, BombGloveFirstPersonFrame.sample(LENS, true, target, end).palm());
        var atCommit = BombGloveStevePose.motion(new VisualPhase(Phase.RELEASED, RELEASE), RELEASE, 0);
        assertEquals(target, BombGloveFirstPersonFrame.sample(LENS, true, target, atCommit).palm());
        assertTrue(BombGloveStevePose.motion(new VisualPhase(Phase.RELEASED, RELEASE), RELEASE + 3, 0).follow() > .9);
        assertEquals(IDLE, BombGloveStevePose.motion(new VisualPhase(Phase.RELEASED, RELEASE), RELEASE + 6, 0));
        var abort = BombGloveStevePose.motion(new VisualPhase(Phase.ABORTED, START + 2), START + 2, 0);
        assertTrue(abort.back() > 0);
        assertEquals(0, abort.follow());
        assertEquals(0, abort.toRelease());
    }

    @Test void clearanceEnclosesRawClass121RadiusAndMirrorPreservesFacing() {
        assertTrue(BombGloveStevePose.shellClearance(new float[]{.096f, .025f, .03f, 0, 0, -.104f}) > .104f);
        var original = new Rac1SkinnedMesh(new float[]{1, 0, 0, 2, 0, 0, 1, 1, 0},
                new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1}, new float[6], new int[]{0, 1, 2},
                new int[]{0}, new int[9], new float[9]);
        var left = BombGloveStevePose.mesh(original, false);
        assertArrayEquals(new int[]{0, 2, 1}, left.indices());
        assertArrayEquals(new int[]{0, 1, 2}, original.indices());
        assertEquals(-1, left.positions()[0]);
        assertEquals(1, left.normals()[2]);
        var a = new Vector3f(-1, 1, 0).sub(-1, 0, 0);
        var b = new Vector3f(-2, 0, 0).sub(-1, 0, 0);
        assertTrue(a.cross(b).z > 0);
    }

    @Test void thirdPersonEndsWindupAndStartsFollowThroughAtTheNativePalm() {
        var feet = new Vec3(6, 64, 2);
        var windup = new VisualPhase(Phase.WINDUP, START);
        var released = new VisualPhase(Phase.RELEASED, RELEASE);
        for (var side : HumanoidArm.values()) {
            boolean right = side == HumanoidArm.RIGHT;
            for (float yaw : new float[]{-120, 0, 70, 180}) {
                var nativePalm = BombGlovePose.palm(feet, 1.62, yaw, side);
                var pullback = BombGloveWornDrawing.sample(feet, 1.62, yaw, right,
                        BombGloveStevePose.motion(windup, START + 2, .5f));
                assertTrue(pullback.palm().distanceTo(nativePalm) > .25);
                for (var phase : new VisualPhase[]{windup, released}) {
                    var atMarker = BombGloveWornDrawing.sample(feet, 1.62, yaw, right,
                            BombGloveStevePose.motion(phase, RELEASE, 0));
                    assertEquals(nativePalm, atMarker.palm());
                    var dorsal = atMarker.armRotation().transform(new Vector3f(0, 0, 1));
                    assertTrue(dorsal.y > .95);
                }
            }
        }
    }

    @Test void flightFirstStartsPoseWithoutRestoringTheHeldCopyAndAbortDoesNotRelease() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var owner = UUID.randomUUID();
        var epoch = UUID.randomUUID();
        var key = new BombGloveTracking.Key(owner, dimension, epoch, 1);
        var view = new BombGloveTracking.View(dimension);
        view.committed(new BombGloveTracking.Commit(key, RELEASE));
        view.held(new BombGloveTracking.Held(key, 12, 1, Phase.WINDUP, START));
        assertNull(view.heldBomb(owner, 12));
        assertEquals(new VisualPhase(Phase.RELEASED, RELEASE), view.visualPhase(owner, 12));
        var next = new BombGloveTracking.Key(owner, dimension, epoch, 2);
        view.held(new BombGloveTracking.Held(next, 12, 2, Phase.WINDUP, RELEASE + 8));
        assertNotNull(view.heldBomb(owner, 12));
        view.held(new BombGloveTracking.Held(next, 12, 3, Phase.ABORTED, RELEASE + 9));
        view.held(new BombGloveTracking.Held(new BombGloveTracking.Key(owner, dimension, epoch, 0),
                12, 4, Phase.HIDDEN, RELEASE + 9));
        assertNull(view.heldBomb(owner, 12));
        assertEquals(new VisualPhase(Phase.ABORTED, RELEASE + 9), view.visualPhase(owner, 12));
    }
}
