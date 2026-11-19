package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class Rac1ClankMountTest {
    @Test void properAxisRotationMatchesIndependentCoordinateEquations() {
        var b = Rac1ClankSteveMount.axisRotation();
        assertEquals(1, b.determinant(), 1e-6);
        for (Vector3f p : new Vector3f[] {new Vector3f(1,0,0), new Vector3f(0,1,0),
                new Vector3f(0,0,1), new Vector3f(-.2f,.3f,-.1f)}) {
            var expected = new Vector3f(-p.z, -p.y, -p.x);
            var actual = b.transformPosition(new Vector3f(p));
            assertEquals(0, expected.distance(actual), 1e-6);
        }
    }
    @Test void torsoCenterAndTwoScaleDefinitionsPreserveOriginalUnitsForSteveAndSlim() {
        for (boolean slim : new boolean[] {false, true}) {
            // Wide/slim have the same torso box [-4,4] x [0,12] x [-2,2] pixels.
            var anchor = new Vector3f(0, ((0 + 12) / 2f) / 16, 2f / 16);
            assertEquals(0, Rac1ClankSteveMount.local().transformPosition(new Vector3f()).distance(anchor));
            for (float classScaleAlreadyImported : new float[] {.145833328f, .166666672f, .25f}) {
                var sourcePoint = new Vector3f(-2 * classScaleAlreadyImported, classScaleAlreadyImported, 0);
                var mounted = Rac1ClankSteveMount.local().transformPosition(new Vector3f(sourcePoint));
                mounted.sub(anchor).mul(.9375f);
                var expected = new Vector3f(0, -classScaleAlreadyImported, 2 * classScaleAlreadyImported);
                assertEquals(0, expected.distance(mounted), 1e-6, "slim=" + slim);
                assertEquals(sourcePoint.length(), mounted.length(), 1e-6);
            }
        }
    }
    @Test void nativeRootAndCrouchBodyAppliedOnceNotRatchetOrHandSocket() {
        var localPoint = new Vector3f(-.2f, .3f, .1f);
        var body = new Matrix4f().translation(0, 3.2f / 16, 0).rotateX(.5f);
        var mount = Rac1ClankSteveMount.local();
        for (Matrix4f root : new Matrix4f[] {
                new Matrix4f().rotateY(.7f), // ordinary yaw
                new Matrix4f().rotateY(.7f).rotateX(-1.2f).translate(0,-1,.3f), // swim root
                new Matrix4f().rotateY(-.4f).rotateZ(.2f) // riding/other resolved root
        }) {
            var actual = new Matrix4f(root).mul(body).mul(mount).transformPosition(new Vector3f(localPoint));
            var independent = new Vector3f(-localPoint.z, -localPoint.y, -localPoint.x)
                    .mul(16f / 15).add(0, 6f / 16, 2f / 16);
            float y = independent.y, z = independent.z;
            independent.y = y * (float)Math.cos(.5) - z * (float)Math.sin(.5) + 3.2f / 16;
            independent.z = y * (float)Math.sin(.5) + z * (float)Math.cos(.5);
            root.transformPosition(independent);
            assertEquals(0, actual.distance(independent), 1e-6);
            var doubled = new Matrix4f(root).mul(body).mul(body).mul(mount)
                    .transformPosition(new Vector3f(localPoint));
            assertTrue(actual.distance(doubled) > .01);
        }
    }
    @Test void sourceClipPairsAndHeroPhaseAreNotPlayable87OrThrusterRotorGuess() {
        var p = Rac1ClankClock.Profile.ORIGINAL_60;
        assertEquals(new Rac1ClankAnimations.Selection(12,5,21,2),
                Rac1ClankAnimations.select(Module.HELI, Action.HIGH_JUMP, p));
        assertEquals(new Rac1ClankAnimations.Selection(15,8,18,2),
                Rac1ClankAnimations.select(Module.HELI, Action.LONG_JUMP, p));
        assertEquals(10, Rac1ClankAnimations.select(Module.HELI, Action.GLIDE, p).packBlendSteps());
        assertEquals(6, Rac1ClankAnimations.select(Module.HELI, Action.GLIDE, p).packSequence());
        assertEquals(1, Rac1ClankAnimations.select(Module.THRUSTER, Action.HIGH_JUMP, p).packSequence());
        var phase = new Rac1ClankAnimations.HeroPhase(29, 50, 36);
        assertEquals(39.5, phase.frame(18));
        assertEquals(50, phase.frame(100));
    }
    @Test void lateObserverTimelineReplayLifecycleAndPerPlayerIsolation() {
        var views = new Rac1ClankViews();
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        var equipment = new Rac1ClankEquipment(true, false, false, true, Module.HELI);
        var s = new Rac1ClankSession.Snapshot(one, 1, 1, 100, equipment, Action.IDLE, 0, 0, 0);
        assertTrue(views.accept(s, 5));
        assertEquals(102.5, views.serverTime(one, 7.5));
        assertFalse(views.accept(s, 6));
        assertTrue(views.anyWorn());
        assertTrue(views.accept(new Rac1ClankSession.Snapshot(one,1,2,100,equipment,
                Action.IDLE,0,0,0), 6)); // a newer mutation in the same authoritative MC tick
        var fresh = new Rac1ClankSession.Snapshot(one, 2, 0, 101, Rac1ClankEquipment.EMPTY,
                Action.IDLE, 0, 0, 0);
        assertTrue(views.accept(fresh, 9));
        assertFalse(views.accept(s, 10));
        assertFalse(views.anyWorn());
        assertTrue(views.accept(new Rac1ClankSession.Snapshot(two,1,0,101,equipment,
                Action.IDLE,0,0,0), 10));
        views.forget(one);
        assertNull(views.get(one));
        assertNotNull(views.get(two));
        views.clear();
        assertNull(views.get(two));
    }
    @Test void layerIsScopedAndDoesNotInstallCompetingRootOrModelWrites() throws Exception {
        Path root = Path.of(System.getProperty("racmc.projectDir"));
        String layer = Files.readString(root.resolve(
                "src/main/java/com/github/giannigr/racmc/client/clank/Rac1ClankLayer.java"));
        assertEquals(1, layer.split("body\\.translateAndRotate\\(pose\\)", -1).length - 1);
        assertTrue(layer.contains("finally"));
        assertTrue(layer.contains("pose.popPose()"));
        assertFalse(layer.contains("applyModelRoot"));
        assertFalse(layer.contains("setRotation"));
        assertFalse(layer.contains("setPos("));
    }
}
