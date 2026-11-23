package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.clank.Rac1HydroTrace;
import com.github.giannigr.racmc.sound.Rac1HydroHostMixer;
import java.nio.*;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;

/** Actual Hydro AudioStream adapter, headless-testable without opening a sound device. */
public final class Rac1HydroHostStream implements AudioStream {
    private final Rac1HydroHostMixer mixer;
    private final String voice;
    private boolean read,nonzero,ramp,release;
    public Rac1HydroHostStream(Rac1HydroHostMixer mixer,String voice) {this.mixer=mixer;this.voice=voice;}
    @Override public AudioFormat getFormat(){return new AudioFormat(48000,16,1,true,false);}
    @Override public ByteBuffer read(int requested) {
        short[] samples=new short[Math.min(Math.max(0,requested/2),2048)];
        int count=mixer.read(samples,0,samples.length);
        var bytes=ByteBuffer.allocateDirect(count*2).order(ByteOrder.LITTLE_ENDIAN);
        int peak=0,nonzeroSamples=0;
        for(int i=0;i<count;i++) {
            bytes.putShort(samples[i]);
            if(Rac1HydroTrace.LOG.enabled()) {
                peak=Math.max(peak,Math.abs((int)samples[i]));if(samples[i]!=0)nonzeroSamples++;
            }
        }
        if(Rac1HydroTrace.LOG.enabled()) {
            var status=mixer.status();
            String event=!read?"first-read":!nonzero&&nonzeroSamples>0?"first-nonzero"
                    :!ramp&&status.bend()==-1?"ramp-complete":!release&&status.released()?"release-pcm":null;
            if(event!=null) {
                int max=peak,nz=nonzeroSamples;
                Rac1HydroTrace.LOG.transition("stream",voice+":"+event,()->"voice="+voice+" event="+event
                        +" requestedBytes="+requested+" samples="+count+" nonzero="+nz+" peak="+max+" "+status);
            }
            read=true;nonzero|=nonzeroSamples>0;ramp|=status.bend()==-1;release|=status.released();
        }
        return bytes.flip();
    }
    @Override public void close() {
        mixer.close();
        Rac1HydroTrace.LOG.transition("stream-close",voice,()->"voice="+voice+" "+mixer.status());
    }
}
