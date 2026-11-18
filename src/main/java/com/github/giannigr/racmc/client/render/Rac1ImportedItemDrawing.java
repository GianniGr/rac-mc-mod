package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Shared mesh submission and texture lifecycle, not shared weapon/action behavior. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class Rac1ImportedItemDrawing {
    private static long epoch;
    private static final List<Rac1ItemTextureLease<ResourceLocation>> LEASES = new ArrayList<>();

    private Rac1ImportedItemDrawing() { }

    public static Rac1ItemTextureLease<ResourceLocation> textures(String prefix) {
        var lease = new Rac1ItemTextureLease<ResourceLocation>(
                (index, texture) -> Rac1ClientTextures.register(prefix + "_" + index, texture),
                Rac1ClientTextures::release);
        LEASES.add(lease);
        return lease;
    }

    public static long epoch() {
        return epoch;
    }

    @SubscribeEvent
    public static void onReloadRegistration(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            epoch++;
            LEASES.forEach(Rac1ItemTextureLease::clear);
        });
    }

    /** Correct the vanilla item cube exactly once; never center a held/socket-relative mesh. */
    public static void draw(PoseStack poses, MultiBufferSource.BufferSource buffers,
                            Rac1AnimatedAsset asset, float[] positions, float[] normals,
                            ResourceLocation[] textures, ItemDisplayContext context, int light,
                            boolean fitGui) {
        draw(poses, buffers, asset, positions, normals, textures, context, light, fitGui,
                ignored -> { });
    }

    public static void draw(PoseStack poses, MultiBufferSource.BufferSource buffers,
                            Rac1AnimatedAsset asset, float[] positions, float[] normals,
                            ResourceLocation[] textures, ItemDisplayContext context, int light,
                            boolean fitGui, java.util.function.Consumer<PoseStack> socketAdjustment) {
        poses.pushPose();
        try {
            restoreSocketOrigin(poses);
            socketAdjustment.accept(poses);
            if (fitGui && context == ItemDisplayContext.GUI) {
                fitGui(poses, positions);
            }
            Rac1MeshRenderer.render(poses, buffers, asset.mesh(), positions, normals, textures,
                    context == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light);
        } finally {
            poses.popPose();
        }
    }

    public static void restoreSocketOrigin(PoseStack poses) {
        poses.translate(0.5, 0.5, 0.5);
    }

    /** Fit a posed bounding sphere so arbitrary GUI rotation cannot clip the silhouette. */
    public static void fitGui(PoseStack poses, float[] positions) {
        float[] box = Rac1ImportedItemPose.bounds(positions);
        if (box == null) {
            return;
        }
        float x = box[3] - box[0];
        float y = box[4] - box[1];
        float z = box[5] - box[2];
        float diameter = (float) Math.sqrt(x * x + y * y + z * z);
        if (diameter > 0) {
            float scale = 0.8f / diameter;
            poses.scale(scale, scale, scale);
        }
        poses.translate(-(box[0] + box[3]) / 2,
                -(box[1] + box[4]) / 2, -(box[2] + box[5]) / 2);
    }
}
