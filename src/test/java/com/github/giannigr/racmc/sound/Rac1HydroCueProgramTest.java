package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.*;
import org.junit.jupiter.api.Test;

/** Numeric metadata only, no bundled bank/sample/audio payload or retail file access. */
class Rac1HydroCueProgramTest {
    private static byte[] definition() {var b=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);b.putShort(26,(short)19);return b.array();}
    private static byte[] cue() {var b=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);b.put(0,(byte)127);b.put(4,(byte)10);b.putInt(8,224*40);return b.array();}
    private static byte[] program() {
        var b=ByteBuffer.allocate(400).order(ByteOrder.LITTLE_ENDIAN);
        int[] op={28,30,1,21,32,29,34,22,43,1};
        for(int i=0;i<10;i++){int at=i*40;b.putInt(at,op[i]);if(i==6)b.putInt(at+4,1);}
        b.putShort(8,(short)-126);b.putShort(40+10,(short)-63);b.putShort(200+8,(short)2);
        for(int i:new int[]{2,9}) {
            int at=i*40;b.put(at+8,(byte)100);b.put(at+9,(byte)127);b.put(at+10,(byte)-74);b.put(at+11,(byte)66);
            b.putShort(at+16,(short)(i==2?3:0));
            b.putShort(at+22,(short)1);b.putInt(at+24,i==2?254752:259008);
            b.putShort(at+18,(short)(i==2?0xacff:0xa1ff));b.putShort(at+20,(short)(i==2?0x9fcd:0x9fc5));
        }
        return b.array();
    }
    @Test void preservesAllTenGrainsTheirAudioTimeDelayAndBothDistinctToneReferences() throws Exception {
        var result=Rac1HydroCueProgram.read(new Rac1SoundMap.Definition(37,definition()),cue(),program());
        assertEquals(10,result.grains().size());assertEquals(1,result.grains().get(6).delay());
        assertEquals(43,result.grains().get(8).opcode());
        assertEquals(226,result.tones().getFirst().grain());assertEquals(233,result.tones().get(1).grain());
        var operand=result.grains().getFirst().operands();operand[0]=0;
        assertEquals((byte)-126,result.grains().getFirst().operands()[0]);
        assertThrows(IOException.class,result::requirePlaybackEvidence);
    }
    @Test void partialProgramsWrongCueAndUnknownControlBranchesFailClosed() {
        var definition=new Rac1SoundMap.Definition(37,definition());
        assertThrows(IOException.class,()->Rac1HydroCueProgram.read(definition,cue(),new byte[40]));
        assertThrows(IOException.class,()->Rac1HydroCueProgram.read(new Rac1SoundMap.Definition(36,definition()),cue(),program()));
        var changed=program();changed[8*40]=1;
        assertThrows(IOException.class,()->Rac1HydroCueProgram.read(definition,cue(),changed));
        var wrongRamp=program();wrongRamp[200+8]=3;
        assertThrows(IOException.class,()->Rac1HydroCueProgram.read(definition,cue(),wrongRamp));
        var wrongDelay=program();wrongDelay[6*40+4]=2;
        assertThrows(IOException.class,()->Rac1HydroCueProgram.read(definition,cue(),wrongDelay));
    }
    @Test void ntscOriginalStopStartsTailAtEarlyAndSustainedTimesWithoutResettingPitchRegister() throws Exception {
        var program=Rac1HydroCueProgram.read(new Rac1SoundMap.Definition(37,definition()),cue(),program());
        for(int stopTick:new int[]{0,1,31,62,63,240}) {
            var clock=new Rac1HydroProgramClock(program,com.github.giannigr.racmc.iso.Rac1DiscRevision.NTSC_U_V1_00);
            var start=clock.start();assertEquals(Rac1HydroProgramClock.Kind.KEY_ON_LOOP,start.get(1).kind());
            for(int tick=0;tick<stopTick;tick++)assertTrue(clock.tick240().stream()
                    .noneMatch(c->c.kind()==Rac1HydroProgramClock.Kind.KEY_ON_TAIL));
            int bend=clock.pitchBend(),reg=clock.register0();
            var release=clock.release();
            assertEquals(java.util.List.of(Rac1HydroProgramClock.Kind.KEY_OFF_LOOP,Rac1HydroProgramClock.Kind.KEY_ON_TAIL),
                    release.stream().map(Rac1HydroProgramClock.Command::kind).toList());
            assertEquals(233,release.get(1).grain());assertEquals(bend,clock.pitchBend());
            assertEquals(reg,clock.register0());assertTrue(clock.release().isEmpty());
            for(int i=0;i<100;i++)assertTrue(clock.tick240().isEmpty());
        }
    }
    @Test void originalIntegerBendRampEndsAtMinusOneAndHardInvalidationDoesNotPlayTail() throws Exception {
        var program=Rac1HydroCueProgram.read(new Rac1SoundMap.Definition(37,definition()),cue(),program());
        var clock=new Rac1HydroProgramClock(program,com.github.giannigr.racmc.iso.Rac1DiscRevision.NTSC_U_V1_00);
        clock.start();assertEquals(-31993,clock.pitchBend());assertEquals(-62,clock.register0());
        for(int tick=0;tick<63;tick++)clock.tick240();
        assertEquals(63,clock.rampIterations());assertEquals(0,clock.register0());
        assertEquals(-1,clock.pitchBend());assertTrue(clock.forwardDone());
        assertEquals(Rac1HydroProgramClock.Kind.HARD_STOP,clock.abort().getFirst().kind());
        assertTrue(clock.release().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new Rac1HydroProgramClock(program,
                com.github.giannigr.racmc.iso.Rac1DiscRevision.PAL_V2_00));
    }
}
