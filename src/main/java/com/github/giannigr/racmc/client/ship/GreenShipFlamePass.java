package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipHullPose;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Ship-local, depth-tested additive flame pass; never obtains or substitutes a texture. */
public final class GreenShipFlamePass {
    private GreenShipFlamePass() {}

    /** Uses the same source-space transform as the hull, with no change to the camera or world. */
    public static void draw(RenderType prepared, PoseStack.Pose shipToRender,
                            MultiBufferSource buffers, List<GreenShipFxGeometry.Quad> quads) {
        if (prepared == null || quads.isEmpty()) {
            return;
        }
        var target = buffers.getBuffer(prepared);
        float scale = GreenShipHullPose.BLOCKS_PER_UNIT;
        for (var quad : quads) {
            for (var vertex : quad.corners()) {
                target.addVertex(shipToRender, vertex.side() * scale, vertex.up() * scale,
                                vertex.forward() * scale)
                        .setColor(quad.argb()).setUv(vertex.u(), vertex.v());
            }
        }
    }
}
