package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.sound.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Optional audio capability, not a new media gate. Missing/corrupt assets are silent and logged once. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1ClientAudio {
    private static final ArrayList<Rac1PcmSound> ACTIVE = new ArrayList<>();
    private static final Rac1AudioGeneration IMPORT = new Rac1AudioGeneration();
    private static Path path;
    private static Path cacheRoot;
    private static Object world;
    private static long lastBoltTick = Long.MIN_VALUE;
    private Rac1ClientAudio() {}

    public static void start(Path iso, Path cacheDirectory) {
        if (iso.equals(path) && cacheDirectory.equals(cacheRoot)) return;
        stopAll();
        path = iso; cacheRoot = cacheDirectory;
        long token = IMPORT.invalidate();
        CompletableFuture.supplyAsync(() -> {
            try (var inspected = new Rac1DiscValidator().inspect(iso)) {
                if (!inspected.isSupported()) throw new java.io.IOException("Sound source no longer verified");
                var profile = new Rac1DiscValidator().catalog().profiles().stream()
                        .filter(p -> p.revision() == inspected.result().revision()).findFirst().orElseThrow();
                var store = new Rac1SoundCache(cacheDirectory.resolve("sound"));
                String hash = profile.imageSha1();
                try {
                    var cached = store.read(hash);
                    if (cached.isPresent()) return cached.get();
                } catch (java.io.IOException corrupt) {
                    RacMcMod.LOGGER.warn("Sound sidecar invalid; rebuilding from verified local media.");
                }
                var built = Rac1AudioCatalog.extract(inspected.image());
                store.write(hash, built);
                return built;
            } catch (Exception failure) {
                RacMcMod.LOGGER.warn("Original audio unavailable; gameplay continues silently: {}", failure.toString());
                return null;
            }
        }).thenAccept(built -> Minecraft.getInstance().execute(() -> {
            IMPORT.publish(token, built);
        }));
    }
    public static void stopAll() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (var sound : ACTIVE) { manager.stop(sound); sound.close(); }
        ACTIVE.clear();
        lastBoltTick = Long.MIN_VALUE;
    }
    private static void receive(Rac1AudioSignal signal) {
        var mc = Minecraft.getInstance();
        var catalog = IMPORT.current();
        if (catalog == null || mc.level == null || mc.player == null
                || !mc.level.dimension().location().equals(signal.dimension())
                || mc.player.distanceToSqr(signal.x(), signal.y(), signal.z()) > 32 * 32) return;
        long tick = mc.level.getGameTime();
        // Audio-only coalescing; never affects currency or its collection.
        if (signal.event().bolt()) {
            if (lastBoltTick == tick) return;
            lastBoltTick = tick;
        }
        // Short one-shots share scarce vanilla streaming channels; drop audio under pressure.
        if (ACTIVE.size() >= 4) return;
        var clip = catalog.clips().get(signal.event());
        if (clip == null) return;
        var sound = new Rac1PcmSound(signal, clip);
        ACTIVE.add(sound);
        mc.getSoundManager().play(sound);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Rac1AudioSignal.receiver = Rac1ClientAudio::receive;
        var mc = Minecraft.getInstance();
        if (world != mc.level) { stopAll(); world = mc.level; }
        ACTIVE.removeIf(sound -> {
            if (mc.getSoundManager().isActive(sound)) return false;
            sound.close(); return true;
        });
        if (RacMcClient.media().state() != Rac1MediaState.VALID && path != null) {
            IMPORT.invalidate(); stopAll(); path = null;
        }
    }
    @EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    @SuppressWarnings("removal")
    public static final class Reload {
        private Reload() {}
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    resources -> Minecraft.getInstance().execute(Rac1ClientAudio::stopAll));
        }
    }
}
