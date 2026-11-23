package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import java.nio.*;
import java.util.List;

/**
 * Feature-local 48-kHz mono host adaptation: linear resampling, authored ADSR and
 * sample-clocked 240-Hz commands. Not an SPU2 emulator. No file or device access.
 */
public final class Rac1HydroHostMixer implements AutoCloseable {
    public static final int RATE=48000;
    private final Rac1HydroAudio audio;
    private final Rac1HydroProgramClock clock;
    private Voice loop,tail;
    private long frames;
    private boolean closed,released;
    private static final class Voice {
        final short[] pcm;
        final Rac1AudioCatalog.Clip clip;
        final Envelope envelope;
        final float gain;
        double cursor,increment;
        Voice(Rac1AudioCatalog.Clip clip) {
            this.clip=clip;gain=clip.gain();
            var bytes=ByteBuffer.wrap(clip.pcm()).order(ByteOrder.LITTLE_ENDIAN);
            pcm=new short[bytes.remaining()/2];bytes.asShortBuffer().get(pcm);
            var grain=SoundBytes.view(clip.grain());
            envelope=new Envelope(Short.toUnsignedInt(grain.getShort(18)),Short.toUnsignedInt(grain.getShort(20)));
            pitch(0);
        }
        void pitch(int bend) {
            byte[] grain=clip.grain();
            int fine=bend*128*grain[bend<0?16:17]/(bend<0?32768:32767);
            increment=clip.rate()/RATE*StrictMath.pow(2,fine/1536.0);
        }
        double next() {
            if(envelope.done()||cursor>=pcm.length)return 0;
            int a=(int)cursor,b=a+1;
            if(b>=pcm.length)b=clip.repeat()?clip.loopStart():a;
            double sample=pcm[a]+(pcm[b]-pcm[a])*(cursor-a);
            cursor+=increment;
            if(clip.repeat()&&cursor>=pcm.length)
                cursor=clip.loopStart()+(cursor-clip.loopStart())%(pcm.length-clip.loopStart());
            return sample*envelope.next()/32767.0*gain;
        }
        boolean done(){return envelope.done()||!clip.repeat()&&cursor>=pcm.length;}
    }
    /** Register-field interpretation from pinned public 989snd envelope; bounded host arithmetic. */
    private static final class Envelope {
        final int a,b;
        int phase,level,counter;
        Envelope(int a,int b){this.a=a;this.b=b;}
        boolean done(){return phase==4;}
        void release(){if(!done()){phase=3;counter=0;}}
        int next() {
            if(done())return 0;
            int shift,step,target;boolean decrease,exp;
            switch(phase) {
                case 0 -> {shift=(a>>>10)&31;step=7-((a>>>8)&3);target=32767;decrease=false;exp=(a&0x8000)!=0;}
                case 1 -> {shift=(a>>>4)&15;step=-8;target=Math.min(32767,((a&15)+1)*2048);decrease=true;exp=true;}
                case 2 -> {shift=(b>>>8)&31;decrease=(b&0x4000)!=0;
                    step=decrease?-8+((b>>>6)&3):7-((b>>>6)&3);target=0;exp=(b&0x8000)!=0;}
                default -> {shift=b&31;step=-8;target=0;decrease=true;exp=(b&32)!=0;}
            }
            int period=1<<Math.max(0,shift-11);
            step*=1<<Math.max(0,11-shift);
            if(exp&&!decrease&&level>0x6000)period*=4;
            if(++counter>=period) {
                counter=0;
                if(exp&&decrease)step=(step*level)>>15;
                level=Math.clamp(level+step,0,32767);
                if(phase!=2&&(decrease?level<=target:level>=target)){phase++;counter=0;}
            }
            return level;
        }
    }
    public Rac1HydroHostMixer(Rac1HydroAudio audio) {
        this.audio=audio;clock=new Rac1HydroProgramClock(audio.program(),Rac1DiscRevision.NTSC_U_V1_00);
        apply(clock.start());
    }
    private void apply(List<Rac1HydroProgramClock.Command> commands) {
        for(var c:commands)switch(c.kind()) {
            case KEY_ON_LOOP -> {loop=new Voice(audio.loop());loop.pitch(c.pitchBend());}
            case PITCH -> {if(loop!=null)loop.pitch(c.pitchBend());}
            case KEY_OFF_LOOP -> {if(loop!=null)loop.envelope.release();}
            case KEY_ON_TAIL -> tail=new Voice(audio.tail()); // PB range zero, new authored ADSR.
            case HARD_STOP -> {loop=null;tail=null;}
        }
    }
    /** Applied at the next not-yet-rendered sample; queued device PCM cannot be retracted. */
    public synchronized void release() {
        if(closed||released)return;released=true;apply(clock.release());
    }
    public synchronized int read(short[] output,int offset,int length) {
        java.util.Objects.checkFromIndexSize(offset,length,output.length);
        int written=0;
        while(written<length&&!finished()) {
            if(frames>0&&frames%200==0)apply(clock.tick240());
            double value=(loop==null?0:loop.next())+(tail==null?0:tail.next());
            output[offset+written++]=(short)Math.clamp(Math.round(value),-32768L,32767L);frames++;
            if(loop!=null&&loop.done())loop=null;
            if(tail!=null&&tail.done())tail=null;
        }
        return written;
    }
    public synchronized boolean finished(){return closed||released&&loop==null&&tail==null;}
    public synchronized long frames(){return frames;}
    public synchronized int voices(){return (loop==null?0:1)+(tail==null?0:1);}
    public synchronized int pitchBend(){return clock.pitchBend();}
    public record Status(long frames,int voices,int bend,boolean released,boolean closed,
                         float loopGain,float tailGain,double loopIncrement,int loopLevel,int tailLevel) {}
    public synchronized Status status() {
        return new Status(frames,voices(),clock.pitchBend(),released,closed,audio.loop().gain(),audio.tail().gain(),
                loop==null?0:loop.increment,loop==null?0:loop.envelope.level,tail==null?0:tail.envelope.level);
    }
    @Override public synchronized void close(){if(!closed){closed=true;apply(clock.abort());loop=null;tail=null;}}
}
