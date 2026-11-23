package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.sound.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

final class Rac1PcmSound extends AbstractSoundInstance implements TickableSoundInstance {
    private final Rac1PcmStream stream;
    private final int range;
    private final boolean allowOverride;
    private boolean resolved;
    private boolean resourceOverride;
    private boolean stopped;
    Rac1PcmSound(Rac1AudioSignal signal, Rac1AudioCatalog.Clip clip) {
        this(signal, clip, false);
    }
    Rac1PcmSound(Rac1AudioSignal signal, Rac1AudioCatalog.Clip clip, boolean sustained) {
        this(Rac1Sounds.location(signal.event()), signal.seed(), signal.x(), signal.y(), signal.z(),
                clip, sustained, 32);
    }
    Rac1PcmSound(ResourceLocation id, long seed, double x, double y, double z,
            Rac1AudioCatalog.Clip clip, boolean sustained, int range) {
        this(id, seed, x, y, z, clip, sustained, range, SoundSource.BLOCKS, true);
    }

    Rac1PcmSound(ResourceLocation id, long seed, double x, double y, double z, Rac1AudioCatalog.Clip clip,
            boolean sustained, int range, SoundSource category, boolean allowOverride) {
        super(id, category, RandomSource.create(seed));
        this.range = range;
        this.allowOverride = allowOverride;
        position(x, y, z);
        volume = clip.gain();
        // Original bend conversion is unverified; preserve it, don't invent a random multiplier.
        pitch = 1;
        looping = sustained;
        stream = sustained ? new Rac1PcmStream(clip.pcm(), clip.rate(), clip.loopStart(), clip.loopEnd())
                : new Rac1PcmStream(clip.pcm(), clip.rate());
    }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        resolved = true;
        // Resource packs can replace semantic events with ordinary OGG-backed definitions.
        if (allowOverride && manager.getSoundEvent(location) != null) {
            resourceOverride = true;
            return super.resolve(manager);
        }
        sound = new Sound(location, ConstantFloat.of(1), ConstantFloat.of(1), 1,
                Sound.Type.FILE, true, false, range);
        return new WeighedSoundEvents(location, null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound selected, boolean loop) {
        return resourceOverride ? super.getStream(buffers, selected, loop)
                : CompletableFuture.completedFuture(stream);
    }
    @Override public void tick() {}
    @Override public boolean isStopped() { return stopped; }
    boolean reachedResolution() { return resolved; }
    void close() { stopped = true; stream.close(); }
    void position(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
}
