package com.github.giannigr.racmc.ship;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShipCockpitRefitTest {
    @Test
    void candidateHasSingleSeatAndStaysOutsideUndersizedBaseline() {
        var profile = ShipCockpitRefit.CANDIDATE;
        assertEquals(1.875 / 1.408195999, profile.blocksPerSourceUnit(), 1e-9);
        assertEquals(2.92, profile.seatLocal().y + 1.62, 1e-8);
        assertTrue(profile.fitsAtAllHeadings(EntityDimensions.fixed(.60f, 1.80f)));
        assertTrue(profile.width() / 2 > .776823 * profile.blocksPerSourceUnit() / .20);
        assertNotEquals(profile, ShipCockpitProfile.CURRENT);
    }

    @Test
    void threeLegPathOnlyDescendsIntoThePilotPortal() {
        var profile = ShipCockpitRefit.CANDIDATE;
        var outside = new Vec3(profile.width() / 2 + .50, 0, 0);
        var points = ShipCockpitRefit.waypoints(outside);
        assertEquals(outside.x, points.get(1).x);
        assertEquals(points.get(1).y, points.get(2).y);
        assertTrue(points.get(2).y > profile.height());
        assertEquals(points.get(3).x, points.get(2).x);
        assertEquals(points.get(3).z, points.get(2).z);
        assertEquals(profile.seatLocal(), points.getLast());
    }

    @Test
    void mediaIndependentFallbackLeavesPortalAndForwardEyeOpen() {
        var k = ShipCockpitRefit.CANDIDATE;
        var seat = k.seatLocal();
        var eye = seat.add(0, 1.62, 0);
        for (var part : ShipRefitFallback.parts()) {
            var b = part.bounds();
            assertFalse(b.intersects(ShipCockpitRefit.portalColumn()));
            assertTrue(b.clip(eye, eye.add(0, 0, 8)).isEmpty());
            for (double x : new double[]{b.minX, b.maxX})
                for (double z : new double[]{b.minZ, b.maxZ})
                    assertTrue(Math.hypot(x, z) <= k.width() / 2, b::toString);
            assertTrue(b.minY >= 0 && b.maxY <= k.height());
        }
    }

    @Test
    void sideStandClearanceUsesTheYawSafeEnvelopeNotTheVisibleWing() {
        var k = ShipCockpitRefit.CANDIDATE;
        var hull = EntityDimensions.fixed(k.width(), k.height()).makeBoundingBox(Vec3.ZERO);
        var player = EntityDimensions.fixed(.60f, 1.80f);
        assertFalse(ShipCockpitProfile.outsideHull(hull, new Vec3(3.20, 0, 2.12), player));
        assertTrue(ShipCockpitProfile.outsideHull(hull, new Vec3(5.70, 0, 2.12), player));
    }
}
