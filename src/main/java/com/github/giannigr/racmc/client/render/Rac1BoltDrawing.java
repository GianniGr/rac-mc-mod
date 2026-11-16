package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Bolts;
import com.github.giannigr.racmc.client.RacMcClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** Original class dimensions and original denomination scale, never normalized to a crate cube. */
public final class Rac1BoltDrawing {
    private Rac1BoltDrawing() {}
    public static void draw(String key, PoseStack pose, MultiBufferSource buffers, int light) {
        var content = RacMcClient.assets().content().orElse(null);
        if (content == null) return;
        var asset = content.crate(key);
        if (asset == null) return;
        pose.pushPose();
        float scale = Rac1Bolts.scale(asset.classNumber());
        pose.scale(scale, scale, scale);
        Rac1MeshRenderer.render(pose, buffers, asset.mesh(),
                Rac1CrateTextures.of(content, asset), light, false);
        pose.popPose();
    }
}
