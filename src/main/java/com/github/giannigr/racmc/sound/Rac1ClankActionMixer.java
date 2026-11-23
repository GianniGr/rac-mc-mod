package com.github.giannigr.racmc.sound;

import java.nio.*;
import java.util.Random;

/** Only the bounded action programs. 48-kHz host adaptation; Hydro and other item mixers untouched. */
public final class Rac1ClankActionMixer implements AutoCloseable {
    public record Choice(int tone,int bend,boolean audible) {}
    /** Native-local RNG/register/previous-choice namespace, not original shared bank/global RNG parity. */
    public static final class Selector {
        private int previous;
        public Choice choose(Rac1ClankActionProgram program,Random random) {
            if(program.local()!=62)return new Choice(0,0,true);
            // SET_REGISTER_RAND[0,39], TEST_REGISTER >=2 comparator0: only register0==0 passes STOP.
            if(random.nextInt(40)!=0)return new Choice(0,0,false);
            int bend=86*((65535*random.nextInt(32767))/32767-32768)/100;
            int tone=random.nextInt(2);if(tone==previous)tone=(tone+1)%2;previous=tone;
            return new Choice(tone,bend,true);
        }
    }
    private final Rac1ClankActionProgram program;
    private final Rac1AudioCatalog.Clip clip;
    private short[] pcm;
    private final Envelope envelope;
    private final double increment;
    private final float gain;
    private double cursor;
    private long frames;
    private boolean closed;
    public Rac1ClankActionMixer(Rac1ClankActionProgram program,Choice choice,long seed) {
        this.program=program;clip=program.tones().get(choice.tone());gain=clip.gain();
        var bytes=ByteBuffer.wrap(clip.pcm()).order(ByteOrder.LITTLE_ENDIAN);
        pcm=new short[bytes.remaining()/2];bytes.asShortBuffer().get(pcm);
        var grain=ByteBuffer.wrap(clip.grain()).order(ByteOrder.LITTLE_ENDIAN);
        envelope=new Envelope(Short.toUnsignedInt(grain.getShort(18)),Short.toUnsignedInt(grain.getShort(20)));
        int bend=choice.bend(),fine=bend*128*clip.grain()[bend<0?16:17]/(bend<0?32768:32767);
        double pitch=program.local()==1?Rac1ClankHighEntryAudio.pitch(clip,new Random(seed))
                :StrictMath.pow(2,fine/1536.0);
        increment=clip.rate()/48000*pitch;
        closed=!choice.audible();
    }
    public synchronized int read(short[] output,int offset,int length) {
        java.util.Objects.checkFromIndexSize(offset,length,output.length);
        int n=0;
        while(n<length&&!finished()) {
            if(frames==program.keyOff240()*200L)envelope.release();
            double value=0;
            if(frames>=program.startDelay240()*200L&&cursor<pcm.length&&!envelope.done()) {
                int a=(int)cursor,b=a+1;
                if(b>=pcm.length)b=clip.repeat()?clip.loopStart():a;
                value=(pcm[a]+(pcm[b]-pcm[a])*(cursor-a))*envelope.next()/32767.0*gain;
                cursor+=increment;
                if(clip.repeat()&&cursor>=pcm.length)
                    cursor=clip.loopStart()+(cursor-clip.loopStart())%(pcm.length-clip.loopStart());
            }
            output[offset+n++]=(short)Math.clamp(Math.round(value),-32768L,32767L);frames++;
        }
        return n;
    }
    public synchronized boolean finished() {
        if(closed)return true;
        if(program.stop240()>=0)return frames>=program.stop240()*200L;
        return cursor>=pcm.length||envelope.done();
    }
    @Override public synchronized void close(){closed=true;pcm=new short[0];}
    /** Independently bounded host ADSR; same public field semantics as documented Hydro adaptation. */
    private static final class Envelope {
        final int a,b;int phase,level,counter;
        Envelope(int a,int b){this.a=a;this.b=b;}
        boolean done(){return phase==4;}
        void release(){if(!done()){phase=3;counter=0;}}
        int next() {
            if(done())return 0;
            int shift,step,target;boolean decrease,exp;
            switch(phase) {
                case 0 -> {shift=a>>>10&31;step=7-(a>>>8&3);target=32767;decrease=false;exp=(a&32768)!=0;}
                case 1 -> {shift=a>>>4&15;step=-8;target=Math.min(32767,((a&15)+1)*2048);decrease=true;exp=true;}
                case 2 -> {shift=b>>>8&31;decrease=(b&16384)!=0;step=decrease?-8+(b>>>6&3):7-(b>>>6&3);target=0;exp=(b&32768)!=0;}
                default -> {shift=b&31;step=-8;target=0;decrease=true;exp=(b&32)!=0;}
            }
            int period=1<<Math.max(0,shift-11);step*=1<<Math.max(0,11-shift);
            if(exp&&!decrease&&level>24576)period*=4;
            if(++counter>=period) {
                counter=0;if(exp&&decrease)step=step*level>>15;
                level=Math.clamp(level+step,0,32767);
                if(phase!=2&&(decrease?level<=target:level>=target)){phase++;counter=0;}
            }
            return level;
        }
    }
}
