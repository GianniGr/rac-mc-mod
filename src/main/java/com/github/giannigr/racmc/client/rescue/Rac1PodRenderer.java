package com.github.giannigr.racmc.client.rescue;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.rescue.Rac1PodMarker;
import com.github.giannigr.racmc.rescue.Rac1PodFootprint;
import com.github.giannigr.racmc.rescue.Rac1PodRegistration;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class Rac1PodRenderer implements BlockEntityRenderer<Rac1PodMarker> {
    private Rac1PodClientResources.Art failed;
    private long observedGeneration = Long.MIN_VALUE;

    public Rac1PodRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Rac1PodRegistration.MARKER.get(), Rac1PodRenderer::new);
    }

    @Override
    public AABB getRenderBoundingBox(Rac1PodMarker marker) {
        var pos = marker.getBlockPos();
        return Rac1PodFootprint.full(marker.getBlockState())
                ? new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                        pos.getX() + 2, pos.getY() + 1, pos.getZ() + 2)
                : new AABB(pos);
    }

    @Override
    public void render(Rac1PodMarker marker, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light, int overlay) {
        if (marker.getLevel() == null) return;
        boolean full = Rac1PodFootprint.full(marker.getBlockState());
        boolean intact = (marker.isNatural() || marker.isDecorative())
                && (!full || Rac1PodFootprint.complete(marker.getLevel(), marker.getBlockPos()));
        var art = intact ? Rac1PodClientResources.get() : null;
        if (art != failed) failed = null;
        if (intact && marker.isNatural() && RacMcMod.LOGGER.isDebugEnabled()) {
            long generation = RacMcClient.media().generation();
            if (observedGeneration != generation) {
                observedGeneration = generation;
                RacMcMod.LOGGER.debug("Rescue pod first natural render callback: generation={}, originalReady={}",
                        generation, art != null && art != failed);
            }
        }
        if (art == null || art == failed) {
            drawFallback(marker, full, poses, buffers, light, overlay);
            return;
        }
        try {
            var meshes = full ? art.prepared().full() : art.prepared().legacy();
            var hullTextures = art.hullTextures();
            boolean flush = buffers instanceof MultiBufferSource.BufferSource;
            Rac1MeshRenderer.render(poses, buffers, meshes.hull(), hullTextures, light, flush);
            if (Rac1PodViewer.showOccupant(marker.isNatural(), marker.isClaimed())) {
                Rac1MeshRenderer.render(poses, buffers, meshes.occupant(),
                        art.occupantTextures(), light, flush);
            }
            // Same shell and shared occupant state for every viewer.
            drawCanopy(poses, buffers, meshes.canopy(), hullTextures[0], light);
        } catch (RuntimeException failure) {
            failed = art;
            RacMcMod.LOGGER.warn("Rescue pod drawing disabled for these local resources.", failure);
        }
    }

    private static void drawFallback(Rac1PodMarker marker, boolean full, PoseStack poses,
                                     MultiBufferSource buffers, int light, int overlay) {
        int cells = full && (marker.isNatural() || marker.isDecorative()) ? Rac1PodFootprint.CELLS.size() : 1;
        var model = Minecraft.getInstance().getBlockRenderer();
        var anchor = marker.getBlockPos();
        // Clearly authored dark, low-poly wreck silhouette, not a pedestal or a second mesh.
        for (int i = 0; i < cells; i++) {
            var cell = Rac1PodFootprint.CELLS.get(i);
            if (i != 0 && !Rac1PodFootprint.isPresentPart(marker.getLevel(), cell.at(anchor), i)) continue;
            var bounds = Rac1PodFootprint.shape(full, i).bounds();
            poses.pushPose();
            try {
                poses.translate(cell.x() + bounds.minX, bounds.minY, cell.z() + bounds.minZ);
                poses.scale((float) bounds.getXsize(), (float) bounds.getYsize(), (float) bounds.getZsize());
                model.renderSingleBlock(Blocks.POLISHED_DEEPSLATE.defaultBlockState(), poses, buffers, light, overlay);
            } finally {
                poses.popPose();
            }
        }
    }

    private static void drawCanopy(PoseStack poses, MultiBufferSource buffers, Rac1Mesh mesh,
                                   ResourceLocation texture, int light) {
        var type = RenderType.entityTranslucent(texture);
        var sink = buffers.getBuffer(type);
        // 27% Minecraft glass opacity on the genuine window pocket; no polygons moved or opened.
        int alpha = 69;
        float[] positions = mesh.positions(), normals = mesh.normals(), uvs = mesh.uvs();
        int[] indices = mesh.indices();
        for (int i = 0; i < indices.length; i += 3) {
            for (int corner = 0; corner < 4; corner++) {
                int vertex = indices[i + corner % 3];
                int n = vertex * 3;
                sink.addVertex(poses.last(), positions[n], positions[n + 1], positions[n + 2])
                        .setColor(255, 255, 255, alpha)
                        .setUv(uvs[2 * vertex], uvs[2 * vertex + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(poses.last(), normals[n], normals[n + 1], normals[n + 2]);
            }
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
    }
}
