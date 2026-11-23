package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import java.io.IOException;
import java.util.Arrays;

/** Hydro-only optional importer. Does not relax the shared bank's single-tone contract. */
public record Rac1HydroAudio(Rac1HydroCueProgram program,Rac1AudioCatalog.Clip loop,
                             Rac1AudioCatalog.Clip tail) {
    public Rac1HydroAudio {
        if(program==null||loop==null||tail==null||!loop.repeat()||loop.loopStart()<0||tail.repeat())
            throw new IllegalArgumentException("Hydro needs a loop and an independent finite release");
    }
    public static Rac1HydroAudio read(byte[] bytes,Rac1SoundMap.Definition definition,
                                     Rac1DiscRevision revision) throws IOException {
        if(revision!=Rac1DiscRevision.NTSC_U_V1_00)throw new IOException("PAL Hydro release unverified");
        // The enclosing importer has already validated the reference bank; check again before decoding.
        String hash=SoundBytes.hash(bytes);
        if(!hash.equals("50e2f3ad91d16288ed2d4bd1da432e2824705c8b35d589e7a65ccf8da685f366"))
            throw new IOException("Unverified Hydro bank");
        var b=SoundBytes.view(bytes);
        int metadata=b.getInt(8),samples=b.getInt(16),sampleSize=b.getInt(20);
        int cues=metadata+b.getInt(metadata+0x1c),grains=metadata+b.getInt(metadata+0x20);
        byte[] cue=Arrays.copyOfRange(bytes,cues+37*12,cues+38*12);
        byte[] raw=Arrays.copyOfRange(bytes,grains+224*40,grains+234*40);
        var program=Rac1HydroCueProgram.read(definition,cue,raw);
        var clips=new Rac1AudioCatalog.Clip[2];
        for(int i=0;i<2;i++) {
            var ref=program.tones().get(i);
            // Only the verified cue37 waves; finite ADPCM end is mandatory.
            byte[] wave=Arrays.copyOfRange(bytes,samples,samples+sampleSize);
            var decoded=PsAdpcm.decode(wave,ref.sampleOffset(),i==0?259008:sampleSize);
            byte[] grain=Arrays.copyOfRange(raw,(ref.grain()-224)*40,(ref.grain()-223)*40);
            var tone=new Rac1SoundBank.Tone(37,ref.grain(),127,127,-74,66,1,cue,grain,
                    SoundBytes.hash(Arrays.copyOfRange(wave,ref.sampleOffset(),ref.sampleOffset()+decoded.frames()*16)),decoded);
            clips[i]=new Rac1AudioCatalog.Clip(decoded.pcm(),tone.sampleRate(),definition.metadata(),cue,grain,
                    hash,tone.adpcmHash(),37,decoded.loopStart(),decoded.loopEnd(),decoded.repeat());
        }
        return new Rac1HydroAudio(program,clips[0],clips[1]);
    }
}
