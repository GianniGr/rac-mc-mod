package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GreenShipCockpitInteriorTest {
    private static final double EPSILON = 1e-4; // Pixel-space contact tolerance, not world clearance.

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testedSkinAndWearPartsTouchSupportsWithoutPenetratingAndLeaveLookUntouched(boolean slim) {
        var model = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),
                64, 64).bakeRoot(), slim);
        var actor = new Object();
        var pose = new PoseStack();
        var seat = ShipCockpitRefit.CANDIDATE.seatLocal();
        pose.translate(seat.x, seat.y, seat.z);
        pose.scale(.9375f, -.9375f, -.9375f);
        pose.translate(0, -1.501, 0);
        model.rightLeg.yRot = .314f;
        model.leftPants.zRot = -.05f;
        model.head.xRot = model.hat.xRot = .9f;
        model.head.yRot = model.hat.yRot = -1.1f;

        GreenShipPlayerRender.renderRefitSeat(actor, model, pose, Vec3.ZERO, 0, posed -> {
            GreenShipPlayerRender.afterSetup(actor, model);
            assertEquals(.9f, model.head.xRot);
            assertEquals(-1.1f, model.hat.yRot);
            assertEquals(0, model.rightLeg.yRot);
            assertEquals(0, model.leftPants.zRot);
            // Vanilla PlayerModel uses the negative-X right leg and positive-X left leg.
            verifySide(-1, posed, model.rightLeg, model.rightPants, model);
            verifySide(1, posed, model.leftLeg, model.leftPants, model);
        });
        assertEquals(.314f, model.rightLeg.yRot);
        assertEquals(-.05f, model.leftPants.zRot);
    }

    private static void verifySide(int side, PoseStack posed, ModelPart leg, ModelPart pants, PlayerModel<?> model) {
        var legToShip = new PoseStack();
        GreenShipCockpitInterior.legToShip(legToShip, side);
        var toLocal = new Matrix4f(legToShip.last().pose()).invert();
        var bare = bounds(points(leg, posed), toLocal);
        var layer = bounds(points(pants, posed), toLocal);
        var cushion = GreenShipCockpitInterior.CUSHION;
        var pedal = GreenShipCockpitInterior.PEDAL;
        assertEquals(cushion.minZ, layer.maxZ, EPSILON, "pants touch the cushion");
        assertEquals(pedal.minY, layer.maxY, EPSILON, "pants sole touches the pedal");
        assertEquals(.25, cushion.minZ - bare.maxZ, EPSILON, "unlayered gap is just the skin inflation");
        assertEquals(.25, pedal.minY - bare.maxY, EPSILON);
        assertTrue(layer.minX < pedal.minX && layer.maxX > pedal.maxX);
        for (var part : List.of(model.head, model.hat, model.body, model.jacket, model.rightArm, model.leftArm,
                model.rightSleeve, model.leftSleeve, model.rightLeg, model.leftLeg, model.rightPants, model.leftPants)) {
            var transformed = bounds(points(part, posed), toLocal);
            assertFalse(transformed.intersects(cushion.deflate(EPSILON)), () -> "Cushion enters " + transformed);
            assertFalse(transformed.intersects(pedal.deflate(EPSILON)), () -> "Pedal enters " + transformed);
        }
        var inShip = legToShip.last().pose();
        for (var support : List.of(cushion, pedal)) {
            for (var corner : corners(support)) {
                var world = transform(inShip, corner);
                assertTrue(world.y < ShipCockpitRefit.CANDIDATE.seatLocal().y + 1.62 - .5,
                        "Authored support must be well below the forward eye ray");
                var c = ShipCockpitRefit.OPENING;
                assertTrue(world.x > c.minX() && world.x < c.maxX() && world.y > c.minY()
                        && world.y < c.maxY() && world.z > c.minZ() && world.z < c.maxZ(),
                        "Authored support must stay entirely within the local opening");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void conservativeAllAngleHeadSphereFitsOpaqueOpeningButExceedsPriorGlassProofByThirtyMilliblocks(boolean slim) {
        var model = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),
                64, 64).bakeRoot(), slim);
        var scale = new PoseStack();
        scale.scale(.9375f, -.9375f, -.9375f);
        double radius = points(model.hat, scale).stream().mapToDouble(Vec3::length).max().orElseThrow();
        assertEquals(.62217, radius, .001);
        var p = ShipCockpitRefit.CANDIDATE.seatLocal().add(0, GreenShipCockpitInterior.BODY_DATUM, 0);
        var c = ShipCockpitRefit.OPENING;
        assertTrue(p.x - radius > c.minX() && p.x + radius < c.maxX());
        assertTrue(p.x - radius - .025 > c.minX() && p.x + radius + .025 < c.maxX(),
                "The restored side strips cannot enter the padded all-angle head sphere");
        assertTrue(p.y - radius > c.minY() && p.y + radius < c.maxY());
        assertTrue(p.z - radius > c.minZ() && p.z + radius < c.maxZ());
        // This is an evidence gap, not proof that the retail windshield actually intersects.
        var verifiedColumn = ShipCockpitRefit.portalColumn();
        assertTrue(p.x + radius > verifiedColumn.maxX);
        assertTrue(p.y - radius > verifiedColumn.minY && p.y + radius < verifiedColumn.maxY);
        assertTrue(p.z - radius > verifiedColumn.minZ && p.z + radius < verifiedColumn.maxZ);
    }

    private static List<Vec3> corners(AABB a) {
        var points = new ArrayList<Vec3>(8);
        for (double x : new double[]{a.minX, a.maxX})
            for (double y : new double[]{a.minY, a.maxY})
                for (double z : new double[]{a.minZ, a.maxZ}) points.add(new Vec3(x, y, z));
        return points;
    }

    private static AABB bounds(List<Vec3> vertices, Matrix4f transform) {
        var points = vertices.stream().map(v -> transform(transform, v)).toList();
        return new AABB(points.stream().mapToDouble(Vec3::x).min().orElseThrow(),
                points.stream().mapToDouble(Vec3::y).min().orElseThrow(),
                points.stream().mapToDouble(Vec3::z).min().orElseThrow(),
                points.stream().mapToDouble(Vec3::x).max().orElseThrow(),
                points.stream().mapToDouble(Vec3::y).max().orElseThrow(),
                points.stream().mapToDouble(Vec3::z).max().orElseThrow());
    }

    private static Vec3 transform(Matrix4f matrix, Vec3 v) {
        return new Vec3(matrix.transformPosition(v.toVector3f(), new Vector3f()));
    }

    private static List<Vec3> points(ModelPart part, PoseStack pose) {
        var sink = new Sink();
        part.render(pose, sink, 0, 0);
        return sink.vertices;
    }

    private static final class Sink implements VertexConsumer {
        final List<Vec3> vertices = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new Vec3(x, y, z));
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
