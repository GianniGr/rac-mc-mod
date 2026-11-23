package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import com.github.giannigr.racmc.sound.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/** Independent one-shot, never a substituted glide loop or shared Blaster event. */
final class Rac1ClankEntrySound extends AbstractSoundInstance implements TickableSoundInstance {
    private final Rac1PcmStream stream;
    private boolean stopped;
    Rac1ClankEntrySound(Rac1ClankHighEntryAudio.Cue cue,long seed,Point at,Rac1AudioCatalog.Clip clip) {
        super(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID,"clank_"+cue.name().toLowerCase(java.util.Locale.ROOT)+"_high_entry"),
                SoundSource.PLAYERS,RandomSource.create(seed));
        if(clip.repeat())throw new IllegalArgumentException("Entry must be a one-shot");
        position(at);volume=clip.gain();pitch=Rac1ClankHighEntryAudio.pitch(clip,new java.util.Random(seed));
        stream=new Rac1PcmStream(clip.pcm(),clip.rate(),-1,clip.loopEnd());
    }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        sound=new Sound(location,ConstantFloat.of(1),ConstantFloat.of(1),1,Sound.Type.FILE,true,false,32);
        return new WeighedSoundEvents(location,null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,Sound sound,boolean loop) {
        return CompletableFuture.completedFuture(stream);
    }
    @Override public void tick() {}
    @Override public boolean isStopped(){return stopped;}
    void position(Point at){x=at.x();y=at.y();z=at.z();}
    void close(){stopped=true;stream.close();}
}
