package com.github.giannigr.racmc.item.weapon.bomb;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BombBallisticsTest {
    @Test void socketMirrorsAroundFacingAndDoesNotDependOnCameraPitch() {
        var feet = new Vec3(4, -3, 8);
        var right = BombGlovePose.palm(feet, 1.62, 0, HumanoidArm.RIGHT);
        var left = BombGlovePose.palm(feet, 1.62, 0, HumanoidArm.LEFT);
        assertEquals(4, (right.x + left.x) / 2, 1e-6);
        assertTrue(right.x < left.x);
        assertEquals(-3 + 1.24, right.y, 1e-6);
        assertEquals(8.64, right.z, 1e-6);
        assertEquals(right.z, left.z, 1e-6);
        assertTrue(BombGlovePose.palm(feet, 1.62, 90, HumanoidArm.RIGHT).x < feet.x);
    }

    @Test void flightAndWaveKeepSourceScaleAtMinecraftTickRate() {
        var forward = BombBallistics.launch(0, 0);
        assertEquals(.425, forward.horizontalDistance(), 1e-6);
        assertEquals(.275, BombBallistics.launch(0, -89).y, 1e-6);
        assertEquals(BombBallistics.launch(0, 34), BombBallistics.launch(0, 89));
        assertEquals(.1, BombBallistics.WATER_GRAVITY / BombBallistics.GRAVITY, 1e-6);
        assertEquals(.5, BombBallistics.radius(0));
        assertEquals(2.5, BombBallistics.radius(BombBallistics.WAVE_STEPS));
        for (int i = 1; i <= BombBallistics.WAVE_STEPS; i++)
            assertTrue(BombBallistics.radius(i) > BombBallistics.radius(i - 1));
    }

    @Test void palmSweepChecksFullVolumeBetweenClearEndpoints() {
        var obstacle = new AABB(.03, .14, .26, .10, .2, .38);
        var start = Vec3.ZERO;
        var end = new Vec3(0, 0, .64);
        assertTrue(BombGloveThrows.clipsPalm(obstacle, start, end));
        assertFalse(BombGloveThrows.clipsPalm(obstacle.move(.2, 0, 0), start, end));
    }
}
