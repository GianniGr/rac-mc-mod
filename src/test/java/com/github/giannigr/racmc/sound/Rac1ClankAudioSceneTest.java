package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankAv.Loop;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import org.junit.jupiter.api.Test;

class Rac1ClankAudioSceneTest {
    static Rac1ClankSession.Snapshot state(int id,Rac1ClankEquipment.Module pack,
                                          Rac1ClankCapability.Action action) {
        return new Rac1ClankSession.Snapshot(new UUID(0,id),1,0,20,
                new Rac1ClankEquipment(true,true,pack==Rac1ClankEquipment.Module.THRUSTER,true,pack),
                action,1,0,action==Rac1ClankCapability.Action.IDLE?0:10);
    }
    static Rac1ClankAudioScene.Actor actor(int id,double distance,boolean valid,
                                         Rac1ClankEquipment.Module pack,Rac1ClankCapability.Action action) {
        return new Rac1ClankAudioScene.Actor(state(id,pack,action),null,valid,-1,distance,new Point(id,2,3));
    }
    @Test void nearestTwoDistinctAcceptedActorsOnlyNoPoseOrVfxDependency() {
        var pack=Rac1ClankEquipment.Module.HELI;var glide=Rac1ClankCapability.Action.GLIDE;
        var result=Rac1ClankAudioScene.requests(List.of(actor(1,64,true,pack,glide),
                actor(2,1,true,pack,glide),actor(3,9,true,pack,glide),actor(2,4,true,pack,glide)),
                Rac1ClankAudioCatalogTest.catalog());
        assertEquals(List.of(new UUID(0,2),new UUID(0,3)),result.stream().map(Rac1ClankAudioScene.Request::actor).toList());
        assertEquals(new Point(2,2,3),result.getFirst().position());assertEquals(Loop.HELI,result.getFirst().cue());
    }
    @Test void unavailableCueDoesNotConsumeBudgetOrFallBackToOtherPack() throws Exception {
        var partial=new Rac1ClankAudioCatalog(Map.of(Loop.THRUSTER,Rac1ClankAudioCatalogTest.clip(Loop.THRUSTER)),Map.of());
        var result=Rac1ClankAudioScene.requests(List.of(
                actor(1,1,true,Rac1ClankEquipment.Module.HELI,Rac1ClankCapability.Action.GLIDE),
                actor(2,4,true,Rac1ClankEquipment.Module.THRUSTER,Rac1ClankCapability.Action.GLIDE)),partial);
        assertEquals(1,result.size());assertEquals(new UUID(0,2),result.getFirst().actor());
        assertEquals(Loop.THRUSTER,result.getFirst().cue());
    }
    @Test void invalidStaleDeadUntrackedOutOfRangeIdleAndJumpRequestsAreSilent() {
        var pack=Rac1ClankEquipment.Module.THRUSTER;var glide=Rac1ClankCapability.Action.GLIDE;
        var catalog=Rac1ClankAudioCatalogTest.catalog();
        for(var candidate:List.of(actor(1,1,false,pack,glide),actor(1,1024.01,true,pack,glide),
                actor(1,Double.NaN,true,pack,glide),actor(1,1,true,pack,Rac1ClankCapability.Action.IDLE),
                actor(1,1,true,pack,Rac1ClankCapability.Action.HIGH_JUMP),
                actor(1,1,true,pack,Rac1ClankCapability.Action.LONG_JUMP)))
            assertTrue(Rac1ClankAudioScene.requests(List.of(candidate),catalog).isEmpty());
        assertTrue(Rac1ClankAudioScene.requests(List.of(),catalog).isEmpty());
        assertTrue(Rac1ClankAudioScene.requests(List.of(actor(1,1,true,pack,glide)),null).isEmpty());
    }
    @Test void wetHydroDoesNotBorrowHeliOrThrusterEvenIfDrySelectionGlideIsStale() {
        var clank=state(1,Rac1ClankEquipment.Module.HELI,Rac1ClankCapability.Action.GLIDE);
        var motion=new Rac1HydroMotion();
        var hydro=new Rac1HydroWire.Snapshot(clank.player(),2,1,20,0,true,true,false,
                Rac1ClankEquipment.Module.HELI,Rac1HydroWater.Mode.SUBMERGED,Rac1HydroVisual.Phase.WET,
                0,Rac1ClankMotion.Vector.ZERO,motion.state(),Rac1HydroBoost.ARMED_UP);
        assertTrue(Rac1ClankAudioScene.requests(List.of(new Rac1ClankAudioScene.Actor(clank,hydro,true,
                -1,1,new Point(0,0,0))),Rac1ClankAudioCatalogTest.catalog()).isEmpty());
    }
    @Test void boundedLifecycleSustainsPositionsAndOnlyStopsItsOwnHandles() {
        var started=new ArrayList<Object>();var stopped=new ArrayList<Object>();var positions=new ArrayList<Point>();
        Set<Object> dead=new HashSet<>();boolean[] available={true};
        var lifecycle=new Rac1ClankLoopLifecycle(new Rac1ClankLoopLifecycle.Backend() {
            public Object start(UUID actor,Loop cue,Point position) {
                if(!available[0])return null;
                Object handle=new Object();started.add(handle);return handle;
            }
            public void position(Object handle,Point position){positions.add(position);}
            public void stop(Object handle){assertTrue(started.contains(handle));stopped.add(handle);}
            public boolean alive(Object handle){return !dead.contains(handle);}
        },2);
        var a=new UUID(0,1);var b=new UUID(0,2);var c=new UUID(0,3);var at=new Point(0,0,0);
        lifecycle.update(a,1,Loop.HELI,at);lifecycle.update(b,1,Loop.THRUSTER,at);
        lifecycle.update(c,1,Loop.HELI,at);assertEquals(2,started.size());
        lifecycle.update(a,1,Loop.HELI,new Point(1,2,3));assertEquals(2,started.size());
        assertEquals(List.of(new Point(1,2,3)),positions);
        lifecycle.update(a,1,null,at);assertEquals(1,stopped.size()); // action end, not landing grace
        lifecycle.update(b,1,Loop.HELI,at);assertEquals(2,stopped.size()); // pack switch
        lifecycle.update(b,2,Loop.HELI,at);assertEquals(3,stopped.size()); // equipment epoch
        dead.add(started.getLast());available[0]=false;
        lifecycle.update(b,2,Loop.HELI,at);assertEquals(0,lifecycle.size()); // dropped channel
        available[0]=true;lifecycle.update(b,2,Loop.HELI,at);assertEquals(1,lifecycle.size());
        lifecycle.retain(Set.of());assertEquals(0,lifecycle.size()); // death, untrack, distance
        lifecycle.update(c,2,Loop.THRUSTER,at);lifecycle.clear(); // world, disconnect, reload/media
        assertEquals(started.size(),stopped.size());
        assertThrows(IllegalArgumentException.class,()->new Rac1ClankLoopLifecycle(
                new Rac1ClankLoopLifecycle.Backend() {
                    public Object start(UUID id,Loop cue,Point p){return null;}
                    public void position(Object h,Point p){}public void stop(Object h){}
                },0));
    }
    @Test void actionSerialReentryRestartsIntroButUnrelatedDryHydroEpochDoesNot() {
        var clank=state(1,Rac1ClankEquipment.Module.HELI,Rac1ClankCapability.Action.GLIDE);
        var hydro=new Rac1HydroWire.Snapshot(clank.player(),99,1,20,0,true,false,false,
                Rac1ClankEquipment.Module.HELI,Rac1HydroWater.Mode.DRY,Rac1HydroVisual.Phase.DRY,
                0,Rac1ClankMotion.Vector.ZERO,new Rac1HydroMotion().state(),Rac1HydroBoost.BLOCKED);
        var request=Rac1ClankAudioScene.requests(List.of(new Rac1ClankAudioScene.Actor(clank,hydro,true,
                -1,1,new Point(0,0,0))),Rac1ClankAudioCatalogTest.catalog()).getFirst();
        assertEquals(clank.epoch(),request.epoch());assertNotEquals(hydro.epoch(),request.epoch());
        assertEquals(clank.actionSerial(),request.actionSerial());
        int[] calls=new int[3];
        var life=new Rac1ClankLoopLifecycle(new Rac1ClankLoopLifecycle.Backend() {
            public Object start(UUID id,Loop cue,Point p){calls[0]++;return new Object();}
            public void position(Object h,Point p){calls[1]++;}
            public void stop(Object h){calls[2]++;}
        },2);
        var at=request.position();var id=clank.player();
        life.update(id,1,1,Loop.HELI,at);life.update(id,1,1,Loop.HELI,at);
        assertArrayEquals(new int[]{1,1,0},calls);
        life.update(id,1,2,Loop.HELI,at);
        assertArrayEquals(new int[]{2,1,1},calls);
        life.clear();assertEquals(2,calls[2]);
    }
}
