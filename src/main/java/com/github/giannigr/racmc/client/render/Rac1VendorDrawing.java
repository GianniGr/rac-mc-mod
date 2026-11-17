package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1VendorAsset;
import com.github.giannigr.racmc.asset.Rac1VendorPose;
import com.github.giannigr.racmc.block.Rac1VendorFit;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/** Shared block/item drawing. Both receive the vanilla block-cube CORNER origin. */
public final class Rac1VendorDrawing {
    private static Rac1VendorAsset source;
    private static Rac1VendorPose.Mesh closed;
    private static Rac1VendorFit fit;
    private static Rac1VendorFit itemFit;

    private Rac1VendorDrawing() {
    }

    public static void drawInBlock(Rac1CacheContent content, PoseStack pose,
                                    MultiBufferSource buffers, int light, float growth, float seconds) {
        draw(content, pose, buffers, light, growth, seconds, true);
    }

    /** Items fit their main enclosure; antenna tips may protrude just like other item details. */
    public static void drawAsItem(Rac1CacheContent content, PoseStack pose,
                                   MultiBufferSource buffers, int light) {
        draw(content, pose, buffers, light, 0, 0, false);
    }

    private static void draw(Rac1CacheContent content, PoseStack pose, MultiBufferSource buffers,
                             int light, float growth, float seconds, boolean inWorld) {
        Rac1VendorAsset vendor = content.vendor();
        if (vendor == null) {
            return;
        }
        if (source != vendor) {
            closed = Rac1VendorPose.closed(vendor);
            fit = Rac1VendorFit.of(closed.positions());
            itemFit = Rac1VendorFit.forItem(vendor.body().mesh(), closed.positions());
            source = vendor;
        }
        Rac1VendorTextures.Pair<ResourceLocation> textures = Rac1VendorTextures.SHARED.of(content);
        pose.pushPose();
        try {
            // Translate BEFORE scaling: the half-block cube origin must not itself be scaled.
            place(pose, inWorld ? fit.inWorld() : itemFit);
            boolean close = buffers instanceof MultiBufferSource.BufferSource;
            Rac1MeshRenderer.render(pose, buffers, vendor.body().mesh(), closed.positions(),
                    closed.normals(), textures.body().toArray(ResourceLocation[]::new), light, close);
            if (growth > 0) {
                pose.translate(0, Rac1VendorPose.HOLOGRAM_HEIGHT, 0);
                float scale = Rac1VendorPose.HOLOGRAM_SCALE * Math.min(1, growth);
                pose.scale(scale, scale, scale);
                Rac1VendorPose.Mesh logo = Rac1VendorPose.hologram(vendor, seconds);
                Rac1MeshRenderer.render(pose, buffers, vendor.hologram().mesh(), logo.positions(),
                        logo.normals(), textures.hologram().toArray(ResourceLocation[]::new),
                        Rac1MeshRenderer.FULL_LIGHT, close);
            }
        } finally {
            pose.popPose();
        }
    }

    public static void clear() {
        source = null;
        closed = null;
        fit = null;
        itemFit = null;
        Rac1VendorTextures.SHARED.clear();
    }

    static void place(PoseStack pose, Rac1VendorFit fit) {
        pose.translate(fit.x(), fit.y(), fit.z());
        pose.scale(fit.scale(), fit.scale(), fit.scale());
    }
}
