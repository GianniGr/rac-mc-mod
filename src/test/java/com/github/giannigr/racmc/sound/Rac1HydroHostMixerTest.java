package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Synthetic signals only; no retail assets, files or sound device. */
class Rac1HydroHostMixerTest {
    static Rac1HydroAudio audio() throws Exception {
        var def=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        def.putShort(26,(short)19);def.putInt(12,1024);
        var cue=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        cue.put(0,(byte)127);cue.put(4,(byte)10);cue.putInt(8,224*40);
        var raw=ByteBuffer.allocate(400).order(ByteOrder.LITTLE_ENDIAN);
        int[] op={28,30,1,21,32,29,34,22,43,1};
        for(int i=0;i<10;i++){raw.putInt(i*40,op[i]);raw.putInt(i*40+4,i==6?1:0);}
        raw.putShort(8,(short)-126);raw.putShort(50,(short)-63);raw.putShort(208,(short)2);
        for(int i:new int[]{2,9}) {
            int at=i*40;raw.put(at+8,(byte)100);raw.put(at+9,(byte)127);
            raw.put(at+10,(byte)-74);raw.put(at+11,(byte)66);
            raw.putShort(at+16,(short)(i==2?3:0));raw.putShort(at+18,(short)(i==2?0xacff:0xa1ff));
            raw.putShort(at+20,(short)(i==2?0x9fcd:0x9fc5));raw.putShort(at+22,(short)1);
            raw.putInt(at+24,i==2?254752:259008);
        }
        var program=Rac1HydroCueProgram.read(new Rac1SoundMap.Definition(37,def.array()),cue.array(),raw.array());
        var clips=new Rac1AudioCatalog.Clip[2];
        for(int i=0;i<2;i++) {
            byte[] pcm=new byte[2048];
            var b=ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            for(int j=0;j<1024;j++)b.putShort((short)(10000*StrictMath.sin(j*.05)));
            byte[] grain=Arrays.copyOfRange(raw.array(),i==0?80:360,i==0?120:400);
            clips[i]=new Rac1AudioCatalog.Clip(pcm,22019.53125f,def.array(),cue.array(),grain,
                    "a".repeat(64),"b".repeat(64),37,i==0?28:-1,1024,i==0);
        }
        return new Rac1HydroAudio(program,clips[0],clips[1]);
    }
    @Test void sampleClockAndPcmAreInvariantToReadPartitionsAndCallerRenders() throws Exception {
        var a=new Rac1HydroHostMixer(audio());var b=new Rac1HydroHostMixer(audio());
        short[] expected=new short[15000],actual=new short[15000];
        assertEquals(15000,a.read(expected,0,15000));
        var random=new Random(36);
        for(int at=0;at<actual.length;) {
            int count=Math.min(actual.length-at,1+random.nextInt(513));
            assertEquals(count,b.read(actual,at,count));at+=count;
        }
        assertArrayEquals(expected,actual);assertEquals(-1,a.pitchBend());assertEquals(1,a.voices());
        a.close();b.close();
    }
    @Test void earlyAndSustainedReleaseOwnTwoVoicesFinishAndNeverRestart() throws Exception {
        for(int time:new int[]{0,1,199,200,6300,15000}) {
            var mixer=new Rac1HydroHostMixer(audio());
            mixer.read(new short[time],0,time);int bend=mixer.pitchBend();
            mixer.release();mixer.release();assertEquals(2,mixer.voices());
            assertEquals(bend,mixer.pitchBend());
            short[] chunk=new short[317];
            for(int i=0;i<2000&&!mixer.finished();i++)mixer.read(chunk,0,chunk.length);
            assertTrue(mixer.finished());assertEquals(0,mixer.voices());
            assertEquals(0,mixer.read(chunk,0,chunk.length));mixer.close();mixer.release();
        }
    }
    @Test void abortIsIdempotentAndCannotEmitTailOrQueuedFrames() throws Exception {
        var mixer=new Rac1HydroHostMixer(audio());mixer.read(new short[1000],0,1000);
        mixer.close();mixer.close();mixer.release();
        assertEquals(0,mixer.voices());assertTrue(mixer.finished());
        assertEquals(0,mixer.read(new short[100],0,100));
        assertThrows(IndexOutOfBoundsException.class,()->mixer.read(new short[10],9,2));
    }
    @Test void releaseBoundaryIsPartitionIndependent() throws Exception {
        var a=new Rac1HydroHostMixer(audio());var b=new Rac1HydroHostMixer(audio());
        a.read(new short[999],0,999);b.read(new short[500],0,500);b.read(new short[499],0,499);
        a.release();b.release();short[] x=new short[20000],y=new short[20000];
        int count=a.read(x,0,x.length),at=0;
        while(!b.finished())at+=b.read(y,at,Math.min(73,y.length-at));
        assertEquals(count,at);assertArrayEquals(x,y);
    }
}
