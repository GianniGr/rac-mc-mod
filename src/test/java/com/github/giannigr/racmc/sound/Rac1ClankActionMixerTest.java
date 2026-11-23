package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class Rac1ClankActionMixerTest {
    static Rac1ClankActionProgram program(int local) {
        var clips=new ArrayList<Rac1AudioCatalog.Clip>();
        for(int i=0;i<(local==62?2:1);i++) {
            var pcm=ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
            for(int n=0;n<1024;n++)pcm.putShort((short)(i==0?5000:-6000));
            var def=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);def.putInt(12,1024);
            byte[] cue=new byte[12];cue[0]=127;
            var grain=ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
            grain.put(9,(byte)127);grain.putShort(18,(short)0x80ff);grain.putShort(20,(short)0x9fc5);
            grain.put(16,(byte)1);grain.put(17,(byte)2);
            boolean repeat=local==43||local==46;
            clips.add(new Rac1AudioCatalog.Clip(pcm.array(),22050,def.array(),cue,grain.array(),
                    "a".repeat(64),"b".repeat(64),local==1?23:54,repeat?28:-1,1024,repeat));
        }
        return new Rac1ClankActionProgram(local,clips);
    }
    @Test void retainedScriptShapesIncludeAllControlGrainsAndExactDelayOperands() {
        for(int local:new int[]{43,46,62}) {
            var bytes=Rac1ClankActionProgram.metadata(local);assertEquals(local==62?280:120,bytes.length);
            var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if(local==62){assertEquals(31,b.getInt());assertEquals(39,b.getShort(12));}
            else {assertEquals(1,b.getInt());assertEquals(41,b.getInt(40));assertEquals(24,b.getInt(80));}
        }
    }
    @Test void scriptsRespectStartDelayKeyoffStopAndPcmPartitioning() {
        for(int local:new int[]{43,46}) {
            var p=program(local);var choice=new Rac1ClankActionMixer.Choice(0,0,true);
            var a=new Rac1ClankActionMixer(p,choice,36);var b=new Rac1ClankActionMixer(p,choice,36);
            int samples=p.stop240()*200;
            short[] x=new short[samples],y=new short[samples];assertEquals(samples,a.read(x,0,samples));
            for(int at=0;at<samples;) {int n=Math.min(317,samples-at);assertEquals(n,b.read(y,at,n));at+=n;}
            assertArrayEquals(x,y);assertTrue(a.finished());assertTrue(b.finished());
            for(int n=0;n<p.startDelay240()*200;n++)assertEquals(0,x[n]);
            assertTrue(x[p.startDelay240()*200+1000]>0);
            assertEquals(0,x[samples-1]); // Keyoff envelope reaches zero before final STOP.
            a.close();b.close();assertEquals(0,a.read(new short[10],0,10));
        }
    }
    @Test void actualRegisterGateIsUsuallySilentAndAudibleRandomPlayAlternatesRealToneChoices() {
        var selector=new Rac1ClankActionMixer.Selector();var random=new Random(36);int audible=0,previous=0;
        var program=program(62);
        for(int i=0;i<10000;i++) {
            var choice=selector.choose(program,random);
            if(!choice.audible())continue;
            audible++;assertNotEquals(previous,choice.tone());previous=choice.tone();
            assertTrue(choice.bend()>=-28181&&choice.bend()<=28181);
        }
        assertTrue(audible>150&&audible<350,"Not an unconditional grunt or first-tone substitute");
        var silent=new Rac1ClankActionMixer(program,new Rac1ClankActionMixer.Choice(0,0,false),36);
        assertTrue(silent.finished());assertEquals(0,silent.read(new short[10],0,10));silent.close();
    }
    @Test void hardAbortCancelsDelayedAndPlayingProgramsIdempotently() {
        for(int elapsed:new int[]{0,100,10000}) {
            var mixer=new Rac1ClankActionMixer(program(43),new Rac1ClankActionMixer.Choice(0,0,true),36);
            mixer.read(new short[elapsed],0,elapsed);mixer.close();mixer.close();
            assertTrue(mixer.finished());assertEquals(0,mixer.read(new short[100],0,100));
        }
    }
}
