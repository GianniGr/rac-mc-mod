package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.clank.*;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.render.Rac1ClientTextures;
import com.github.giannigr.racmc.client.render.Rac1ItemTextureLease;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;

/** Client-thread resources; background work only decodes local data, never calls the GPU. */
public final class Rac1ClankResources {
    private static final Map<Rac1ClankAssets.Role, Rac1AnimatedAsset> MODELS =
            new EnumMap<>(Rac1ClankAssets.Role.class);
    private static final Map<Rac1ClankAssets.Role, Rac1ItemTextureLease<ResourceLocation>> LEASES =
            new EnumMap<>(Rac1ClankAssets.Role.class);
    private static CompletableFuture<Rac1ClankAssets> pending;
    private static String requested;
    private static long generation;
    private Rac1ClankResources() {}

    /** Lazy: no acquisition means no extra startup scan/import and no second media manager. */
    public static void update(boolean needed) {
        if (!RacMcClient.allowsWorldEntry()) { clear(); return; }
        var content = RacMcClient.assets().content();
        if (content.isEmpty()) return;
        String key = content.get().discKey();
        if (requested != null && !requested.equals(key)) clear();
        if (needed && requested == null && RacMcClient.assetCacheDirectory().isPresent()
                && RacMcClient.media().configuredPath().isPresent()) {
            requested = key;
            var directory = RacMcClient.assetCacheDirectory().orElseThrow();
            var image = RacMcClient.media().configuredPath().orElseThrow();
            pending = CompletableFuture.supplyAsync(() -> {
                var store = new Rac1ClankSidecarStore(directory);
                try {
                    return store.read(key);
                } catch (IOException cacheMiss) {
                    try {
                        var assets = Rac1ClankImporter.read(image);
                        if (!assets.discKey().equals(key)) throw new IOException("Changed Clank source disc");
                        store.write(assets);
                        return assets;
                    } catch (Exception failure) {
                        throw new java.util.concurrent.CompletionException(failure);
                    }
                }
            });
        }
        if (pending != null && pending.isDone()) {
            var completed = pending;
            pending = null;
            try {
                var assets = completed.join();
                for (var role : Rac1ClankAssets.Role.values()) MODELS.put(role, assets.copyModel(role));
            } catch (RuntimeException | IOException failure) {
                MODELS.clear();
                RacMcMod.LOGGER.warn("Wearable Clank local resources unavailable: {}", failure.toString());
                // Retry only after lifecycle reset, not every frame/tick.
            }
        }
    }
    static Rac1AnimatedAsset model(Rac1ClankAssets.Role role) { return MODELS.get(role); }
    static ResourceLocation[] textures(Rac1ClankAssets.Role role) {
        var asset = MODELS.get(role);
        if (asset == null) return new ResourceLocation[0];
        var lease = LEASES.computeIfAbsent(role, r -> new Rac1ItemTextureLease<>(
                (index, texture) -> Rac1ClientTextures.register("clank_" + r.name().toLowerCase(
                        java.util.Locale.ROOT) + "_" + index, texture), Rac1ClientTextures::release));
        return lease.get(asset, generation, asset.textures()).toArray(ResourceLocation[]::new);
    }
    public static void reload() {
        Rac1ClankItemRenderer.clear();
        LEASES.values().forEach(Rac1ItemTextureLease::clear);
        generation++;
    }
    public static void clear() {
        reload();
        MODELS.clear();
        requested = null;
        // A detached background result can finish writing only its validated local sidecar.
        // It cannot publish models/textures after this generation was invalidated.
        pending = null;
    }
}
