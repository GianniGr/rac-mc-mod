package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import org.junit.jupiter.api.Test;

class Rac1ClankAvTest {
    private static final UUID ID=UUID.randomUUID();
    private static Rac1ClankSession.Snapshot s(Rac1ClankEquipment.Module pack,Rac1ClankCapability.Action action,int phase) {
        return new Rac1ClankSession.Snapshot(ID,1,0,20,new Rac1ClankEquipment(true,true,
                pack==Rac1ClankEquipment.Module.THRUSTER,true,pack),action,1,0,phase);
    }
    private static Rac1HydroWire.Snapshot hydro(Rac1HydroWater.Mode water,Rac1HydroMotion.Stage stage,long epoch) {
        var m=new Rac1HydroMotion();
        if(stage==Rac1HydroMotion.Stage.THRUST) {
            var step=m.step(new Rac1HydroMotion.Input(true,false,false,0,0,0),Rac1ClankMotion.Vector.ZERO,false,false);
            m.resolved(step.displacement());
        }
        return new Rac1HydroWire.Snapshot(ID,2,epoch,20,0,true,true,
                stage==Rac1HydroMotion.Stage.THRUST,Rac1ClankEquipment.Module.HELI,water,
                Rac1HydroVisual.Phase.WET,0,Rac1ClankMotion.Vector.ZERO,m.state(),
                water==Rac1HydroWater.Mode.SUBMERGED?Rac1HydroBoost.ARMED_UP:Rac1HydroBoost.BLOCKED);
    }
    @Test void sourceLoopsAreHeroLocalNotPackClassesAndNotAllJumpMotion() {
        assertEquals(2,Rac1ClankAv.Loop.HELI.local());assertEquals(18,Rac1ClankAv.Loop.THRUSTER.local());
        assertEquals(19,Rac1ClankAv.Loop.HYDRO.local());
        for(var loop:Rac1ClankAv.Loop.values())assertEquals(0,loop.ownerClass());
        var glide=s(Rac1ClankEquipment.Module.HELI,Rac1ClankCapability.Action.GLIDE,40);
        assertEquals(Rac1ClankAv.Loop.HELI,Rac1ClankAv.select(glide,null,true,-1).loop());
        assertNull(Rac1ClankAv.select(s(Rac1ClankEquipment.Module.THRUSTER,
                Rac1ClankCapability.Action.HIGH_JUMP,10),null,true,1).loop());
        assertEquals(Rac1ClankAv.Selection.NONE,Rac1ClankAv.select(glide,null,false,-1));
    }
    @Test void hydroRequiresAcceptedSubmergedThrustNotSurfaceSwapIdleOrMismatchedEpoch() {
        var idle=s(Rac1ClankEquipment.Module.HELI,Rac1ClankCapability.Action.IDLE,0);
        assertTrue(Rac1ClankAv.select(idle,hydro(Rac1HydroWater.Mode.SUBMERGED,Rac1HydroMotion.Stage.THRUST,1),true,0).hydroJets());
        assertFalse(Rac1ClankAv.select(idle,hydro(Rac1HydroWater.Mode.SURFACE,Rac1HydroMotion.Stage.NATIVE,1),true,0).hydroJets());
        assertFalse(Rac1ClankAv.select(idle,hydro(Rac1HydroWater.Mode.WADING,Rac1HydroMotion.Stage.NATIVE,1),true,0).hydroJets());
        assertFalse(Rac1ClankAv.select(idle,hydro(Rac1HydroWater.Mode.DRY,Rac1HydroMotion.Stage.NATIVE,1),true,0).hydroJets());
        assertFalse(Rac1ClankAv.select(idle,hydro(Rac1HydroWater.Mode.SUBMERGED,Rac1HydroMotion.Stage.NATIVE,1),true,0).hydroJets());
        assertFalse(Rac1ClankAv.select(idle,hydro(Rac1HydroWater.Mode.SUBMERGED,Rac1HydroMotion.Stage.THRUST,0),true,0).hydroJets());
        assertFalse(Rac1ClankAv.select(idle,hydro(Rac1HydroWater.Mode.SUBMERGED,Rac1HydroMotion.Stage.THRUST,1),false,0).hydroJets());
        assertEquals(2,Rac1ClankAv.presentationEpoch(idle,
                hydro(Rac1HydroWater.Mode.SUBMERGED,Rac1HydroMotion.Stage.THRUST,1)));
        assertEquals(2,Rac1ClankAv.presentationEpoch(idle,
                hydro(Rac1HydroWater.Mode.SURFACE,Rac1HydroMotion.Stage.NATIVE,1)),
                "Action/surface transition must not reset the retained nozzle cadence timer");
    }
    @Test void exactSupportedAerialWindowsExcludeDescendingHighLong44AndIdle() {
        var pack=Rac1ClankEquipment.Module.THRUSTER;
        assertTrue(Rac1ClankAv.select(s(pack,Rac1ClankCapability.Action.LONG_JUMP,43),null,true,-2).flames());
        assertFalse(Rac1ClankAv.select(s(pack,Rac1ClankCapability.Action.LONG_JUMP,44),null,true,2).flames());
        assertTrue(Rac1ClankAv.select(s(pack,Rac1ClankCapability.Action.HIGH_JUMP,10),null,true,0).flames());
        assertFalse(Rac1ClankAv.select(s(pack,Rac1ClankCapability.Action.HIGH_JUMP,10),null,true,-.001).flames());
        assertTrue(Rac1ClankAv.select(s(pack,Rac1ClankCapability.Action.GLIDE,60),null,true,-1).flames());
        assertFalse(Rac1ClankAv.select(s(pack,Rac1ClankCapability.Action.IDLE,0),null,true,0).flames());
    }
    @Test void actorLoopRetriesUnavailableStartsOnceFollowsAndStopsOnReplacementRemovalOrEpoch() {
        int[] calls=new int[3];boolean[] available={false};
        var life=new Rac1ClankLoopLifecycle(new Rac1ClankLoopLifecycle.Backend() {
            public Object start(UUID actor,Rac1ClankAv.Loop cue,Point p){calls[0]++;return available[0]?new Object():null;}
            public void position(Object h,Point p){calls[1]++;}
            public void stop(Object h){calls[2]++;}
        });
        var p=new Point(1,2,3);
        life.update(ID,1,Rac1ClankAv.Loop.HYDRO,p);assertEquals(0,life.size());
        available[0]=true;life.update(ID,1,Rac1ClankAv.Loop.HYDRO,p);
        life.update(ID,1,Rac1ClankAv.Loop.HYDRO,new Point(4,5,6));
        assertArrayEquals(new int[]{2,1,0},calls);
        life.update(ID,1,Rac1ClankAv.Loop.HELI,p);
        life.update(ID,2,Rac1ClankAv.Loop.HELI,p);assertEquals(2,calls[2]);
        life.retain(Set.of());assertEquals(3,calls[2]);assertEquals(0,life.size());
        life.update(ID,2,Rac1ClankAv.Loop.THRUSTER,p);life.clear();assertEquals(4,calls[2]);
    }
}
