package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Rac1ClankIntegrationTest {
    @Test void damageHoldRequiresAcceptedCurrentTickAndCannotBeRefreshedByReplay() {
        var stream=new Rac1ClankInput.Stream(4);
        var held=new Rac1ClankInput(true,true,false,0,0,0);
        assertTrue(stream.offer(4,1,10,held));
        assertTrue(stream.heldThisTick(10));
        assertTrue(stream.at(11).jumpHeld()); // motion grace is unchanged
        assertFalse(stream.heldThisTick(11));
        assertFalse(stream.offer(4,1,11,held));
        assertFalse(stream.offer(3,2,11,held));
        assertFalse(stream.heldThisTick(11));
        assertTrue(stream.offer(4,2,11,Rac1ClankInput.RELEASED));
        assertFalse(stream.heldThisTick(11));
    }
    @Test void coalescedPressReleaseRetainsOneServerDerivedEdgeWithoutReapplyingIt() {
        var stream=new Rac1ClankInput.Stream(4);
        assertTrue(stream.offer(4,1,10,new Rac1ClankInput(true,true,true,0,0,0)));
        assertTrue(stream.offer(4,2,10,new Rac1ClankInput(false,false,true,0,0,0)));
        assertFalse(stream.at(10).jumpHeld());
        assertTrue(stream.takeJumpEdge(10));
        assertFalse(stream.takeJumpEdge(10));
        assertFalse(stream.offer(4,1,11,new Rac1ClankInput(true,true,true,0,0,0)));
        assertFalse(stream.takeJumpEdge(11));
    }
    @Test void actualDescentCreditHasNoRequestedOrStaleOrUpwardShortcut() {
        for(int flags=0;flags<32;flags++)for(double actual:new double[]{
                0,-.001,-.03124,-.03125,-.108,.1,Double.NaN,Double.NEGATIVE_INFINITY}) {
            boolean tick=(flags&1)!=0,eligible=(flags&2)!=0,proof=(flags&4)!=0;
            boolean controlled=(flags&8)!=0,ground=(flags&16)!=0;
            boolean expected=tick&&eligible&&proof&&controlled&&!ground
                    &&Double.isFinite(actual)&&actual<=-.03125;
            assertEquals(expected,Rac1ClankRuntime.descentCredit(tick,eligible,proof,
                    controlled,ground,actual));
        }
    }
    @Test void semanticMovementWireIsBoundedAndNeverAcceptsNonfiniteOrTrailingInput()throws Exception {
        var sample=new Rac1ClankMovementWire.Input(8,12,new Rac1ClankInput(true,false,true,0,1,25));
        byte[] bytes=Rac1ClankMovementWire.encode(sample);
        assertEquals(41,bytes.length);
        assertEquals(sample,Rac1ClankMovementWire.input(bytes));
        for(int length:new int[]{0,1,40,42,257})
            assertThrows(IOException.class,()->Rac1ClankMovementWire.input(Arrays.copyOf(bytes,length)));
        byte[] flags=bytes.clone();flags[16]=(byte)8;
        assertThrows(IOException.class,()->Rac1ClankMovementWire.input(flags));
        for(int offset:new int[]{17,25,33})for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY}) {
            byte[] bad=bytes.clone();ByteBuffer.wrap(bad).putDouble(offset,invalid);
            assertThrows(IOException.class,()->Rac1ClankMovementWire.input(bad));
        }
    }
    @Test void authoritativeCheckpointRoundTripPreservesCurveBudgetAndNextStep()throws Exception {
        var equipment=new Rac1ClankEquipment(true,true,true,true,Rac1ClankEquipment.Module.THRUSTER);
        var motion=new Rac1ClankMotion(Rac1ClankClock.Profile.ORIGINAL_60);
        var ground=new Rac1ClankMotion.Observation(true,0,false,true,false,2,Rac1ClankMotion.Vector.ZERO);
        motion.input(new Rac1ClankInput(false,false,true,0,0,0));
        for(int i=0;i<9;i++)motion.step(equipment,ground);
        motion.input(new Rac1ClankInput(true,true,true,0,0,0));
        for(int i=0;i<20;i++) {
            var step=motion.step(equipment,i<9?ground:new Rac1ClankMotion.Observation(
                    false,4,false,true,false,2,motion.velocity()));
            motion.resolved(step.displacement(),false);
        }
        var correction=new Rac1ClankMovementWire.Correction(UUID.randomUUID(),8,12,100,
                new Rac1ClankMotion.Vector(1,80,2),motion.state());
        byte[] bytes=Rac1ClankMovementWire.encode(correction);
        assertTrue(bytes.length<=Rac1ClankMovementWire.MAX_BYTES);
        var copy=Rac1ClankMovementWire.correction(bytes);
        assertEquals(correction,copy);
        var restored=new Rac1ClankMotion(Rac1ClankClock.Profile.ORIGINAL_60);
        restored.restore(copy.state());
        var air=new Rac1ClankMotion.Observation(false,4,false,true,false,2,motion.velocity());
        for(int i=0;i<20;i++) {
            var a=motion.step(equipment,air);var b=restored.step(equipment,air);
            assertEquals(a,b);
            motion.resolved(a.displacement(),false);restored.resolved(b.displacement(),false);
        }
        assertTrue(restored.spentAirJump());
    }
    @Test void lifecycleInvalidationDropsBufferedEdgeButDoesNotRefillAirJump() {
        var equipment=new Rac1ClankEquipment(true,true,false,true,Rac1ClankEquipment.Module.HELI);
        var motion=new Rac1ClankMotion(Rac1ClankClock.Profile.ORIGINAL_60);
        var ground=new Rac1ClankMotion.Observation(true,0,false,true,false,2,Rac1ClankMotion.Vector.ZERO);
        motion.input(new Rac1ClankInput(true,true,true,0,0,0));
        for(int i=0;i<6;i++)motion.step(equipment,ground);
        var accepted=motion.step(equipment,ground);motion.resolved(accepted.displacement(),false);
        motion.cancel();motion.discardBufferedInput();
        assertTrue(motion.spentAirJump());
        assertFalse(motion.step(equipment,new Rac1ClankMotion.Observation(false,4,false,true,false,2,
                new Rac1ClankMotion.Vector(0,.05,0))).controlled());
    }
}
