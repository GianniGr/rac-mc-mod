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
    private static long lastAmmoPickupTick = Long.MIN_VALUE;
    private record Loop(java.util.UUID session, Rac1PcmSound sound) {}
    private static final java.util.Map<java.util.UUID, Loop> LOOPS = new java.util.HashMap<>();
    private static final FireLoopState LOOP_STATE = new FireLoopState();
    private static long minimumFireTick = Long.MIN_VALUE;
    private Rac1ClientAudio() {}

    public static void start(Path iso, Path cacheDirectory) {
        if (iso.equals(path) && cacheDirectory.equals(cacheRoot)) return;
        stopAll();
        path = iso; cacheRoot = cacheDirectory;
        long token = IMPORT.invalidate();
        boolean hasBombGlove = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "bomb_glove")).isPresent();
        long wrenchToken = Rac1WrenchAudioClient.invalidateCatalog();
        CompletableFuture.supplyAsync(() -> {
            try (var inspected = new Rac1DiscValidator().inspect(iso)) {
                if (!inspected.isSupported()) throw new java.io.IOException("Sound source no longer verified");
                Rac1WrenchAudioClient.prepare(inspected.image(), wrenchToken);
                var profile = new Rac1DiscValidator().catalog().profiles().stream()
                        .filter(p -> p.revision() == inspected.result().revision()).findFirst().orElseThrow();
                var store = new Rac1SoundCache(cacheDirectory.resolve("sound"));
                String hash = profile.imageSha1();
                var shared = sharedCatalog(store, hash, inspected.image());
                if (!hasBombGlove) return shared;
                try {
                    return shared.withDryBomb(inspected.image());
                } catch (Exception unavailable) {
                    RacMcMod.LOGGER.warn("Original Bomb Glove dry cue unavailable: {}", unavailable.toString());
                    return shared;
                }
            } catch (Exception failure) {
                RacMcMod.LOGGER.warn("Original audio unavailable; gameplay continues silently: {}", failure.toString());
                return null;
            }
        }).thenAccept(built -> Minecraft.getInstance().execute(() -> {
            IMPORT.publish(token, built);
        }));
    }

    private static Rac1AudioCatalog sharedCatalog(Rac1SoundCache store, String hash, Rac1DiscImage image)
            throws Exception {
        try {
            var cached = store.read(hash);
            if (cached.isPresent()) return cached.get();
        } catch (java.io.IOException corrupt) {
            RacMcMod.LOGGER.warn("Sound sidecar invalid; rebuilding from verified local media.");
        }
        var built = Rac1AudioCatalog.extract(image);
        store.write(hash, built);
        return built;
    }
    public static void stopAll() {
        Rac1WrenchAudioClient.stopAll();
        var manager = Minecraft.getInstance().getSoundManager();
        for (var sound : ACTIVE) { manager.stop(sound); sound.close(); }
        ACTIVE.clear();
        for (var loop : LOOPS.values()) { manager.stop(loop.sound()); loop.sound().close(); }
        LOOPS.clear();
        var level = Minecraft.getInstance().level;
        minimumFireTick = level == null ? Long.MIN_VALUE : level.getGameTime();
        lastBoltTick = Long.MIN_VALUE;
        lastAmmoPickupTick = Long.MIN_VALUE;
    }
    static boolean hasRoom(boolean sustained) {
        return ACTIVE.size() + LOOPS.size() + Rac1WrenchAudioClient.soundCount() < 4
                && (!sustained || LOOPS.size() + Rac1WrenchAudioClient.loopCount() < 2);
    }
    private static void receive(Rac1AudioSignal signal) {
        if (signal.event() == Rac1AudioEvent.BLASTER_FIRE_LOOP) return; // never one intro per shot
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
        if (!hasRoom(false)) return;
        var clip = catalog.clip(signal.event());
        if (clip == null) return;
        if (signal.event().ammoPickup()) {
            // One personal cue for simultaneous gains, never a delayed or replayed request.
            if (lastAmmoPickupTick == tick) return;
            lastAmmoPickupTick = tick;
        }
        var sound = new Rac1PcmSound(signal, clip);
        ACTIVE.add(sound);
        mc.getSoundManager().play(sound);
    }
    private static void receiveFire(RangedFireSignal signal) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(signal.dimension())
                || signal.active() && signal.tick() <= minimumFireTick
                || !LOOP_STATE.accept(signal.emitter(), signal.session(), signal.sequence(), signal.tick(),
                        signal.active(), mc.level.getGameTime())) return;
        Loop old = LOOPS.get(signal.emitter());
        if (old != null && (!signal.active() || !old.session().equals(signal.session()))) {
            mc.getSoundManager().stop(old.sound()); old.sound().close(); LOOPS.remove(signal.emitter());
            old = null;
        }
        if (!signal.active()) return;
        if (old != null) {
            old.sound().position(signal.x(), signal.y(), signal.z());
            return; // accepted held refresh, no new PCM stream or intro
        }
        var catalog = IMPORT.current();
        if (catalog == null || !hasRoom(true)
                || mc.player.distanceToSqr(signal.x(), signal.y(), signal.z()) > 32 * 32) return;
        var clip = catalog.clips().get(Rac1AudioEvent.BLASTER_FIRE_LOOP);
        if (clip == null || !clip.repeat() || clip.loopStart() < 0) return;
        var sound = new Rac1PcmSound(new Rac1AudioSignal(Rac1AudioEvent.BLASTER_FIRE_LOOP,
                signal.x(), signal.y(), signal.z(), signal.session().getLeastSignificantBits(), signal.dimension()), clip, true);
        LOOPS.put(signal.emitter(), new Loop(signal.session(), sound));
        mc.getSoundManager().play(sound);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Rac1AudioSignal.receiver = Rac1ClientAudio::receive;
        RangedFireSignal.receiver = Rac1ClientAudio::receiveFire;
        var mc = Minecraft.getInstance();
        if (world != mc.level) { stopAll(); LOOP_STATE.clear(); world = mc.level; }
        Rac1WrenchAudioClient.tick(mc);
        ACTIVE.removeIf(sound -> {
            if (mc.getSoundManager().isActive(sound)) return false;
            sound.close(); return true;
        });
        LOOPS.entrySet().removeIf(entry -> {
            var loop = entry.getValue();
            var emitter = mc.level == null ? null : mc.level.getPlayerByUUID(entry.getKey());
            boolean stop = mc.level == null || emitter == null || !emitter.isAlive()
                    || !emitter.getMainHandItem().is(com.github.giannigr.racmc.item.RacMcItems.BLASTER.get())
                    || !LOOP_STATE.active(entry.getKey(), loop.session(), mc.level.getGameTime())
                    || emitter.distanceToSqr(mc.player) > 32 * 32;
            if (stop) { mc.getSoundManager().stop(loop.sound()); loop.sound().close(); }
            return stop;
        });
        if (RacMcClient.media().state() != Rac1MediaState.VALID && path != null) {
            IMPORT.invalidate(); Rac1WrenchAudioClient.invalidateCatalog(); stopAll(); path = null;
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
