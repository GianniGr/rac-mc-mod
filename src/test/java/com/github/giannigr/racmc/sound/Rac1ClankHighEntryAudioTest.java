package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import java.util.*;
import org.junit.jupiter.api.Test;

class Rac1ClankHighEntryAudioTest {
    private static final UUID ID=new UUID(2,36);
    private static Rac1ClankSession.Snapshot state(Module module,Action action,int phase,long serial) {
        return new Rac1ClankSession.Snapshot(ID,1,0,20,new Rac1ClankEquipment(true,true,true,true,module),
                action,serial,0,phase);
    }
    @Test void originalFiveStepBlendSuppressesThenActualFirstKeyRateCrossesEight() {
        for(var module:new Module[]{Module.HELI,Module.THRUSTER}) {
            var c=new Rac1ClankHighEntryAudio();int fired=0,at=-1;
            for(int step=0;step<10;step++) {
                var cue=c.observe(state(module,Action.HIGH_JUMP,step,1),true);
                if(cue.isPresent()){fired++;at=step;assertEquals(module==Module.HELI?49:1,cue.get().local);}
            }
            assertEquals(1,fired);assertEquals(module==Module.HELI?8:7,at);
        }
    }
    @Test void nativeThreeSourceStepSnapshotsKeepSameCrossingNoPerRenderReplays() {
        var clock=new Rac1ClankHighEntryAudio();
        assertTrue(clock.observe(state(Module.HELI,Action.HIGH_JUMP,3,1),true).isEmpty());
        assertTrue(clock.observe(state(Module.HELI,Action.HIGH_JUMP,6,1),true).isEmpty());
        assertEquals(Rac1ClankHighEntryAudio.Cue.HELI,clock.observe(state(Module.HELI,Action.HIGH_JUMP,9,1),true).orElseThrow());
        for(int i=0;i<20;i++)assertTrue(clock.observe(state(Module.HELI,Action.HIGH_JUMP,9,1),true).isEmpty());
    }
    @Test void interruptionLateJoinStaleAndOtherActionsNeverSynthesizeMissedEntrySound() {
        var c=new Rac1ClankHighEntryAudio();
        c.observe(state(Module.THRUSTER,Action.HIGH_JUMP,3,1),true);
        assertTrue(c.observe(state(Module.THRUSTER,Action.IDLE,0,1),true).isEmpty());
        assertTrue(c.observe(state(Module.THRUSTER,Action.HIGH_JUMP,7,1),true).isEmpty());
        assertTrue(c.observe(state(Module.THRUSTER,Action.LONG_JUMP,6,2),true).isEmpty());
        assertTrue(c.observe(state(Module.HELI,Action.HIGH_JUMP,8,3),false).isEmpty());
        assertTrue(c.observe(state(Module.HELI,Action.HIGH_JUMP,8,3),true).isEmpty());
        c.clear();assertTrue(c.observe(state(Module.HELI,Action.HIGH_JUMP,9,3),true).isEmpty());
        c.clear();c.observe(state(Module.HELI,Action.HIGH_JUMP,3,4),true);
        assertTrue(c.observe(state(Module.HELI,Action.HIGH_JUMP,30,4),true).isEmpty(),
                "A large observation gap must not replay a missed first key");
    }
    @Test void onlyTheVerifiedSingleToneHighEntriesCanBecomeClips() throws Exception {
        for(var cue:Rac1ClankHighEntryAudio.Cue.values()) {
            boolean heli=cue==Rac1ClankHighEntryAudio.Cue.HELI;
            var definition=java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            definition.putInt(12,1024);definition.putShort(26,(short)cue.local);
            byte[] row=new byte[12];row[0]=(byte)(heli?100:127);row[4]=1;
            byte[] grain=new byte[40];grain[0]=1;grain[9]=(byte)(heli?110:127);
            var tone=new Rac1SoundBank.Tone(cue.bankCue,cue.grain,heli?100:127,heli?110:127,
                    heli?-84:77,heli?124:6,1,row,grain,"a".repeat(64),
                    new PsAdpcm.Decoded(new byte[56],1,-1,28,false));
            var def=new Rac1SoundMap.Definition(cue.bankCue,definition.array());
            var clip=Rac1ClankHighEntryAudio.verified(cue,def,tone,"b".repeat(64));
            assertFalse(clip.repeat());assertTrue(clip.rate()>1000);
            assertThrows(java.io.IOException.class,()->Rac1ClankHighEntryAudio.verified(cue,
                    new Rac1SoundMap.Definition(37,definition.array()),tone,"b".repeat(64)));
        }
    }
    @Test void definitionBendUsesSourceHalfOpenRangeAndSignedQ7FineConversion() {
        var definition=java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        definition.putInt(16,-508);definition.putInt(20,1270);
        var grain=new byte[40];grain[16]=grain[17]=127;
        var clip=new Rac1AudioCatalog.Clip(new byte[56],22050,definition.array(),new byte[12],grain,
                "a".repeat(64),"b".repeat(64),23,-1,28,false);
        var random=new Random(36);
        for(int i=0;i<100;i++) {
            float pitch=Rac1ClankHighEntryAudio.pitch(clip,random);
            assertTrue(Float.isFinite(pitch)&&pitch>.88&&pitch<1.34);
        }
        definition.putInt(16,0);definition.putInt(20,0);
        var noBend=new Rac1AudioCatalog.Clip(new byte[56],22050,definition.array(),new byte[12],grain,
                "a".repeat(64),"b".repeat(64),58,-1,28,false);
        assertEquals(1,Rac1ClankHighEntryAudio.pitch(noBend,new Random(36)));
    }
}
