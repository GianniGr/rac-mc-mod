package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.sound.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class Rac1ClankCueBoundaryTest {
    private static Rac1SoundBank.Tone tone(int note,int fine) {
        return new Rac1SoundBank.Tone(0,0,127,127,note,fine,1,
                new byte[12],new byte[40],"a".repeat(64),
                new PsAdpcm.Decoded(new byte[56],1,0,28,true));
    }
    @Test void verifiedSignedNotesUseIntegerSpu2PitchNotUnsignedMidiOrSmoothApproximation() throws Exception {
        // Synthetic metadata only. No retail ADPCM/PCM or raw cue/grain data in this fixture.
        assertEquals(15984.375f,tone(-80,124).sampleRate());
        assertEquals(22019.53125f,tone(-74,65).sampleRate());
        // Rate verification alone does not authorize treating Hydro's first tone as its program.
        assertEquals(22031.25f,tone(-74,66).sampleRate());
        assertEquals(48000f,tone(-60,0).sampleRate());
        assertEquals(24000f,tone(-72,0).sampleRate());
        assertEquals(12000f,tone(-84,0).sampleRate());
    }
    @Test void existingPositiveNoteRatesRemainBitIdentical() throws Exception {
        for(int note=0;note<=127;note++)for(int fine=0;fine<=127;fine++) {
            float previous=(float)(44100.0*Math.pow(2.0,(60-note-fine/128.0)/12.0));
            assertEquals(Float.floatToIntBits(previous),Float.floatToIntBits(tone(note,fine).sampleRate()));
        }
    }
    @Test void invalidEncodingStillFailsClosed() {
        for(int[] invalid:new int[][]{{-129,0},{128,0},{-74,-1},{-74,128},{60,128}})
            assertThrows(IOException.class,()->tone(invalid[0],invalid[1]).sampleRate());
    }
}
