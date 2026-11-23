package com.github.giannigr.racmc.sound;

import java.nio.*;

/** Synthetic signal only, with the retained nonzero near gain614/1024; never packaged. */
public final class HydroAudioFixture {
    private HydroAudioFixture() {}
    public static Rac1HydroAudio audio()throws Exception {
        var a=Rac1HydroHostMixerTest.audio();
        return new Rac1HydroAudio(a.program(),gain(a.loop()),gain(a.tail()));
    }
    private static Rac1AudioCatalog.Clip gain(Rac1AudioCatalog.Clip c) {
        var d=ByteBuffer.wrap(c.definition()).order(ByteOrder.LITTLE_ENDIAN);d.putInt(12,614);
        return new Rac1AudioCatalog.Clip(c.pcm(),c.rate(),d.array(),c.cue(),c.grain(),
                c.bankHash(),c.adpcmHash(),c.cueId(),c.loopStart(),c.loopEnd(),c.repeat());
    }
}
