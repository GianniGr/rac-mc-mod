package com.github.giannigr.racmc.item.weapon;

import java.util.List;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;
import static org.junit.jupiter.api.Assertions.*;

class HandRootTest {
    @Test void pinnedPlayerRootPitchRollTranslationsAndOrderReachSharedMuzzle() {
        // Literal source roots: Elytra yaw correction, swimming translation, death roll,
        // upside-down translation, auto-spin, sleeping bed rotation. Not a production root replay.
        var roots = List.of(
                new HandRoot(List.of(HandRoot.Step.y(143), HandRoot.Step.x(-73), HandRoot.Step.y(29))),
                new HandRoot(List.of(HandRoot.Step.y(143), HandRoot.Step.x(-61), HandRoot.Step.translate(0, -1, .3))),
                new HandRoot(List.of(HandRoot.Step.y(143), HandRoot.Step.z(58))),
                new HandRoot(List.of(HandRoot.Step.y(143), HandRoot.Step.translate(0, 1.9, 0), HandRoot.Step.z(180))),
                new HandRoot(List.of(HandRoot.Step.y(143), HandRoot.Step.x(-105), HandRoot.Step.y(-225))),
                new HandRoot(List.of(HandRoot.Step.y(270), HandRoot.Step.z(90), HandRoot.Step.y(270))));
        var matrices = List.of(
                new Matrix4d().rotateY(rad(143)).rotateX(rad(-73)).rotateY(rad(29)),
                new Matrix4d().rotateY(rad(143)).rotateX(rad(-61)).translate(0, -1, .3),
                new Matrix4d().rotateY(rad(143)).rotateZ(rad(58)),
                new Matrix4d().rotateY(rad(143)).translate(0, 1.9, 0).rotateZ(Math.PI),
                new Matrix4d().rotateY(rad(143)).rotateX(rad(-105)).rotateY(rad(-225)),
                new Matrix4d().rotateY(rad(270)).rotateZ(rad(90)).rotateY(rad(270)));
        for (var weapon : new WeaponDefinition[]{Rac1BlasterDefinition.INSTANCE, HandTransformTest.SYNTHETIC})
            for (boolean left : new boolean[]{false, true})
                for (int i = 0; i < roots.size(); i++) {
            var pose = new HandPoseSnapshot(new Vector(20_000_000.125, 65, -10_000_000.25),
                    37, 1.2, left, new Vector(left ? 4.5 : -4.5, 5.2, .3),
                    new Vector(-63, 27, -9), new Vector(1, 1, 1), .9375, 1.501, roots.get(i));
            var r = weapon.itemRotation(left);
            var expected = new Matrix4d().translate(20_000_000.125, 65, -10_000_000.25).scale(1.2)
                    .mul(matrices.get(i)).scale(-1, -1, 1).scale(.9375).translate(0, -1.501, 0)
                    .translate((left ? 4.5 : -4.5) / 16, 5.2 / 16, .3 / 16)
                    .rotateZYX(rad(-9), rad(27), rad(-63))
                    .rotateX(-Math.PI / 2).rotateY(Math.PI).translate((left ? -1.0 : 1.0) / 16, .125, -.625)
                    .rotateXYZ(rad(r.x()), rad(left ? -r.y() : r.y()), rad(left ? -r.z() : r.z()))
                    .translate(-.5, -.5, -.5).translate(.5, .5, .5)
                    .scale(weapon.worldScale()).translate(-weapon.grip().x(), -weapon.grip().y(), -weapon.grip().z())
                    .transformPosition(new Vector3d(weapon.muzzle().x(), weapon.muzzle().y(), weapon.muzzle().z()));
            var actual = HandTransform.muzzle(weapon, pose);
            assertEquals(expected.x, actual.x(), 2e-8);
            assertEquals(expected.y, actual.y(), 2e-8);
            assertEquals(expected.z, actual.z(), 2e-8);
        }
    }
    @Test void immutableBoundedRootRejectsNonfiniteOperations() {
        assertThrows(IllegalArgumentException.class, () -> HandRoot.Step.x(Double.NaN));
        assertThrows(UnsupportedOperationException.class, () -> HandRoot.yaw(0).steps().clear());
        assertThrows(IllegalArgumentException.class, () ->
                new HandRoot(java.util.Collections.nCopies(13, HandRoot.Step.x(1))));
    }
    private static double rad(double degrees) { return Math.toRadians(degrees); }
}
