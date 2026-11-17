package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.sound.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

final class Rac1PcmSound extends AbstractSoundInstance {
    private final Rac1PcmStream stream;
    private boolean resourceOverride;
    Rac1PcmSound(Rac1AudioSignal signal, Rac1AudioCatalog.Clip clip) {
        super(Rac1Sounds.location(signal.event()), SoundSource.BLOCKS, RandomSource.create(signal.seed()));
        x = signal.x(); y = signal.y(); z = signal.z();
        volume = clip.gain();
        // Original bend conversion is unverified; preserve it, don't invent a random multiplier.
        pitch = 1;
        looping = false;
        stream = new Rac1PcmStream(clip.pcm(), clip.rate());
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
    void close() { stream.close(); }
}
