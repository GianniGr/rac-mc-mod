package com.github.giannigr.racmc.client.rescue;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.rescue.Rac1PodAssets;
import com.github.giannigr.racmc.asset.rescue.Rac1PodPresentation;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.render.Rac1ClientTextures;
import com.github.giannigr.racmc.client.render.Rac1ItemTextureLease;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Selection-owned local pod art. The owner/viewer snapshot is never part of this cache. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1PodClientResources {
    record Prepared(Rac1PodAssets source, Rac1PodPresentation legacy, Rac1PodPresentation full) {
        static Prepared of(Rac1PodAssets source) throws AssetFormatException {
            return new Prepared(source, Rac1PodPresentation.of(source, false), Rac1PodPresentation.of(source, true));
        }
    }

    record Art(Prepared prepared, ResourceLocation[] hullTextures, ResourceLocation[] occupantTextures) {
        Rac1PodPresentation presentation(boolean full) {
            return full ? prepared.full() : prepared.legacy();
        }
    }

    private record Request(Path path, Rac1CacheContent owner, long generation) {}

    private static final Rac1PodLoadSlot<Request, Prepared, Art> SLOT = new Rac1PodLoadSlot<>();
    // SLOT does not submit another job until the previous future actually finishes.
    private static final java.util.concurrent.Executor WORKER = Executors.newSingleThreadExecutor(job -> {
        var thread = new Thread(job, "racmc-pod-preparation");
        thread.setDaemon(true);
        return thread;
    });
    private static final Rac1ItemTextureLease<ResourceLocation> HULL_TEXTURES =
            new Rac1ItemTextureLease<>((i, texture) -> Rac1ClientTextures.register("rescue_pod_hull_" + i, texture),
                    Rac1ClientTextures::release);
    private static final Rac1ItemTextureLease<ResourceLocation> OCCUPANT_TEXTURES =
            new Rac1ItemTextureLease<>((i, texture) -> Rac1ClientTextures.register("rescue_pod_clank_" + i, texture),
                    Rac1ClientTextures::release);

    private Rac1PodClientResources() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        // Menu ticks participate. This is optional preparation, never a new world-entry gate.
        var media = RacMcClient.media();
        Request request = null;
        long generation = media.generation();
        if (RacMcClient.allowsWorldEntry() && !media.isChecking()) {
            var owner = RacMcClient.assets().content().orElse(null);
            var path = media.configuredPath().orElse(null);
            if (owner != null && path != null) request = new Request(path, owner, generation);
        }
        SLOT.tick(request, Rac1PodClientResources::current, Rac1PodClientResources::submit,
                Rac1PodClientResources::upload, art -> releaseTextures(), failure ->
                        RacMcMod.LOGGER.warn("Local rescue pod art is unavailable; using the Minecraft hull outline.",
                                failure));
    }

    /** Pure warm lookup on the render thread. In particular, never registers a texture. */
    static Art get() {
        return SLOT.lookup(Rac1PodClientResources::current);
    }

    private static boolean current(Request request) {
        var media = RacMcClient.media();
        // The shared hook advances the generation on reload/logout; reselection does so before
        // validating. A new login can keep already-prepared art for the same selection because
        // this data contains no viewer state.
        return request.generation == media.generation() && !media.isChecking() && RacMcClient.allowsWorldEntry()
                && RacMcClient.assets().content().orElse(null) == request.owner;
    }

    private static CompletableFuture<Prepared> submit(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            try (var image = RacMcClient.media().lease(request.path, request.owner.discKey())) {
                if (image.generation() != request.generation) throw new IOException("Stale pod media request");
                image.checkValid();
                var source = Rac1PodAssets.read(image);
                var prepared = Prepared.of(source);
                image.checkValid();
                RacMcMod.LOGGER.debug("Rescue pod preparation: generation={}, CPU/import={} us",
                        request.generation, (System.nanoTime() - start) / 1_000);
                return prepared;
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, WORKER);
    }

    private static Art upload(Prepared prepared) {
        long start = System.nanoTime();
        try {
            var source = prepared.source;
            var hull = HULL_TEXTURES.get(prepared, 0, source.hull().model().textures());
            var occupant = OCCUPANT_TEXTURES.get(prepared, 0, source.occupant().model().textures());
            var art = new Art(prepared, hull.toArray(ResourceLocation[]::new),
                    occupant.toArray(ResourceLocation[]::new));
            RacMcMod.LOGGER.debug("Rescue pod texture upload: {} us", (System.nanoTime() - start) / 1_000);
            return art;
        } catch (RuntimeException | LinkageError failure) {
            releaseTextures();
            throw failure;
        }
    }

    private static void releaseTextures() {
        HULL_TEXTURES.clear();
        OCCUPANT_TEXTURES.clear();
    }
}
