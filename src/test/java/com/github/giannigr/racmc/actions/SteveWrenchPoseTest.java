package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class SteveWrenchPoseTest {
    @Test void sourceBindPosePreservesTargetRestPose() {
        Quaternionf rest = new Quaternionf().rotateXYZ(0.3F, -0.2F, 0.5F);
        Quaternionf target = new Quaternionf().rotateZ(0.1F);
        Quaternionf actual = SteveWrenchPose.rotation(rest, rest,
                new Quaternionf().rotateX(1.2F), target);
        assertEquals(1, Math.abs(actual.dot(target)), 1.0e-6);
    }

    @Test void conjugationMapsSourceSpinAxisRatherThanSwappingEulerComponents() {
        Quaternionf actual = SteveWrenchPose.rotation(new Quaternionf().rotateZ(0.5F),
                new Quaternionf(), new Quaternionf().rotateX((float) (-Math.PI / 2)),
                new Quaternionf());
        assertEquals(1, Math.abs(actual.dot(new Quaternionf().rotateY(0.5F))), 1.0e-6);
    }

    @Test void socketStaysAttachedAndPartDoesNotMutateSharedTorso() {
        Matrix4f torso = new Matrix4f().rotateY(0.3F);
        Matrix4f saved = new Matrix4f(torso);
        Matrix4f arm = SteveWrenchPose.part(torso, new ActionVector(-5, 2, 0), 0.5, 0, 0);
        Matrix4f socket = SteveWrenchPose.socket(arm, false);
        assertTrue(torso.equals(saved, 0));
        assertFalse(socket.equals(arm, 1.0e-6F));
        // Undo arm transform: the socket origin stays fixed relative to this rigid arm.
        Vector3f actual = new Matrix4f(arm).invert().mul(socket).getTranslation(new Vector3f());
        Vector3f expected = SteveWrenchPose.socket(new Matrix4f(), false)
                .getTranslation(new Vector3f());
        assertTrue(actual.equals(expected, 1.0e-6F));
    }
}
