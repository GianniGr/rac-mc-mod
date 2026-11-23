package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.util.*;

/**
 * Hydro-only ten-grain metadata reader, not a single-tone decoder or a general 989SND VM.
 * NTSC stop-time dispatch is verified separately; host playback is an explicit non-bit-exact adaptation.
 */
public final class Rac1HydroCueProgram {
    public record Grain(int index,int opcode,int delay,byte[] operands) {
        public Grain {operands=operands.clone();}
        @Override public byte[] operands(){return operands.clone();}
    }
    public record ToneReference(int grain,int sampleOffset,int centerNote,int fine,int flags,
                                int adsr1,int adsr2) {}
    private static final int[] OPCODES={28,30,1,21,32,29,34,22,43,1};
    private final List<Grain> grains;
    private final List<ToneReference> tones;
    private Rac1HydroCueProgram(List<Grain> grains,List<ToneReference> tones) {
        this.grains=List.copyOf(grains);this.tones=List.copyOf(tones);
    }
    public List<Grain> grains(){return grains;}
    public List<ToneReference> tones(){return tones;}
    /**
     * Inputs are the already resolved class0/local19 definition, cue37 row and its ten grains.
     * This reads no waveform/ISO, does not imply permission to obtain them and cannot emit audio.
     */
    public static Rac1HydroCueProgram read(Rac1SoundMap.Definition definition,byte[] cue,byte[] program)
            throws IOException {
        byte[] metadata=definition.metadata();
        if(definition.cue()!=37||metadata.length!=32||SoundBytes.view(metadata).getShort(26)!=19
                ||cue.length!=12||program.length!=400)
            throw new IOException("Hydro requires the complete class0/local19 cue37 program");
        var cb=SoundBytes.view(cue);
        if((cue[0]&255)!=127||(cue[4]&255)!=10||cb.getInt(8)!=224*40)
            throw new IOException("Hydro cue row differs from retained metadata");
        var b=SoundBytes.view(program);var grains=new ArrayList<Grain>();var tones=new ArrayList<ToneReference>();
        for(int i=0;i<10;i++) {
            int at=i*40,opcode=b.getInt(at),delay=b.getInt(at+4);
            if(opcode!=OPCODES[i]||delay!=(i==6?1:0))
                throw new IOException("Hydro grain ordering/timing differs from retained metadata");
            grains.add(new Grain(224+i,opcode,delay,Arrays.copyOfRange(program,at+8,at+40)));
            if(opcode==1) {
                var tone=new ToneReference(224+i,b.getInt(at+24),program[at+10],program[at+11]&255,
                        Short.toUnsignedInt(b.getShort(at+22)),Short.toUnsignedInt(b.getShort(at+18)),
                        Short.toUnsignedInt(b.getShort(at+20)));
                if(tone.centerNote()!=-74||tone.fine()!=66||tone.flags()!=1
                        ||tone.sampleOffset()!=(i==2?254752:259008)
                        ||tone.adsr1()!=(i==2?0xacff:0xa1ff)||tone.adsr2()!=(i==2?0x9fcd:0x9fc5)
                        ||(program[at+8]&255)!=100||(program[at+9]&255)!=127
                        ||b.getInt(at+12)!=0||b.getShort(at+16)!=(i==2?3:0))
                    throw new IOException("Hydro tone metadata differs from retained reference");
                for(int p=at+28;p<at+40;p++)if(program[p]!=0)
                    throw new IOException("Unsupported Hydro tone operand");
                tones.add(tone);
            } else {
                // Retained control operands are two signed shorts followed by zeros.
                int first=i==0?-126:i==5?2:0,second=i==1?-63:0;
                if(b.getShort(at+8)!=first||b.getShort(at+10)!=second)
                    throw new IOException("Hydro control operands differ from retained reference");
                for(int p=at+12;p<at+40;p++)if(program[p]!=0)
                    throw new IOException("Unsupported Hydro control operand");
            }
        }
        return new Rac1HydroCueProgram(grains,tones);
    }
    /** Deliberately no conversion to Clip: never silently discard nine grains or invent release. */
    public void requirePlaybackEvidence() throws IOException {
        throw new IOException("Authentic Hydro hardware playback remains unverified: NTSC stop/grain43 "
                +"control flow is verified and a separate host-adaptation backend exists; "
                +"PAL release and bit-exact SPU2 output remain unverified. Neither tone alone is a program");
    }
}
