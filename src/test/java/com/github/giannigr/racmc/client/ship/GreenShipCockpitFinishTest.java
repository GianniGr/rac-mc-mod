package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GreenShipCockpitFinishTest {
    @Test
    void replacementFinishKeepsTheExactBakedContactBoxes() {
        checkBounds(GreenShipCockpitFinish.CUSHION_MESH, GreenShipCockpitInterior.CUSHION);
        checkBounds(GreenShipCockpitFinish.PEDAL_MESH, GreenShipCockpitInterior.PEDAL);
        var atlas = GreenShipCockpitFinish.atlas();
        assertEquals(32, atlas.width());
        assertEquals(32, atlas.height());
        for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) {
            assertEquals(255, atlas.pixel(x, y) >>> 24);
        }
        assertNotEquals(atlas.pixel(8, 8), atlas.pixel(24, 8)); // Cushion is not pedal metal.
    }

    @Test
    void linerSealsTheCutFootprintBelowTheRestoredFloorAndDoesNotEnterTheAccessColumn() {
        var opening = ShipCockpitRefit.OPENING;
        var floor = GreenShipCockpitFinish.FLOOR;
        checkBounds(GreenShipCockpitFinish.FLOOR_MESH, floor);
        assertTrue(floor.minX < opening.minX() && floor.maxX > opening.maxX());
        assertTrue(floor.minZ < opening.minZ() && floor.maxZ > opening.maxZ());
        assertTrue(floor.maxY < opening.minY());
        assertTrue(1.234 < opening.minY(), "Preserve the original low center-floor plane");
        assertTrue(opening.minY() < ShipCockpitRefit.portalColumn().minY);
        assertFalse(floor.intersects(ShipCockpitRefit.portalColumn()));
        for (double x : new double[]{-.84, 0, .84}) for (double z : new double[]{1.26, 2.12, 3.18}) {
            assertTrue(floor.clip(new Vec3(x, 3.2, z), new Vec3(x, 0, z)).isPresent());
        }
    }

    private static void checkBounds(Rac1Mesh mesh, AABB expected) {
        assertEquals(12, mesh.triangleCount());
        var box = new AABB(mesh.positions()[0], mesh.positions()[1], mesh.positions()[2],
                mesh.positions()[0], mesh.positions()[1], mesh.positions()[2]);
        for (int i = 0; i < mesh.positions().length; i += 3) {
            var p = new Vec3(mesh.positions()[i], mesh.positions()[i + 1], mesh.positions()[i + 2]);
            box = box.minmax(new AABB(p, p));
        }
        assertEquals(expected.minX, box.minX, 1e-6);
        assertEquals(expected.minY, box.minY, 1e-6);
        assertEquals(expected.minZ, box.minZ, 1e-6);
        assertEquals(expected.maxX, box.maxX, 1e-6);
        assertEquals(expected.maxY, box.maxY, 1e-6);
        assertEquals(expected.maxZ, box.maxZ, 1e-6);
    }
}
