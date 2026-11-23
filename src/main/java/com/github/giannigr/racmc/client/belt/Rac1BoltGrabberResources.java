package com.github.giannigr.racmc.client.belt;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import com.github.giannigr.racmc.asset.belt.Rac1BoltGrabberAsset;
import com.github.giannigr.racmc.asset.belt.Rac1BoltGrabberPickupAsset;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
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
import org.joml.Matrix4f;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1BoltGrabberResources {
    record Art(Prepared prepared, ResourceLocation[] textures) {}
    record Prepared(Rac1AnimatedAsset model, Rac1ImportedItemPose.Sample neutral, Rac1BeltWaist.Fit mount) {}
    record PickupArt(PreparedPickup prepared, ResourceLocation[] textures) {}
    record PreparedPickup(Rac1StaticAsset model, Matrix4f fit) {}
    private record Request(Path path, Rac1CacheContent owner, long generation) {}

    private static final Rac1BeltArtSlot<Request, Prepared, Art> SLOT = new Rac1BeltArtSlot<>();
    private static final Rac1ItemTextureLease<ResourceLocation> TEXTURES = new Rac1ItemTextureLease<>(
            (i, texture) -> Rac1ClientTextures.register("belt_bolt_grabber_" + i, texture), Rac1ClientTextures::release);
    private static final Rac1BeltArtSlot<Request, PreparedPickup, PickupArt> PICKUP = new Rac1BeltArtSlot<>();
    private static final Rac1ItemTextureLease<ResourceLocation> PICKUP_TEXTURES = new Rac1ItemTextureLease<>(
            (i, texture) -> Rac1ClientTextures.register("belt_bolt_pickup_" + i, texture), Rac1ClientTextures::release);
    private static final java.util.concurrent.Executor WORKER = Executors.newSingleThreadExecutor(job -> {
        var thread = new Thread(job, "racmc-belt-art");
        thread.setDaemon(true);
        return thread;
    });

    private Rac1BoltGrabberResources() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var current = current();
        SLOT.tick(current, Rac1BoltGrabberResources::load, Rac1BoltGrabberResources::install,
                ignored -> TEXTURES.clear(), failure ->
                        RacMcMod.LOGGER.warn("Verified local worn Bolt Grabber art is unavailable", failure));
        PICKUP.tick(current, Rac1BoltGrabberResources::loadPickup, Rac1BoltGrabberResources::installPickup,
                ignored -> PICKUP_TEXTURES.clear(), failure ->
                        RacMcMod.LOGGER.warn("Verified local Bolt Grabber pickup art is unavailable", failure));
    }

    /** Warm lookup only; generation invalidation hides stale art before cleanup on the next tick. */
    static Art get() {
        return SLOT.get(current());
    }

    static PickupArt getPickup() {
        return PICKUP.get(current());
    }

    private static Request current() {
        var media = RacMcClient.media();
        if (!RacMcClient.allowsWorldEntry() || media.isChecking()) return null;
        var owner = RacMcClient.assets().content().orElse(null);
        var path = media.configuredPath().orElse(null);
        return owner == null || path == null ? null : new Request(path, owner, media.generation());
    }

    private static CompletableFuture<Prepared> load(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try (var lease = RacMcClient.media().lease(request.path(), request.owner().discKey())) {
                if (lease.generation() != request.generation()) throw new IOException("Stale Belt art request");
                var model = Rac1BoltGrabberAsset.read(lease);
                var sample = Rac1ImportedItemPose.sample(model, 0, 0);
                var mount = Rac1BeltWaist.fit(sample.positions());
                lease.checkValid();
                return new Prepared(model, sample, mount);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, WORKER);
    }

    private static Art install(Request request, Prepared prepared) {
        return new Art(prepared, TEXTURES.get(prepared, request.generation(), prepared.model().textures())
                .toArray(ResourceLocation[]::new));
    }

    private static CompletableFuture<PreparedPickup> loadPickup(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try (var lease = RacMcClient.media().lease(request.path(), request.owner().discKey())) {
                if (lease.generation() != request.generation()) throw new IOException("Stale pickup art request");
                var model = Rac1BoltGrabberPickupAsset.read(lease);
                var fit = Rac1BoltGrabberPickupFit.transform(model.mesh().positions());
                lease.checkValid();
                return new PreparedPickup(model, fit);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, WORKER);
    }

    private static PickupArt installPickup(Request request, PreparedPickup prepared) {
        return new PickupArt(prepared, PICKUP_TEXTURES.get(prepared, request.generation(), prepared.model().textures())
                .toArray(ResourceLocation[]::new));
    }
}
