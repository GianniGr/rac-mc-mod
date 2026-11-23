package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.ship.GreenShipCanopy;
import com.github.giannigr.racmc.asset.ship.GreenShipCockpitOpening;
import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.ship.GreenShipHullLoader;
import com.github.giannigr.racmc.asset.ship.GreenShipHullPose;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient.ShipArt;
import com.github.giannigr.racmc.client.render.Rac1ClientTextures;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.iso.Rac1DiscCatalog;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import com.github.giannigr.racmc.iso.Rac1MediaManager;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Verified hull and optional NTSC canopy. No substitute glass, audio, scene reads or gameplay dependency. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class GreenShipHullRender {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "racmc-green-ship-assets");
        thread.setDaemon(true);
        return thread;
    });

    private record Prepared(long generation, GreenShipHullPose poses, Rac1Texture texture,
                            GreenShipGlassPass.Table glass, Rac1Mesh openParked, Rac1Mesh openClosed) {}

    // Main/render thread only. No new work is queued while an obsolete job is still running.
    private static Future<Prepared> pending;
    private static long requestedGeneration = -1;
    private static Prepared ready;
    private static ResourceLocation[] textures;

    private GreenShipHullRender() {}

    /** Returns false until the verified model is ready so the gameplay renderer can draw its placeholder. */
    public static boolean draw(boolean secondPose, boolean cockpitOpen, Vec3 cameraInShipBlocks, PoseStack pose,
                               MultiBufferSource buffers, int light) {
        return draw(secondPose, cockpitOpen, cameraInShipBlocks, pose, buffers, light, true);
    }

    static boolean drawItem(PoseStack pose, MultiBufferSource buffers, int light) {
        return draw(false, false, null, pose, buffers, light, false);
    }

    private static boolean draw(boolean secondPose, boolean cockpitOpen, Vec3 cameraInShipBlocks, PoseStack pose,
                                MultiBufferSource buffers, int light, boolean reflection) {
        var media = RacMcClient.media();
        retireStale(media);
        if (!media.isReady() || media.isChecking()) {
            return false;
        }
        long generation = media.generation();
        acceptFinished(media);
        if (ready != null && ready.generation() == media.generation() && media.isReady()) {
            var mesh = cockpitOpen && ready.openParked() != null
                    ? (secondPose ? ready.openClosed() : ready.openParked())
                    : (secondPose ? ready.poses().closed() : ready.poses().parked());
            Rac1MeshRenderer.render(pose, buffers, mesh, textures, light, false);
            if (reflection && ready.glass() != null) {
                GreenShipGlassPass.draw(ready.glass(), Rac1EffectClient.shipMaterial(ShipArt.GLASS),
                        ready.poses(), secondPose, cameraInShipBlocks, pose, buffers);
            }
            return true;
        }
        if (pending == null && generation != requestedGeneration) {
            media.configuredPath().ifPresent(path -> request(media, path, generation));
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        retireStale(RacMcClient.media());
    }

    private static void request(Rac1MediaManager media, Path path, long generation) {
        var selected = media.attemptResult().map(result -> result.revision()).orElse(null);
        var profile = Rac1DiscCatalog.supported().profiles().stream()
                .filter(candidate -> candidate.revision() == selected).findFirst();
        if (profile.isEmpty()) {
            return;
        }
        String key = Rac1DiscIdentity.key(selected, profile.get());
        requestedGeneration = generation;
        pending = WORKER.submit(() -> {
            try (var lease = media.lease(path, key)) {
                if (lease.generation() != generation) {
                    throw new IOException("Green ship media selection changed.");
                }
                var hull = GreenShipHullLoader.load(lease);
                var prepared = GreenShipHullPose.prepare(hull);
                var glass = prepareGlass(lease, hull.glassJoint());
                lease.checkValid();
                if (glass != null) {
                    LOGGER.info("Class 531 cockpit fit: {}", GreenShipFit.measure(glass, prepared));
                }
                Rac1Mesh openParked = null, openClosed = null;
                if (ShipCockpitRefit.activeProfile() == ShipCockpitRefit.CANDIDATE) {
                    if (lease.revision() == Rac1DiscRevision.NTSC_U_V1_00
                            && (GreenShipCockpitGlassFit.check(glass, prepared.parkedGlass())
                                        != GreenShipCockpitGlassFit.Fit.CLEAR
                                || GreenShipCockpitGlassFit.check(glass, prepared.closedGlass())
                                        != GreenShipCockpitGlassFit.Fit.CLEAR)) {
                        throw new AssetFormatException("NTSC windshield did not pass the refitted cockpit check.");
                    }
                    openParked = GreenShipCockpitOpening.subtract(prepared.parked(), ShipCockpitRefit.OPENING);
                    openClosed = GreenShipCockpitOpening.subtract(prepared.closed(), ShipCockpitRefit.OPENING);
                }
                return new Prepared(generation, prepared, hull.texture(), glass, openParked, openClosed);
            }
        });
    }

    private static GreenShipGlassPass.Table prepareGlass(Rac1MediaLease lease, int terminalJoint) {
        if (terminalJoint != 18) {
            return null;
        }
        try {
            // PAL returns empty before any canopy reads; keep its otherwise verified normal hull.
            return GreenShipCanopy.load(lease).map(GreenShipGlassPass::prepare).orElse(null);
        } catch (IOException | AssetFormatException | IllegalArgumentException failure) {
            LOGGER.warn("Optional class 531 canopy unavailable; keeping the normal hull.", failure);
            return null;
        }
    }

    private static void acceptFinished(Rac1MediaManager media) {
        if (pending == null || !pending.isDone()) {
            return;
        }
        try {
            var result = pending.get();
            if (result.generation() == media.generation() && media.isReady() && !media.isChecking()) {
                var texture = Rac1ClientTextures.register("green_ship_hull", result.texture());
                textures = new ResourceLocation[] {texture};
                ready = result;
                retireStale(media);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException failure) {
            if (requestedGeneration == media.generation()) {
                LOGGER.warn("Green ship hull unavailable; keeping the placeholder.", failure);
            }
        } finally {
            pending = null;
        }
    }

    private static void retireStale(Rac1MediaManager media) {
        if (ready != null && (!media.isReady() || ready.generation() != media.generation())) {
            Rac1ClientTextures.release(textures[0]);
            textures = null;
            ready = null;
        }
    }
}
