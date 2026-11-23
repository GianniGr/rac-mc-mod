package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class Rac1HydroAudioTest {
    @Test void palRefusalAndUnverifiedBankPrecedeAnySampleRead() {
        var def=new Rac1SoundMap.Definition(37,new byte[32]);
        assertThrows(IOException.class,()->Rac1HydroAudio.read(new byte[0],def,Rac1DiscRevision.PAL_V2_00));
        assertThrows(IOException.class,()->Rac1HydroAudio.read(new byte[0],def,Rac1DiscRevision.NTSC_U_V1_00));
        assertThrows(IOException.class,()->Rac1HydroAudio.read(new byte[64],def,Rac1DiscRevision.NTSC_U_V1_00));
    }
    @Test void partialOrReversedVoicesCannotBecomeAHostProgram() throws Exception {
        var good=Rac1HydroHostMixerTest.audio();
        assertThrows(IllegalArgumentException.class,()->new Rac1HydroAudio(good.program(),good.tail(),good.loop()));
        assertThrows(IllegalArgumentException.class,()->new Rac1HydroAudio(good.program(),good.loop(),null));
    }
}
