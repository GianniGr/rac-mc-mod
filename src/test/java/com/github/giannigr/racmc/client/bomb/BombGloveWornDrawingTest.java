package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.item.weapon.bomb.BombGlovePose;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BombGloveWornDrawingTest {
    @Test void projectionInputsShareExactlyTheGameplayPalmForBothSides() {
        var feet = new Vec3(-109.5, 64.75, 280.125);
        for (var side : HumanoidArm.values()) {
            for (float yaw : new float[]{0, 90, 180, -45}) {
                var snapshot = BombGloveWornDrawing.sample(feet, 1.27, yaw, side == HumanoidArm.RIGHT,
                        new BombGloveStevePose.Motion(0, 0, 1));
                assertEquals(BombGlovePose.palm(feet, 1.27, yaw, side), snapshot.palm());
                assertEquals(feet, snapshot.feet());
                assertEquals(1, snapshot.armRotation().lengthSquared(), 1e-5);
            }
        }
    }

    @Test void fitRetainsUpperArmForStandardAndSlim() {
        var bounds = new BombGloveWornDrawing.Bounds(-.11f, -.11f, -.03f, .26f, .21f, .55f);
        assertTrue(bounds.cuff(4) >= 2);
        assertTrue(bounds.cuff(3) <= 6);
        assertTrue(bounds.cuff(4) <= bounds.cuff(3));
    }
}
