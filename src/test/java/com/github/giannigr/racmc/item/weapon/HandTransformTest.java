package com.github.giannigr.racmc.item.weapon;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;
import static org.junit.jupiter.api.Assertions.*;

class HandTransformTest {
    // Synthetic content only: all three item angles, off-axis muzzle/nonorigin grip,
    // different forward/up axes, scale, animations, ammo and cadence. Not a second Blaster.
    static final WeaponDefinition SYNTHETIC = new WeaponDefinition(
            "test:synthetic", "test:no_asset", new Vector(.13, -.27, .08),
            new Vector(0, 1, 0), new Vector(0, 0, -1), new Vector(-.4, .9, .21),
            1.7, new Vector(23, -41, 67), 3, 8, 7, "test:cell", "test:probe",
            WeaponDefinition.FireMode.HELD,
            new WeaponDefinition.Presentation(new Vector(7, 13, 29), new Vector(.1, -.2, .3), .8),
            new WeaponDefinition.Presentation(new Vector(3, 5, 11), new Vector(0, 0, 0), 1.3));

    @Test void literalPinnedMinecraftChainForTwoDefinitionsBothHandsAndResolvedPoses() {
        for (var weapon : new WeaponDefinition[]{Rac1BlasterDefinition.INSTANCE, SYNTHETIC}) {
            for (boolean left : new boolean[]{false, true}) {
                for (boolean slim : new boolean[]{false, true}) {
                    // Deliberately nonzero Z, nonuniform arm scale, translated/crouched pivot.
                    var pose = new HandPoseSnapshot(new Vector(30_000_000.125, 71.5, -28_000_000.75),
                            137, 1.12, left,
                            new Vector((left ? 5 : -5) + (slim ? (left ? -.5 : .5) : 0), 5.2, .4),
                            new Vector(-63, 39, -7), new Vector(.97, 1.03, 1.01), .9375, 1.501);
                    Vector actual = HandTransform.muzzle(weapon, pose);
                    // Independent matrix chain transcribed from source, not production rotations.
                    double mirror = left ? -1 : 1;
                    var r = weapon.itemRotation(left);
                    var matrix = new Matrix4d().translation(pose.position().x(), pose.position().y(), pose.position().z())
                            .scale(1.12).rotateY(Math.toRadians(180 - 137))
                            .scale(-1, -1, 1).scale(.9375).translate(0, -1.501, 0)
                            .translate(pose.pivot().x() / 16, 5.2 / 16, .4 / 16)
                            .rotateZYX(Math.toRadians(-7), Math.toRadians(39), Math.toRadians(-63))
                            .scale(.97, 1.03, 1.01)
                            .rotateX(-Math.PI / 2).rotateY(Math.PI)
                            .translate(mirror / 16, .125, -.625)
                            .rotateXYZ(Math.toRadians(r.x()), Math.toRadians(mirror * r.y()), Math.toRadians(mirror * r.z()))
                            // ItemRenderer centers; imported drawing restores the socket, exactly once.
                            .translate(-.5, -.5, -.5).translate(.5, .5, .5)
                            .scale(weapon.worldScale())
                            .translate(-weapon.grip().x(), -weapon.grip().y(), -weapon.grip().z());
                    var expected = matrix.transformPosition(new Vector3d(
                            weapon.muzzle().x(), weapon.muzzle().y(), weapon.muzzle().z()));
                    assertEquals(expected.x, actual.x(), 2e-8);
                    assertEquals(expected.y, actual.y(), 2e-8);
                    assertEquals(expected.z, actual.z(), 2e-8);
                }
            }
        }
    }

    @Test void standingScaleHasIndependentSourceDerivedNumericRegression() {
        var result = HandTransform.muzzle(Rac1BlasterDefinition.INSTANCE,
                HandPoseSnapshot.standing(new Vector(0, 0, 0), 0, 0, 0, false, false, 1));
        assertEquals(-.3234375, result.x(), 1e-8);
        assertEquals(1.62328125, result.y(), 1e-8);
        assertEquals(1.17984375, result.z(), 1e-8);
    }

    @Test void compatibilityConstructorRetainsRawRightFallbackForOtherDefinitions() {
        assertEquals(SYNTHETIC.itemRotation(), SYNTHETIC.leftItemRotation());
        assertEquals(SYNTHETIC.itemRotation(), SYNTHETIC.itemRotation(true));
        assertEquals(new Vector(180, 0, -90), Rac1BlasterDefinition.INSTANCE.itemRotation(false));
        assertEquals(new Vector(180, 0, 90), Rac1BlasterDefinition.INSTANCE.itemRotation(true));
    }

    @Test void invalidContentAndPoseFailBeforeWorldMutation() {
        assertThrows(IllegalArgumentException.class, () -> new Vector(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WeaponDefinition("test:a", "test:a",
                new Vector(0, 0, 0), new Vector(0, 0, 0), new Vector(0, 1, 0),
                new Vector(1, 2, 3), 1, new Vector(0, 0, 0), 0, 1, 1, "test:a", "test:a",
                WeaponDefinition.FireMode.HELD, SYNTHETIC.firstPerson(), SYNTHETIC.gui()));
        assertThrows(IllegalArgumentException.class, () -> HandPoseSnapshot.standing(
                new Vector(0, 0, 0), 0, 0, 0, false, false, 0));
    }
}
