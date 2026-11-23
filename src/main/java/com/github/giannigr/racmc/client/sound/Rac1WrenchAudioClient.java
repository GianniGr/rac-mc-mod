package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.actions.WrenchActionClient;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.iso.Rac1MediaState;
import com.github.giannigr.racmc.sound.Rac1WrenchAudioCatalog;
import com.github.giannigr.racmc.sound.WrenchAudioGate;
import com.github.giannigr.racmc.sound.WrenchAudioSignal;
import com.github.giannigr.racmc.sound.WrenchMeleeSignal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Separate wrench playback and dedup; shares only the existing verified handle/backend/budget. */
final class Rac1WrenchAudioClient {
    private record Flight(long action, Rac1PcmSound sound) {}

    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private static final ArrayList<Rac1PcmSound> IMPACTS = new ArrayList<>();
    private static final WrenchAudioGate EDGES = new WrenchAudioGate();
    private static Rac1WrenchAudioCatalog catalog;
    private static long mediaGeneration;
    private static ClientLevel world;

    private Rac1WrenchAudioClient() {}

    static long invalidateCatalog() {
        catalog = null;
        return ++mediaGeneration;
    }

    // Invoked on the existing sound import worker, sharing the already fully validated handle.
    static void prepare(Rac1DiscImage verified, long generation) {
        try {
            var clips = Rac1WrenchAudioCatalog.load(verified);
            if (clips.defaultHit() == null || clips.alternateHit() == null)
                RacMcMod.LOGGER.warn("One or more original wrench hit cues unavailable; preserving native feedback.");
            Minecraft.getInstance().execute(() -> {
                if (generation == mediaGeneration) catalog = clips;
            });
        } catch (Exception failure) {
            RacMcMod.LOGGER.warn("Original wrench audio unavailable: {}", failure.toString());
        }
    }

    static int soundCount() { return FLIGHTS.size() + IMPACTS.size(); }
    static int loopCount() { return FLIGHTS.size(); }

    static void stopAll() {
        var mc = Minecraft.getInstance();
        for (var hit : IMPACTS) stop(mc, hit);
        for (var loop : FLIGHTS.values()) stop(mc, loop.sound());
        IMPACTS.clear();
        FLIGHTS.clear();
        if (world != mc.level) {
            EDGES.worldChanged();
            world = mc.level;
        }
        if (world != null) EDGES.fence(world.getGameTime());
    }

    static void tick(Minecraft mc) {
        WrenchAudioSignal.receiver = Rac1WrenchAudioClient::receive;
        WrenchMeleeSignal.clientReceiver = Rac1WrenchAudioClient::receiveMelee;
        if (world != mc.level) stopAll();
        if (world == null || mc.player == null) return;
        IMPACTS.removeIf(hit -> {
            if (mc.getSoundManager().isActive(hit)) return false;
            hit.close();
            return true;
        });
        FLIGHTS.entrySet().removeIf(entry -> {
            var owner = world.getPlayerByUUID(entry.getKey());
            var flight = entry.getValue();
            if (owner == null || !WrenchActionClient.cometInFlight(owner, flight.action(), world.getGameTime())
                    || owner.distanceToSqr(mc.player) > 25 * 25) {
                stop(mc, flight.sound());
                return true;
            }
            flight.sound().position(owner.getX(), owner.getY(), owner.getZ());
            return false;
        });
    }

    private static boolean receiveMelee(WrenchMeleeSignal offer) {
        var mc = Minecraft.getInstance();
        if (world == null || mc.level != world || mc.player == null || catalog == null
                || RacMcClient.media().state() != Rac1MediaState.VALID
                || !Rac1ClientAudio.hasRoom(false)) return false;
        var clip = catalog.melee(offer.variant());
        if (clip == null) return false;
        var at = offer.impact();
        var fallback = offer.fallback();
        var sound = new Rac1PcmSound(offer.variant().location(), fallback.getSeed(), at.x, at.y,
                at.z, clip, false, 32, fallback.getSource(), false);
        var manager = mc.getSoundManager();
        manager.play(sound);
        // NeoForge may cancel or replace this exact instance before resolve. Do not send
        // a second sound in that case. Otherwise channel admission is synchronous in 1.21.1.
        boolean active = manager.isActive(sound);
        if (active) IMPACTS.add(sound);
        else sound.close();
        return handledMeleeAttempt(active, sound.reachedResolution());
    }

    static boolean handledMeleeAttempt(boolean active, boolean reachedResolution) {
        return active || !reachedResolution;
    }

    private static void receive(WrenchAudioSignal signal) {
        var mc = Minecraft.getInstance();
        if (world == null || world != mc.level || mc.player == null
                || !world.dimension().location().equals(signal.dimension()) || !signal.canHear(mc.player.position())
                || !EDGES.claim(signal.emitter(), signal.action(), signal.tick(), world.getGameTime())
                || catalog == null || RacMcClient.media().state() != Rac1MediaState.VALID) return;
        boolean loop = signal.kind() == WrenchAudioSignal.Kind.COMET_RELEASE;
        if (loop) {
            var owner = world.getPlayerByUUID(signal.emitter());
            if (owner == null || !WrenchActionClient.cometInFlight(owner, signal.action(), world.getGameTime())) return;
            var previous = FLIGHTS.remove(signal.emitter());
            if (previous != null) stop(mc, previous.sound());
        }
        if (!Rac1ClientAudio.hasRoom(loop)) return;
        var clip = loop ? catalog.comet() : catalog.slam();
        var sound = new Rac1PcmSound(signal.kind().location(),
                signal.emitter().getLeastSignificantBits() ^ signal.action(),
                signal.x(), signal.y(), signal.z(), clip, loop, signal.kind().range());
        if (loop) FLIGHTS.put(signal.emitter(), new Flight(signal.action(), sound));
        else IMPACTS.add(sound);
        mc.getSoundManager().play(sound);
    }

    private static void stop(Minecraft mc, Rac1PcmSound sound) {
        mc.getSoundManager().stop(sound);
        sound.close();
    }
}
