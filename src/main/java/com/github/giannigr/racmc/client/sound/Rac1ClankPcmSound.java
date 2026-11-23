package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.Rac1ClankAv.Loop;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import com.github.giannigr.racmc.sound.Rac1AudioCatalog;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/** One owned actor voice. Uses the existing loop-aware PCM stream; never decodes on the audio thread. */
final class Rac1ClankPcmSound extends AbstractSoundInstance implements TickableSoundInstance {
    private final Rac1PcmStream stream;
    private boolean stopped;
    Rac1ClankPcmSound(Loop cue,long seed,Point at,Rac1AudioCatalog.Clip clip) {
        super(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID,
                cue==Loop.HELI?"clank_heli_glide":"clank_thruster_glide"),SoundSource.PLAYERS,RandomSource.create(seed));
        if(cue!=Loop.HELI&&cue!=Loop.THRUSTER||!clip.repeat()||clip.loopStart()<0)
            throw new IllegalArgumentException("Unsupported Clank loop");
        position(at);volume=clip.gain();pitch=1;looping=true;
        stream=new Rac1PcmStream(clip.pcm(),clip.rate(),clip.loopStart(),clip.loopEnd());
    }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        sound=new Sound(location,ConstantFloat.of(1),ConstantFloat.of(1),1,Sound.Type.FILE,true,false,32);
        return new WeighedSoundEvents(location,null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,Sound selected,boolean loop) {
        return CompletableFuture.completedFuture(stream);
    }
    @Override public void tick() {}
    @Override public boolean isStopped(){return stopped;}
    void position(Point at){x=at.x();y=at.y();z=at.z();}
    void close(){stopped=true;stream.close();}
}
