package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import com.github.giannigr.racmc.sound.*;
import java.nio.*;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/** Separate bounded action channel; never the glide/Hydro or another item's sound. */
final class Rac1ClankActionSound extends AbstractSoundInstance implements TickableSoundInstance {
    private final Rac1ClankActionMixer mixer;
    private final AudioStream stream;
    Rac1ClankActionSound(long seed,Point at,Rac1ClankActionProgram program,Rac1ClankActionMixer.Choice choice) {
        super(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID,"clank_action_"+program.local()),
                SoundSource.PLAYERS,RandomSource.create(seed));
        mixer=new Rac1ClankActionMixer(program,choice,seed);position(at);volume=1;pitch=1;looping=false;
        stream=new AudioStream() {
            public AudioFormat getFormat(){return new AudioFormat(48000,16,1,true,false);}
            public ByteBuffer read(int requested) {
                short[] samples=new short[Math.min(Math.max(0,requested/2),2048)];
                int n=mixer.read(samples,0,samples.length);
                var bytes=ByteBuffer.allocateDirect(n*2).order(ByteOrder.LITTLE_ENDIAN);
                for(int i=0;i<n;i++)bytes.putShort(samples[i]);return bytes.flip();
            }
            public void close(){mixer.close();}
        };
    }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        sound=new Sound(location,ConstantFloat.of(1),ConstantFloat.of(1),1,Sound.Type.FILE,true,false,32);
        return new WeighedSoundEvents(location,null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,Sound selected,boolean loop) {
        return CompletableFuture.completedFuture(stream);
    }
    @Override public void tick() {}
    @Override public boolean isStopped(){return mixer.finished();}
    void position(Point at){x=at.x();y=at.y();z=at.z();}
    void close(){mixer.close();}
}
