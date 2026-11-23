package com.github.giannigr.racmc.client.sound;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import com.github.giannigr.racmc.sound.*;
import java.nio.*;
import org.junit.jupiter.api.Test;

/** Actual sound/getStream/byte adapter, no Minecraft singleton, window, OpenAL or device launch. */
class Rac1HydroHostSoundTest {
    @Test void actualLocalSoundResolvesAndStartsNonzeroLittleEndianStreamAtRetainedGain()throws Exception {
        var audio=HydroAudioFixture.audio();
        assertEquals(614/1024f,audio.loop().gain());
        var sound=new Rac1HydroHostSound(36,new Point(0,0,0),audio);
        assertNotNull(sound.resolve(null));
        assertFalse(sound.isStopped());assertEquals(1,sound.getVolume());assertEquals(1,sound.getPitch());
        var stream=sound.getStream(null,null,false).join();
        assertEquals(48000,stream.getFormat().getSampleRate());
        assertEquals(16,stream.getFormat().getSampleSizeInBits());
        assertEquals(1,stream.getFormat().getChannels());assertFalse(stream.getFormat().isBigEndian());
        var expected=new Rac1HydroHostMixer(audio);short[] samples=new short[2048];
        assertEquals(samples.length,expected.read(samples,0,samples.length));
        var bytes=stream.read(16384).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(4096,bytes.remaining());int peak=0;
        for(short sample:samples){assertEquals(sample,bytes.getShort());peak=Math.max(peak,Math.abs((int)sample));}
        assertTrue(peak>100,"Host byte stream is neither empty nor silent at retained near gain");
        for(int block=0;block<7;block++) {
            assertEquals(samples.length,expected.read(samples,0,samples.length));
            var ramped=stream.read(4096).order(ByteOrder.LITTLE_ENDIAN);
            for(short sample:samples)assertEquals(sample,ramped.getShort());
        }
        assertEquals(-1,expected.pitchBend(),"Actual stream reads pass through the complete dynamic ramp");
        sound.close();assertTrue(sound.isStopped());assertEquals(0,stream.read(4096).remaining());
        sound.close();stream.close();expected.close();
    }
    @Test void releaseDrainsIndependentVoicesButAbortProducesImmediateEndOfStream()throws Exception {
        var sound=new Rac1HydroHostSound(36,new Point(0,0,0),HydroAudioFixture.audio());
        var stream=sound.getStream(null,null,false).join();stream.read(4096);sound.release();
        int remaining=0;for(int i=0;i<500&&!sound.isStopped();i++)remaining+=stream.read(4096).remaining();
        assertTrue(remaining>0);assertTrue(sound.isStopped());assertEquals(0,stream.read(4096).remaining());
        sound.close();stream.close();
        var abort=new Rac1HydroHostSound(37,new Point(0,0,0),HydroAudioFixture.audio());
        var aborted=abort.getStream(null,null,false).join();abort.close();abort.release();
        assertEquals(0,aborted.read(4096).remaining());assertTrue(abort.isStopped());
    }
}
