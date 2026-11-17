package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.Rac1VendorPose;
import com.github.giannigr.racmc.block.Rac1VendorFit;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class Rac1VendorWorldScaleTest {
    private static final float[] CLOSED = {-1.1515f, -.002f, -1.1515f,
            1.1515f, 1.262f, 1.1515f};

    @Test
    void exactTwoTimesWorldScaleKeepsGroundAndHorizontalCenter() {
        var item = Rac1VendorFit.of(CLOSED);
        var world = item.inWorld();
        assertEquals(2 * item.scale(), world.scale());
        Vector3f min = transformed(world, CLOSED[0], CLOSED[1], CLOSED[2]);
        Vector3f max = transformed(world, CLOSED[3], CLOSED[4], CLOSED[5]);
        assertEquals(-.5f, min.x, 1e-6);
        assertEquals(-.5f, min.z, 1e-6);
        assertEquals(1.5f, max.x, 1e-6);
        assertEquals(1.5f, max.z, 1e-6);
        assertEquals(0, min.y, 1e-6);
        assertEquals(.5f, (min.x + max.x) / 2, 1e-6);
        assertEquals(2 * 1.264f / 2.303f, max.y, 1e-6);
    }

    @Test
    void worldOffsetAndLogoScaleGrowTogetherButItemStillFitsOneCube() {
        var item = Rac1VendorFit.of(CLOSED);
        var world = item.inWorld();
        Vector3f itemLogo = transformed(item, 0, Rac1VendorPose.HOLOGRAM_HEIGHT, 0);
        Vector3f worldLogo = transformed(world, 0, Rac1VendorPose.HOLOGRAM_HEIGHT, 0);
        assertEquals(2 * itemLogo.y, worldLogo.y, 1e-6);
        assertEquals(itemLogo.x, worldLogo.x, 1e-6);
        assertEquals(itemLogo.z, worldLogo.z, 1e-6);
        assertEquals(2 * item.scale() * Rac1VendorPose.HOLOGRAM_SCALE,
                world.scale() * Rac1VendorPose.HOLOGRAM_SCALE, 1e-6);
        assertEquals(0, transformed(item, CLOSED[0], CLOSED[1], CLOSED[2]).x, 1e-6);
        assertEquals(1, transformed(item, CLOSED[3], CLOSED[4], CLOSED[5]).x, 1e-6);
    }

    @Test
    void asymmetricGeometryIsEnlargedAroundGroundedCenterNotBlockCorner() {
        var item = Rac1VendorFit.of(new float[] {-3, -1, -2, 1, 1, 0});
        var world = item.inWorld();
        Vector3f min = transformed(world, -3, -1, -2);
        Vector3f max = transformed(world, 1, 1, 0);
        assertEquals(.5f, (min.x + max.x) / 2, 1e-6);
        assertEquals(.5f, (min.z + max.z) / 2, 1e-6);
        assertEquals(0, min.y, 1e-6);
        assertEquals(2, max.x - min.x, 1e-6);
        assertEquals(1, max.z - min.z, 1e-6);
    }

    private static Vector3f transformed(Rac1VendorFit fit, float x, float y, float z) {
        PoseStack pose = new PoseStack();
        Rac1VendorDrawing.place(pose, fit);
        return pose.last().pose().transformPosition(new Vector3f(x, y, z));
    }
}
