package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.Rac1HydroTrace;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import com.github.giannigr.racmc.sound.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/** Hydro-local streaming channel; its two synthesized voices never enter other items' playback. */
final class Rac1HydroHostSound extends AbstractSoundInstance implements TickableSoundInstance {
    private final Rac1HydroHostMixer mixer;
    private final AudioStream stream;
    private final String voice;
    Rac1HydroHostSound(long seed,Point at,Rac1HydroAudio audio) {
        super(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID,"clank_hydro_host"),
                SoundSource.PLAYERS,RandomSource.create(seed));
        mixer=new Rac1HydroHostMixer(audio);position(at);volume=1;pitch=1;looping=false;
        voice=Long.toUnsignedString(seed,16)+"/"+Integer.toHexString(System.identityHashCode(this));
        stream=new Rac1HydroHostStream(mixer,voice);
        Rac1HydroTrace.LOG.transition("voice-create",voice,()->"voice="+voice+" location="+location
                +" hostVolume="+volume+" hostPitch="+pitch+" "+mixer.status());
    }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        sound=new Sound(location,ConstantFloat.of(1),ConstantFloat.of(1),1,Sound.Type.FILE,true,false,32);
        Rac1HydroTrace.LOG.transition("voice-resolve",voice,()->"voice="+voice+" streaming=true "+location);
        return new WeighedSoundEvents(location,null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,Sound selected,boolean loop) {
        Rac1HydroTrace.LOG.transition("stream-start",voice,()->"voice="+voice+" getStream called format="+stream.getFormat());
        return CompletableFuture.completedFuture(stream);
    }
    @Override public void tick() {}
    @Override public boolean isStopped(){return mixer.finished();}
    void position(Point at){x=at.x();y=at.y();z=at.z();}
    void release(){
        var before=Rac1HydroTrace.LOG.enabled()?mixer.status():null;
        mixer.release();
        if(before!=null) {
            var after=mixer.status();
            boolean dispatched=!before.closed()&&!before.released()&&after.released();
            Rac1HydroTrace.LOG.transition(dispatched?"voice-release":"voice-release-ignored",voice,
                    ()->"voice="+voice+" "+(dispatched?"ordinary-release":"release ignored; no new tail")+" "+after);
        }
    }
    void close(){
        mixer.close();Rac1HydroTrace.LOG.transition("voice-abort",voice,()->"voice="+voice+" hard-close "+mixer.status());
    }
}
