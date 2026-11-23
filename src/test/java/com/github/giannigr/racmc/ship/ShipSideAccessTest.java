package com.github.giannigr.racmc.ship;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShipSideAccessTest {
    @Test
    void normalStandBesideCockpitWasInsideTheOldSweptBox() {
        var profile = ShipCockpitRefit.CANDIDATE;
        var hull = EntityDimensions.fixed(profile.width(), profile.height()).makeBoundingBox(Vec3.ZERO);
        var player = EntityDimensions.fixed(.6F, 1.8F);
        for (float yaw : new float[]{0, 37, 90, 135, 225}) {
            for (int side : new int[]{-1, 1}) {
                var feet = new Vec3(side * ShipSideAccess.exitSide(yaw, player.width()), 0, profile.seatLocal().z);
                assertFalse(ShipCockpitProfile.outsideHull(hull, feet.yRot(-yaw * ((float)Math.PI / 180)), player));
                assertTrue(ShipSideAccess.besideCockpit(feet, yaw, player.width(), profile.seatLocal()));
                var eye = feet.add(0, 1.62, 0);
                assertNotNull(ShipSideAccess.aimPoint(eye, new Vec3(-side, 0, 0), 3, side,
                        profile.seatLocal(), profile.height()));
                assertNull(ShipSideAccess.aimPoint(eye, new Vec3(side, 0, 0), 3, side,
                        profile.seatLocal(), profile.height()));
                assertNull(ShipSideAccess.aimPoint(eye, new Vec3(-side, 0, 0), .01, side,
                        profile.seatLocal(), profile.height()));
                assertFalse(ShipSideAccess.besideCockpit(feet.add(0, 0, -2), yaw, player.width(), profile.seatLocal()));
            }
        }
    }
}
