package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import com.github.giannigr.racmc.client.RacMcClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Held sequence 1 only. P28 presentation is intentionally not coupled before consolidation. */
public final class Rac1WrenchItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final Rac1ItemTextureLease<ResourceLocation> textures =
            Rac1ImportedItemDrawing.textures("omniwrench_item");
    private Object sampledSource;
    private Rac1ImportedItemPose.Sample held;
    private int sampledSequence = -1;
    private float sampledSeconds = -1;

    public Rac1WrenchItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        renderSequence(context, poses, buffers, light, 1, 0);
    }

    /** Read-only item drawing seam for the one P28 presentation clock after consolidation. */
    public void renderSequence(ItemDisplayContext context, PoseStack poses,
                               MultiBufferSource buffers, int light, int sequence, float seconds) {
        draw(context, poses, buffers, light, sequence, seconds, true);
    }

    /** Draw at an already composed world/socket transform, without vanilla item-cube correction. */
    public void renderDetached(PoseStack poses, MultiBufferSource buffers, int light,
                               int sequence, float seconds) {
        draw(ItemDisplayContext.NONE, poses, buffers, light, sequence, seconds, false);
    }

    private void draw(ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers,
                      int light, int sequence, float seconds, boolean itemCube) {
        var content = RacMcClient.assets().content().orElse(null);
        var asset = content == null ? null : content.wrench();
        if (asset == null) {
            textures.clear();
            sampledSource = null;
            held = null;
            return;
        }
        if (sampledSource != asset || sampledSequence != sequence || sampledSeconds != seconds) {
            held = Rac1ImportedItemPose.sample(asset, sequence, seconds);
            sampledSource = asset;
            sampledSequence = sequence;
            sampledSeconds = seconds;
        }
        if (held.positions().length == 0
                || !(buffers instanceof MultiBufferSource.BufferSource source)) {
            return;
        }
        var locations = textures.get(content, Rac1ImportedItemDrawing.epoch(), asset.textures());
        if (itemCube) {
            Rac1ImportedItemDrawing.draw(poses, source, asset, held.positions(), held.normals(),
                    locations.toArray(ResourceLocation[]::new), context, light, true);
        } else {
            Rac1MeshRenderer.render(poses, source, asset.mesh(), held.positions(), held.normals(),
                    locations.toArray(ResourceLocation[]::new), light);
        }
    }
}
