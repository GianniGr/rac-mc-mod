package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.client.sound.Rac1PcmStream;
import com.github.giannigr.racmc.sound.Rac1AudioCatalog;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/** Private one-shot PCM transport; exact imported cue/tone rate and gain, no synthetic sound event. */
final class Rac1PdaOriginalSound extends AbstractSoundInstance implements TickableSoundInstance {
    private final Rac1PcmStream stream;
    private boolean stopped;
    Rac1PdaOriginalSound(Rac1AudioCatalog.Clip clip) {
        super(ResourceLocation.fromNamespaceAndPath("racmc","pda_remote_pcm"),SoundSource.PLAYERS,RandomSource.create(0));
        if (clip.cueId()<68 || clip.cueId()>75 || clip.repeat() || clip.loopStart()!=-1)
            throw new IllegalArgumentException("Verified original PDA remote one-shot required");
        stream=new Rac1PcmStream(clip.pcm(),clip.rate());
        volume=clip.gain(); pitch=1; looping=false; relative=true; attenuation=Attenuation.NONE;
    }
    @Override public void tick() {}
    @Override public boolean isStopped() { return stopped; }
    @Override public boolean canPlaySound() { return !stopped; }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        sound=new Sound(location,ConstantFloat.of(1),ConstantFloat.of(1),1,Sound.Type.FILE,true,false,32);
        return new WeighedSoundEvents(location,null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,Sound selected,boolean loop) {
        return CompletableFuture.completedFuture(stream);
    }
    void close() { if (!stopped) { stopped=true; stream.close(); } }
}
