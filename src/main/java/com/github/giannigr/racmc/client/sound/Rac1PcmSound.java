package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.sound.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

final class Rac1PcmSound extends AbstractSoundInstance implements TickableSoundInstance {
    private final Rac1PcmStream stream;
    private boolean resourceOverride;
    private boolean stopped;
    Rac1PcmSound(Rac1AudioSignal signal, Rac1AudioCatalog.Clip clip) {
        this(signal, clip, false);
    }
    Rac1PcmSound(Rac1AudioSignal signal, Rac1AudioCatalog.Clip clip, boolean sustained) {
        super(Rac1Sounds.location(signal.event()), SoundSource.BLOCKS, RandomSource.create(signal.seed()));
        x = signal.x(); y = signal.y(); z = signal.z();
        volume = clip.gain();
        // Original bend conversion is unverified; preserve it, don't invent a random multiplier.
        pitch = 1;
        looping = sustained;
        stream = sustained ? new Rac1PcmStream(clip.pcm(), clip.rate(), clip.loopStart(), clip.loopEnd())
                : new Rac1PcmStream(clip.pcm(), clip.rate());
    }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        // Resource packs can replace semantic events with ordinary OGG-backed definitions.
        if (manager.getSoundEvent(location) != null) {
            resourceOverride = true;
            return super.resolve(manager);
        }
        sound = new Sound(location, ConstantFloat.of(1), ConstantFloat.of(1), 1,
                Sound.Type.FILE, true, false, 32);
        return new WeighedSoundEvents(location, null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound selected, boolean loop) {
        return resourceOverride ? super.getStream(buffers, selected, loop)
                : CompletableFuture.completedFuture(stream);
    }
    @Override public void tick() {}
    @Override public boolean isStopped() { return stopped; }
    void close() { stopped = true; stream.close(); }
    void position(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
}
