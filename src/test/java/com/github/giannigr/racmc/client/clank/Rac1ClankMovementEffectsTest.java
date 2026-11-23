package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.asset.effect.EffectAssetRef;
import com.github.giannigr.racmc.effect.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class Rac1ClankMovementEffectsTest {
    private static final UUID ID=new UUID(1,2);
    private static final Point ZERO=new Point(0,0,0);
    private static Rac1ClankSession.Snapshot state(Module pack,Action action,int phase,long epoch,long serial) {
        return new Rac1ClankSession.Snapshot(ID,epoch,0,20,
                new Rac1ClankEquipment(true,true,pack==Module.THRUSTER,true,pack),action,serial,0,
                action==Action.IDLE?0:phase);
    }
    @Test void thrusterHistoryUsesDistinctHighLongGhostsAndSourceLagMinusOne() {
        var high=new Rac1ClankTrail();var longJump=new Rac1ClankTrail();
        List<Rac1ClankTrail.Ghost> h=List.of(),l=List.of();
        for(int i=0;i<8;i++) {
            h=high.sample(state(Module.THRUSTER,Action.HIGH_JUMP,i,1,1),i,i,new Matrix4f().translation(i,0,0),true);
            l=longJump.sample(state(Module.THRUSTER,Action.LONG_JUMP,i,1,1),i,i,new Matrix4f().translation(i,0,0),true);
        }
        assertEquals(List.of(40,20),h.stream().map(Rac1ClankTrail.Ghost::alpha).toList());
        assertEquals(List.of(5f,3f),h.stream().map(g->g.root().m30()).toList());
        assertEquals(List.of(40,20,10),l.stream().map(Rac1ClankTrail.Ghost::alpha).toList());
        assertEquals(List.of(6f,4f,2f),l.stream().map(g->g.root().m30()).toList());
        var matrix=l.getFirst().root();matrix.m30(999);
        assertEquals(6,l.getFirst().root().m30());
    }
    @Test void longEntryFirstRenderDoesNotSeedGhostsFromIdleOrEarlierActionHistory() {
        var trail=new Rac1ClankTrail();
        var idle=state(Module.THRUSTER,Action.IDLE,0,1,0);
        trail.sample(idle,99,0,new Matrix4f().translation(-1,0,0),true);
        var entry=state(Module.THRUSTER,Action.LONG_JUMP,0,1,1);
        for(int render=0;render<8;render++)assertTrue(trail.sample(entry,100,0,
                new Matrix4f().translation(render*.001f,0,0),true).isEmpty(),
                "Repeated renders in the entry source step must not manufacture placements");
        var firstLag=trail.sample(entry,101,1,new Matrix4f().translation(.1f,0,0),true);
        assertEquals(1,firstLag.size(),"The next source placement deliberately begins the original body trail");
        assertEquals(40,firstLag.getFirst().alpha());
        var replacement=state(Module.THRUSTER,Action.LONG_JUMP,0,1,2);
        assertTrue(trail.sample(replacement,102,0,new Matrix4f(),true).isEmpty());
    }
    @Test void ghostsFadeTwoPerSourceUpdateAfterTwelveAndNeverResurrectOnCorrection() {
        var trail=new Rac1ClankTrail();List<Rac1ClankTrail.Ghost> ghosts=List.of();
        for(int i=0;i<=13;i++)ghosts=trail.sample(state(Module.THRUSTER,Action.LONG_JUMP,i,1,1),
                i,i,new Matrix4f().translation(i,0,0),true);
        assertEquals(List.of(38,18,8),ghosts.stream().map(Rac1ClankTrail.Ghost::alpha).toList());
        for(int i=14;i<=32;i++)ghosts=trail.sample(state(Module.THRUSTER,Action.LONG_JUMP,i,1,1),
                i,i,new Matrix4f().translation(i,0,0),true);
        assertTrue(ghosts.isEmpty());
        assertTrue(trail.sample(state(Module.THRUSTER,Action.LONG_JUMP,8,1,1),32,8,
                new Matrix4f().translation(32,0,0),true).isEmpty());
    }
    @Test void historyClearsOnSwitchSerialEpochUntrackingStallOrTeleport() {
        for(int reset=0;reset<5;reset++) {
            var t=new Rac1ClankTrail();
            for(int i=0;i<8;i++)t.sample(state(Module.THRUSTER,Action.LONG_JUMP,8,1,1),
                    i,8,new Matrix4f(),true);
            var view=state(Module.THRUSTER,Action.LONG_JUMP,8,reset==0?2:1,reset==1?2:1);
            var result=t.sample(view,reset==2?20:8,8,new Matrix4f().translation(reset==3?20:0,0,0),reset!=4);
            assertTrue(result.isEmpty(),"History bridged reset"+reset);
        }
        var t=new Rac1ClankTrail();
        for(var pack:new Module[]{Module.HELI,Module.THRUSTER})
            assertTrue(t.sample(state(pack,Action.GLIDE,10,1,1),1,10,new Matrix4f(),true).isEmpty());
    }
    @Test void sourceSixtyHistoryHasSameLagAtTwentySixtyAndHighRenderRates() {
        for(int fps:new int[]{20,60,144}) {
            var trail=new Rac1ClankTrail();List<Rac1ClankTrail.Ghost> ghosts=List.of();
            for(int frame=0;frame<=fps;frame++) {
                double source=frame*60.0/fps;
                ghosts=trail.sample(state(Module.THRUSTER,Action.LONG_JUMP,8,1,1),(long)source,8,
                        new Matrix4f().translation((float)(source*.01),0,0),true);
            }
            assertEquals(3,ghosts.size());
            assertEquals(.59,ghosts.getFirst().root().m30(),.003);
        }
    }
    @Test void touchdownRequiresAcceptedDescentTakeoffAndFreshContiguousRealGroundTransition() {
        var latch=new Rac1ClankTouchdown();var jump=state(Module.THRUSTER,Action.HIGH_JUMP,20,1,1);
        var idle=state(Module.THRUSTER,Action.IDLE,0,1,1);
        assertEquals(0,latch.observe(1,jump,true,true,0,0,ZERO));
        assertEquals(0,latch.observe(2,jump,true,false,-.1,0,new Point(0,1,0)));
        assertEquals(1,latch.observe(3,idle,true,true,0,0,ZERO));
        assertEquals(0,latch.observe(4,idle,true,true,0,0,ZERO));
        for(int invalid=0;invalid<6;invalid++) {
            latch.clear();
            var before=state(Module.THRUSTER,Action.LONG_JUMP,invalid==0?1:20,1,1);
            latch.observe(1,before,true,false,invalid==1?.1:-.1,0,new Point(0,1,0));
            var after=state(Module.THRUSTER,Action.IDLE,0,invalid==2?2:1,invalid==3?2:1);
            assertEquals(0,latch.observe(invalid==4?4:2,after,invalid!=5,true,0,0,ZERO));
        }
        latch.clear();latch.observe(1,jump,true,false,-1,0,ZERO);
        assertEquals(0,latch.observe(2,idle,true,true,0,0,new Point(20,0,0)));
        latch.clear();
        latch.observe(1,state(Module.HELI,Action.LONG_JUMP,30,1,1),true,false,-1,0,ZERO);
        assertEquals(0,latch.observe(2,state(Module.HELI,Action.IDLE,0,1,1),true,true,0,0,ZERO));
    }
    @Test void glideOnlyHasConservativeIdleTouchdownNotInferredRunningRowsOrOrdinaryWalking() {
        var latch=new Rac1ClankTouchdown();var glide=state(Module.HELI,Action.GLIDE,20,1,1);
        var idle=state(Module.HELI,Action.IDLE,0,1,1);
        latch.observe(1,glide,true,false,-1,0,new Point(0,1,0));
        assertEquals(5,latch.observe(2,idle,true,true,0,0,ZERO));
        latch.clear();latch.observe(1,glide,true,false,-1,0,new Point(0,1,0));
        assertEquals(0,latch.observe(2,idle,true,true,0,1,ZERO));
        latch.clear();latch.observe(1,idle,true,false,-1,0,new Point(0,1,0));
        assertEquals(0,latch.observe(2,idle,true,true,0,0,ZERO));
    }
    @Test void motesUseOnlyImportedTypeTwentyEightTwoPerFootForFiveUpdatesAndAgeNormally() {
        var runtime=new EffectRuntime(EffectClock.Profile.NTSC,2048);
        var asset=new EffectAssetRef("a".repeat(40),1,EffectAssetRef.Bank.PARTICLE,28);
        var motes=new Rac1ClankFootMotes(runtime,asset);
        var feet=Map.of(ID,List.of(new Point(-1,0,0),new Point(1,0,0)));
        motes.touchdown(ID,1);int count=0;
        for(int i=0;i<6;i++){motes.step(feet);count+=motes.emitted();if(i<5)assertEquals(4,motes.emitted());}
        assertEquals(20,count);assertEquals(0,motes.emitted());
        assertTrue(runtime.snapshot(ZERO,1).stream().allMatch(d->d.asset().equals(asset)));
        for(int i=0;i<260;i++)motes.step(Map.of());
        assertEquals(0,motes.particles());assertEquals(0,runtime.size());
    }
    @Test void missingSpriteFeetPoolPressureAndCancellationDoNotSubstituteOrReplayBursts() {
        var runtime=new EffectRuntime(EffectClock.Profile.NTSC,2);
        var missing=new Rac1ClankFootMotes(runtime,null);missing.touchdown(ID,1);missing.step(Map.of());
        assertEquals(0,missing.particles());
        var motes=new Rac1ClankFootMotes(runtime,new EffectAssetRef("a".repeat(40),1,EffectAssetRef.Bank.PARTICLE,28));
        motes.touchdown(ID,1);for(int i=0;i<5;i++)motes.step(Map.of());
        motes.step(Map.of(ID,List.of(ZERO,ZERO)));assertEquals(0,motes.emitted());
        motes.touchdown(ID,1);motes.step(Map.of(ID,List.of(ZERO,ZERO)));assertTrue(runtime.size()<=2);
        motes.cancel(ID);motes.step(Map.of(ID,List.of(ZERO,ZERO)));assertEquals(0,motes.emitted());
        motes.clear();assertEquals(0,runtime.size());
    }
    @Test void initialJumpPulseHasNoInventedPostLandOrSkidRefresh() {
        var runtime=new EffectRuntime(EffectClock.Profile.NTSC,2048);
        var motes=new Rac1ClankFootMotes(runtime,new EffectAssetRef("a".repeat(40),1,EffectAssetRef.Bank.PARTICLE,28));
        var feet=Map.of(ID,List.of(ZERO,ZERO));
        motes.touchdown(ID,1,1);motes.step(feet);assertEquals(4,motes.emitted());
        motes.step(feet);assertEquals(0,motes.emitted());
        assertThrows(IllegalArgumentException.class,()->motes.touchdown(ID,1,12));
    }
}
