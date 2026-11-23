package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.giannigr.racmc.asset.ship.GreenShipFxBeats;
import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GreenShipFlamePassTest {
    @Test
    void borrowedMaterialSubmitsAllEightQuadsInScaledShipAxes() {
        var quads = GreenShipFxGeometry.flames(GreenShipFxBeats.takeoff(460), 1);
        var type = RenderType.lightning(); // Identity sentinel only: this fixture never prepares/draws a GPU pass.
        var sink = new Sink();
        GreenShipFlamePass.draw(type, new PoseStack().last(), material -> {
            assertEquals(type, material);
            return sink;
        }, quads);
        assertEquals(32, sink.points.size());
        var source = quads.getFirst().corners().getFirst();
        Vec3 actual = sink.points.getFirst();
        double fit = ShipCockpitRefit.activeProfile().blocksPerSourceUnit();
        assertEquals(source.side() * fit, actual.x, .00001);
        assertEquals(source.up() * fit, actual.y, .00001);
        assertEquals(source.forward() * fit, actual.z, .00001);
    }

    @Test
    void absentOriginalMaterialAndInactiveBeatNeverOpenABuffer() {
        var quads = GreenShipFxGeometry.flames(GreenShipFxBeats.takeoff(460), 1);
        GreenShipFlamePass.draw(null, null, ignored -> fail("No texture fallback"), quads);
        GreenShipFlamePass.draw(RenderType.lightning(), null, ignored -> fail("No inactive draw"), List.of());
    }

    private static final class Sink implements VertexConsumer {
        final List<Vec3> points = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            points.add(new Vec3(x, y, z));
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return fail("Unexpected overlay channel"); }
        @Override public VertexConsumer setUv2(int u, int v) { return fail("Unexpected light channel"); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return fail("Unexpected normal channel"); }
    }
}
