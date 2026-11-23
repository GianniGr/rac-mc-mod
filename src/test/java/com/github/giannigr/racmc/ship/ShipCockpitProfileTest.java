package com.github.giannigr.racmc.ship;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShipCockpitProfileTest {
    @Test
    void classicAndSlimUseIdenticalVanillaCollisionAndEyeContract() {
        var profile = ShipCockpitProfile.CURRENT;
        var hull = EntityDimensions.fixed(profile.width(), profile.height()).makeBoundingBox(Vec3.ZERO);
        var standing = EntityType.PLAYER.getDimensions();
        for (var model : PlayerSkin.Model.values()) {
            assertTrue(java.util.stream.IntStream.range(0, 64)
                    .anyMatch(i -> DefaultPlayerSkin.get(new UUID(0, i)).model() == model));
            assertTrue(ShipCockpitProfile.containsPilot(hull, profile.seatLocal(), standing));
            assertTrue(profile.fitsAtAllHeadings(standing));
            assertTrue(profile.seatLocal().y + standing.eyeHeight() <= hull.maxY);
        }
        assertEquals(1.4, ShipCockpitProfile.exitDistance(hull, standing), 1e-6);
        assertTrue(ShipCockpitProfile.outsideHull(hull, new Vec3(1.4, 0, 0), standing));
    }

    @Test
    void clearanceChecksEveryAxisAndRecomputesExitWithMeasuredEnvelope() {
        // Synthetic geometry, not a class 531 fit.
        var hull = EntityDimensions.fixed(2.6F, 3F).makeBoundingBox(Vec3.ZERO);
        var standing = EntityDimensions.fixed(.6F, 1.8F);
        assertTrue(ShipCockpitProfile.containsPilot(hull, new Vec3(.5, 1, .5), standing));
        assertFalse(ShipCockpitProfile.containsPilot(hull, new Vec3(-1.1, 1, 0), standing));
        assertFalse(ShipCockpitProfile.containsPilot(hull, new Vec3(0, 1, 1.1), standing));
        assertFalse(ShipCockpitProfile.containsPilot(hull, new Vec3(0, -.01, 0), standing));
        assertFalse(ShipCockpitProfile.containsPilot(hull, new Vec3(0, 1.3, 0), standing));
        assertFalse(ShipCockpitProfile.outsideHull(hull, new Vec3(1.4, 0, 0), standing));
        double exit = ShipCockpitProfile.exitDistance(hull, standing);
        assertEquals(1.8, exit, 1e-6);
        assertTrue(ShipCockpitProfile.outsideHull(hull, new Vec3(exit, 0, 0), standing));
        var angleSensitive = new ShipCockpitProfile(.20, 1.8F, 2.45F, new Vec3(.45, .54, .45), Vec3.ZERO);
        var originalHull = EntityDimensions.fixed(1.8F, 2.45F).makeBoundingBox(Vec3.ZERO);
        assertTrue(ShipCockpitProfile.containsPilot(originalHull, angleSensitive.seatLocal(), standing));
        assertFalse(angleSensitive.fitsAtAllHeadings(standing));
        assertThrows(IllegalArgumentException.class,
                () -> new ShipCockpitProfile(.2, 1.8F, 2.45F, new Vec3(1, .5, 0), Vec3.ZERO));
    }
}
