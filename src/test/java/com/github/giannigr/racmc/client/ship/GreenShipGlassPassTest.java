package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.giannigr.racmc.asset.ship.GreenShipCanopy;
import com.github.giannigr.racmc.asset.ship.GreenShipHullPose;
import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class GreenShipGlassPassTest {
    private static final GreenShipGlassPass.Table TABLE = new GreenShipGlassPass.Table(
            List.of(new GreenShipGlassPass.Point(new Vec3(1, 0, 1), new Vec3(0, 0, 1))),
            List.of(new GreenShipGlassPass.Quad(0, 0, 0, 0)), 0xff778899);

    @Test
    void poseChangeMovesGlassInShipAxesAndCameraChangesOnlyItsUvs() {
        var bind = new Matrix4f();
        var raised = new Matrix4f().translation(0, 0, 2);
        var facing = GreenShipGlassPass.project(TABLE, bind, Vec3.ZERO).getFirst();
        var side = GreenShipGlassPass.project(TABLE, bind, new Vec3(0, 4, 0)).getFirst();
        var raisedVertex = GreenShipGlassPass.project(TABLE, raised, Vec3.ZERO).getFirst();
        assertEquals(facing.blocks(), side.blocks());
        assertNotEquals(facing.v(), side.v());
        assertEquals(2 * ShipCockpitRefit.activeProfile().blocksPerSourceUnit(),
                raisedVertex.blocks().y - facing.blocks().y, .0001);
        assertTrue(Float.isFinite(raisedVertex.u()));
        assertTrue(Float.isFinite(raisedVertex.v()));
    }

    @Test
    void rejectsTruncatedOrOutOfRangeGeometryAndHasNoTextureFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> new GreenShipGlassPass.Table(TABLE.points(),
                        List.of(new GreenShipGlassPass.Quad(0, 0, 1, 0)), 0));
        GreenShipGlassPass.draw(TABLE, null, null, false, null, null, null);
        GreenShipGlassPass.draw(null, null, null, false, null, null, null);
    }

    @Test
    void submittedCanopyBorrowsTheExactMaterialAndUsesTheMatchingHullJointPose() {
        var vectors = List.of(new GreenShipCanopy.Vector(1, 0, 0),
                new GreenShipCanopy.Vector(1, 1, 0), new GreenShipCanopy.Vector(2, 0, 0),
                new GreenShipCanopy.Vector(2, 1, 0));
        var geometry = new GreenShipCanopy.Geometry(vectors,
                List.of(new GreenShipCanopy.Quad(0, 1, 2, 3)), vectors, 0x30807060);
        var table = GreenShipGlassPass.prepare(geometry);
        assertEquals(0x5fbfdfff, table.argb());

        var hull = new GreenShipHullPose(null, null, new Matrix4f().get(new float[16]),
                new Matrix4f().translation(0, 0, 2).get(new float[16]));
        var material = RenderType.lightning(); // Identity sentinel; no render-state setup in the test.
        var capture = new Sink();
        GreenShipGlassPass.draw(table, material, hull, true, new Vec3(4, 2, 3),
                new PoseStack(), borrowed -> {
                    assertEquals(material, borrowed);
                    return capture;
                });
        assertEquals(4, capture.vertices.size());
        double fit = ShipCockpitRefit.activeProfile().blocksPerSourceUnit();
        assertEquals(List.of(0, 1, 1, 0), capture.vertices.stream()
                .map(p -> (int) Math.round(p.x / fit)).toList()); // Source strip -> perimeter.
        assertTrue(capture.vertices.stream().allMatch(p -> Math.abs(p.y - 2 * fit) < .00001));
        assertEquals(List.of(0x5fbfdfff, 0x5fbfdfff, 0x5fbfdfff, 0x5fbfdfff), capture.argb);
    }

    private static final class Sink implements VertexConsumer {
        final List<Vec3> vertices = new ArrayList<>();
        final List<Integer> argb = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new Vec3(x, y, z));
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            argb.add(a << 24 | r << 16 | g << 8 | b);
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) {
            assertTrue(Float.isFinite(u) && Float.isFinite(v));
            return this;
        }
        @Override public VertexConsumer setUv1(int u, int v) { return fail("Unexpected overlay channel"); }
        @Override public VertexConsumer setUv2(int u, int v) { return fail("Unexpected light channel"); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return fail("Unexpected normal channel"); }
    }
}
