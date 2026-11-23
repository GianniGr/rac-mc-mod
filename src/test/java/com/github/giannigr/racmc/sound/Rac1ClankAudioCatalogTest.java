package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.Rac1ClankAv.Loop;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Synthetic zero PCM plus numeric metadata only. No retail cue/grain/sample payloads. */
class Rac1ClankAudioCatalogTest {
    static Rac1SoundMap.Definition definition(Loop loop) {
        byte[] metadata=new byte[32];
        SoundBytes.view(metadata).putShort(26,(short)loop.local()).putInt(12,1024);
        return new Rac1SoundMap.Definition(loop==Loop.HELI?24:36,metadata);
    }
    static Rac1SoundBank.Tone tone(Loop loop) {
        boolean h=loop==Loop.HELI;
        byte[] cue=new byte[12],grain=new byte[40];cue[0]=127;grain[9]=127;
        return new Rac1SoundBank.Tone(h?24:36,h?207:223,127,127,h?-80:-74,h?124:65,1,cue,grain,
                h?"62babdaeb3f9ac47352498b38559a1f76f4503bc1fe7ec988e103018ebc3a471"
                        :"17a3388e73b2d21631cd2a62dbc5bd52b8625da4f3cfd17d49c15562741a109d",
                new PsAdpcm.Decoded(new byte[(h?20440:13468)*2],h?730:481,h?28:168,h?20440:13468,true));
    }
    static Rac1AudioCatalog.Clip clip(Loop loop) throws IOException {
        return Rac1ClankAudioCatalog.verifiedClip(loop,definition(loop),tone(loop),"a".repeat(64));
    }
    static Rac1ClankAudioCatalog catalog() {
        return Rac1ClankAudioCatalog.read(Rac1ClankAudioCatalogTest::clip);
    }
    @Test void exactRatesMarkersAndIndependentImmutableCapability() throws Exception {
        var catalog=catalog();
        assertEquals(Set.of(Loop.HELI,Loop.THRUSTER),catalog.clips().keySet());
        assertTrue(catalog.failures().isEmpty());
        var h=catalog.clips().get(Loop.HELI);var t=catalog.clips().get(Loop.THRUSTER);
        assertEquals(15984.375f,h.rate());assertEquals(28,h.loopStart());assertEquals(20440,h.loopEnd());
        assertEquals(22019.53125f,t.rate());assertEquals(168,t.loopStart());assertEquals(13468,t.loopEnd());
        assertTrue(h.repeat());assertTrue(t.repeat());
        h.pcm()[0]=99;assertEquals(0,h.pcm()[0]);
        assertThrows(UnsupportedOperationException.class,()->catalog.clips().clear());
        assertThrows(IllegalArgumentException.class,()->new Rac1ClankAudioCatalog(Map.of(Loop.HYDRO,h),Map.of()));
        assertThrows(IOException.class,()->clip(Loop.HYDRO));
    }
    @Test void failureOfEitherCueNeverPoisonsOtherCueOrSharedGeneration() {
        var shared=new Rac1AudioGeneration();long token=shared.invalidate();
        var baseline=Rac1SoundPipelineTest.catalog();shared.publish(token,baseline);
        for(var bad:List.of(Loop.HELI,Loop.THRUSTER)) {
            var partial=Rac1ClankAudioCatalog.read(loop->{
                if(loop==bad)throw new IOException("Synthetic optional corruption");
                return clip(loop);
            });
            assertEquals(1,partial.clips().size());assertEquals(Set.of(bad),partial.failures().keySet());
            assertFalse(partial.clips().containsKey(bad));assertSame(baseline,shared.current());
        }
        assertEquals(2,Rac1ClankAudioCatalog.read(loop->{throw new IOException("bad");}).failures().size());
    }
    @Test void importReloadAndMediaInvalidationFenceStaleCompletions() {
        var state=new Rac1ClankAudioGeneration();var good=catalog();
        long old=state.invalidate(),current=state.invalidate();
        assertFalse(state.publish(old,good));assertNull(state.current().catalog());
        assertTrue(state.publish(current,good));
        state.reload();assertSame(good,state.current().catalog());
        assertFalse(state.publish(current,null));assertSame(good,state.current().catalog());
        long replacement=state.invalidate();assertNull(state.current().catalog());
        assertFalse(state.publish(current,good));assertTrue(state.publish(replacement,good));
        state.invalidate();assertFalse(state.publish(replacement,good));assertNull(state.current().catalog());
    }
    @Test void pendingImportCannotPublishAcrossReloadAndFailedGenerationCanBeRetried() {
        var state=new Rac1ClankAudioGeneration();long pending=state.invalidate();
        state.reload();assertFalse(state.publish(pending,catalog()));
        assertNull(state.current().catalog());
        long retry=state.invalidate();assertTrue(state.publish(retry,null));
        assertNull(state.current().catalog());
        assertTrue(state.publish(state.invalidate(),catalog()));
    }
    @Test void wrongCueIdentityDefinitionHashOrLoopFailsClosed() {
        var original=tone(Loop.HELI);
        var wrongHash=new Rac1SoundBank.Tone(24,207,127,127,-80,124,1,original.rawCue(),original.rawGrain(),
                "b".repeat(64),original.decoded());
        assertThrows(IOException.class,()->Rac1ClankAudioCatalog.verifiedClip(
                Loop.HELI,definition(Loop.HELI),wrongHash,"a".repeat(64)));
        assertThrows(IOException.class,()->Rac1ClankAudioCatalog.verifiedClip(
                Loop.HELI,definition(Loop.THRUSTER),original,"a".repeat(64)));
        for(var decoded:List.of(
                new PsAdpcm.Decoded(new byte[40880],730,0,20440,true),
                new PsAdpcm.Decoded(new byte[40880],730,28,20440,false),
                new PsAdpcm.Decoded(new byte[40880],730,28,20439,true))) {
            var wrong=new Rac1SoundBank.Tone(24,207,127,127,-80,124,1,original.rawCue(),original.rawGrain(),
                    original.adpcmHash(),decoded);
            assertThrows(IOException.class,()->Rac1ClankAudioCatalog.verifiedClip(
                    Loop.HELI,definition(Loop.HELI),wrong,"a".repeat(64)));
        }
    }
}
