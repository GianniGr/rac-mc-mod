package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipFxBeats;
import com.github.giannigr.racmc.effect.EffectMaterial;
import com.github.giannigr.racmc.ship.ShipThrust;
import java.util.ArrayList;
import java.util.List;

/** Geometry for the ship-local additive pass. Texture availability is the draw caller's gate. */
public final class GreenShipFxGeometry {
    private static final int SOURCE_COLOR = 0x2058b0; // GS R-low, neutral channel 128

    private GreenShipFxGeometry() {}

    public record Vertex(float forward, float side, float up, float u, float v) {}
    public record Quad(List<Vertex> corners, int argb) {}

    /**
     * Uses the renderer's hull transform, not an independent yaw/scale. Landing flicker is a
     * deterministic client adaptation, not PS2 RNG; drawing an extra view never changes it.
     */
    public static List<Quad> flames(GreenShipFxBeats.Sample sample, long seed) {
        if (sample.flameQuads() == 0) {
            return List.of();
        }
        var out = new ArrayList<Quad>(GreenShipFxBeats.FLAMES.size());
        for (var flame : GreenShipFxBeats.FLAMES) {
            // The source has +0xb2 = 10 while the landing actor is at command 0x32.
            int jitter = sample.landingFlicker() ? Math.floorMod(Long.hashCode(seed), 10) : 0;
            seed = Long.rotateLeft(seed ^ 0x9e3779b97f4a7c15L, 11);
            int intensity = sample.flameCommand() + jitter;
            float radius = intensity * flame.size() / 40;
            var a = flame.anchor();
            // Source strip indices 0, 1, 2, 3 in the hull's Y/Z plane; perimeter order for quads.
            var vertices = List.of(vertex(a, -radius, radius, 0, 0),
                    vertex(a, radius, radius, 1, 0), vertex(a, radius, -radius, 1, 1),
                    vertex(a, -radius, -radius, 0, 1));
            int rgba = EffectMaterial.rgba(intensity << 24 | SOURCE_COLOR);
            out.add(new Quad(vertices, Integer.rotateRight(rgba, 8)));
        }
        return List.copyOf(out);
    }

    private static Vertex vertex(GreenShipFxBeats.Anchor anchor, float y, float z, float u, float v) {
        return new Vertex(anchor.forward(), anchor.side() + y, anchor.up() + z, u, v);
    }

    /** Continuous Minecraft adaptation at the two class-531 fly-away trail anchors. */
    static List<Quad> rear(ShipThrust thrust, long tick) {
        if (thrust == ShipThrust.OFF) return List.of();
        boolean boost = thrust == ShipThrust.BOOST;
        float pulse = (float) Math.sin((tick & 7) * Math.PI / 4);
        float end = -3.5f - (boost ? 2.8f : 1.55f) - .22f * pulse;
        float width = boost ? .57f : .36f;
        int color = boost ? 0xffa6eaff : 0xcc78cafa;
        var jets = new ArrayList<Quad>(4);
        for (float side : new float[] {-1.5f, 1.5f}) {
            for (boolean vertical : new boolean[] {false, true}) {
                float x = vertical ? 0 : width, y = vertical ? width : 0;
                jets.add(new Quad(List.of(
                        new Vertex(-3.5f, side - x, 1.2f - y, 0, 0),
                        new Vertex(-3.5f, side + x, 1.2f + y, 0, 1),
                        new Vertex(end, side + x * .22f, 1.2f + y * .22f, 1, 1),
                        new Vertex(end, side - x * .22f, 1.2f - y * .22f, 1, 0)), color));
            }
        }
        return jets;
    }
}
