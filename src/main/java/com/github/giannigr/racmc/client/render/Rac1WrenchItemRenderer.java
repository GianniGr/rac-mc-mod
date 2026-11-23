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
import com.github.giannigr.racmc.client.actions.WrenchActionClient;
import com.github.giannigr.racmc.client.actions.WrenchPlayerPoseHook;
import com.github.giannigr.racmc.actions.WrenchActionPresentation;
import net.minecraft.world.entity.HumanoidArm;
import com.github.giannigr.racmc.actions.WrenchActionGeometry;

/** One accepted action snapshot drives held and detached imported sequence sampling. */
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
        var actor = WrenchPlayerPoseHook.actor();
        boolean third = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean primary = actor != null && (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                == (actor.getMainArm() == HumanoidArm.RIGHT);
        if (third && primary) {
            var view = WrenchActionClient.presentation(actor, WrenchPlayerPoseHook.actorPartialTick());
            if (view.phase() != WrenchActionPresentation.Phase.IDLE) {
                if (view.heldVisible()) {
                    draw(context, poses, buffers, light, view.wrenchSequence(),
                            (float) view.wrenchClipSeconds(), true, (float) view.wrenchBlendWeight(), 1,
                            com.github.giannigr.racmc.client.actions.WrenchActionRendering
                                    .itemAdjustment(actor, view));
                }
                return;
            }
        }
        renderSequence(context, poses, buffers, light, 1, 0);
    }

    /** Read-only item drawing seam for the one P28 presentation clock after consolidation. */
    public void renderSequence(ItemDisplayContext context, PoseStack poses,
                               MultiBufferSource buffers, int light, int sequence, float seconds) {
        draw(context, poses, buffers, light, sequence, seconds, true, 1, 1);
    }

    /** Draw at an already composed world/socket transform, without vanilla item-cube correction. */
    public void renderDetached(PoseStack poses, MultiBufferSource buffers, int light,
                               int sequence, float seconds) {
        draw(ItemDisplayContext.NONE, poses, buffers, light, sequence, seconds, false, 1, 1);
    }

    public void renderAction(PoseStack poses, MultiBufferSource buffers, int light,
            WrenchActionPresentation view, boolean detached) {
        draw(ItemDisplayContext.NONE, poses, buffers, light, view.wrenchSequence(),
                (float) view.wrenchClipSeconds(), false, (float) view.wrenchBlendWeight(),
                detached && view.wrenchSequence() == 1 ? 6 : 1);
    }

    private void draw(ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers,
                       int light, int sequence, float seconds, boolean itemCube,
                       float blend, int fromSequence) {
        draw(context, poses, buffers, light, sequence, seconds, itemCube, blend, fromSequence, null);
    }

    private void draw(ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers,
                       int light, int sequence, float seconds, boolean itemCube,
                       float blend, int fromSequence, org.joml.Matrix4f adjustment) {
        var content = RacMcClient.assets().content().orElse(null);
        var asset = content == null ? null : content.wrench();
        if (asset == null) {
            textures.clear();
            sampledSource = null;
            held = null;
            return;
        }
        if (sampledSource != asset || sampledSequence != sequence || sampledSeconds != seconds) {
            var sampled = WrenchActionGeometry.mesh(asset, sequence, seconds);
            held = new Rac1ImportedItemPose.Sample(sampled.positions(), sampled.normals());
            sampledSource = asset;
            sampledSequence = sequence;
            sampledSeconds = seconds;
        }
        if (held.positions().length == 0
                || !(buffers instanceof MultiBufferSource.BufferSource source)) {
            return;
        }
        var locations = textures.get(content, Rac1ImportedItemDrawing.epoch(), asset.textures());
        var sample = held;
        if (blend < 1 && fromSequence != sequence) {
            var from = Rac1ImportedItemPose.sample(asset, fromSequence, 0);
            if (from.positions().length == held.positions().length) {
                float[] positions = held.positions().clone();
                float[] normals = held.normals().clone();
                for (int i = 0; i < positions.length; i++) {
                    positions[i] = from.positions()[i] * (1 - blend) + positions[i] * blend;
                    normals[i] = from.normals()[i] * (1 - blend) + normals[i] * blend;
                }
                sample = new Rac1ImportedItemPose.Sample(positions, normals);
            }
        }
        if (itemCube) {
            Rac1ImportedItemDrawing.draw(poses, source, asset, sample.positions(), sample.normals(),
                    locations.toArray(ResourceLocation[]::new), context, light, true,
                    frame -> applyItemSocket(frame, context, adjustment));
        } else {
            poses.pushPose();
            try {
                // Both managed held viewmodel and the detached proxy receive this after
                // composing the same unscaled grip/release/catch socket.
                scaleSteveMesh(poses);
                Rac1MeshRenderer.render(poses, source, asset.mesh(), sample.positions(), sample.normals(),
                        locations.toArray(ResourceLocation[]::new), light);
            } finally {
                poses.popPose();
            }
        }
    }

    static void applyItemSocket(PoseStack frame, ItemDisplayContext context, org.joml.Matrix4f adjustment) {
        if (adjustment != null) frame.mulPose(adjustment);
        switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                    THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> scaleSteveMesh(frame);
            default -> { } // Inventory, ground, fixed, head and other displays keep the source scale.
        }
    }

    static void scaleSteveMesh(PoseStack poses) {
        float scale = WrenchActionGeometry.STEVE_MESH_SCALE;
        poses.scale(scale, scale, scale);
    }
}
