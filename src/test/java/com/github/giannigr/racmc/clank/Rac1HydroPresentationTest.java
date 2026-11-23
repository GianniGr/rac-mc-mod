package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.client.clank.Rac1ClankViews;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Rac1HydroPresentationTest {
    static final UUID ID=new UUID(0,36);
    static Rac1ClankSession.Snapshot clank() {
        return new Rac1ClankSession.Snapshot(ID,1,0,100,
                new Rac1ClankEquipment(true,true,true,true,Rac1ClankEquipment.Module.THRUSTER),
                Rac1ClankCapability.Action.IDLE,0,100,0);
    }
    static Rac1HydroWire.Snapshot hydro(UUID id,long clankEpoch,Rac1HydroWater.Mode water,
                                       boolean controlled,Rac1HydroMotion.Stage stage) {
        return new Rac1HydroWire.Snapshot(id,2,clankEpoch,120,7,true,true,controlled,
                Rac1ClankEquipment.Module.THRUSTER,water,Rac1HydroVisual.Phase.WET,0,
                Rac1ClankMotion.Vector.ZERO,new Rac1HydroMotion.State(stage,3,0,-1,1,0,0,0,0,
                new Rac1ClankMotion.Vector(0,0,1)),
                water==Rac1HydroWater.Mode.SUBMERGED?Rac1HydroBoost.ARMED_UP:Rac1HydroBoost.BLOCKED);
    }
    @Test void actualAerialObserverAgesOutWhileFreshMatchingHydroStillOwnsWetPresentation() {
        var c=clank();var h=hydro(ID,1,Rac1HydroWater.Mode.SUBMERGED,true,Rac1HydroMotion.Stage.THRUST);
        var views=new Rac1ClankViews();views.accept(c,100);
        boolean legacyFresh=views.serverTime(ID,120)-c.serverTick()<=4;
        assertFalse(legacyFresh);assertFalse(Rac1ClankAv.select(c,h,legacyFresh,0).hydroJets());
        boolean hydroFresh=Rac1HydroPresentation.fresh(c,h,120,120);
        assertTrue(hydroFresh);var selected=Rac1ClankAv.select(c,h,legacyFresh||hydroFresh,0);
        assertTrue(selected.hydroJets());assertEquals(Rac1ClankAv.Loop.HYDRO,selected.loop());
        assertFalse(selected.flames());assertFalse(selected.glide());
    }
    @Test void ownFreshnessNeverOverridesEpochActorWetAccessOrPhysicalValidity() {
        var c=clank();var h=hydro(ID,1,Rac1HydroWater.Mode.SUBMERGED,true,Rac1HydroMotion.Stage.THRUST);
        assertFalse(Rac1HydroPresentation.fresh(c,h,120,125));
        assertFalse(Rac1HydroPresentation.fresh(c,h,121,120));
        assertFalse(Rac1HydroPresentation.fresh(c,hydro(ID,3,Rac1HydroWater.Mode.SUBMERGED,true,
                Rac1HydroMotion.Stage.THRUST),120,120));
        assertFalse(Rac1HydroPresentation.fresh(c,hydro(new UUID(0,99),1,Rac1HydroWater.Mode.SUBMERGED,
                true,Rac1HydroMotion.Stage.THRUST),120,120));
        assertFalse(Rac1HydroPresentation.fresh(null,h,120,120));
        assertFalse(Rac1HydroPresentation.fresh(c,null,120,120));
        assertFalse(Rac1ClankAv.select(c,h,false,0).hydroJets(),"Dead/stale physical actor still denied");
    }
    @Test void surfaceSubmergedNativeAndDriftStaySilentWithNoJetOrDryPackSubstitution() {
        for(var h:new Rac1HydroWire.Snapshot[]{
                hydro(ID,1,Rac1HydroWater.Mode.SURFACE,false,Rac1HydroMotion.Stage.NATIVE),
                hydro(ID,1,Rac1HydroWater.Mode.SUBMERGED,false,Rac1HydroMotion.Stage.NATIVE),
                hydro(ID,1,Rac1HydroWater.Mode.SUBMERGED,true,Rac1HydroMotion.Stage.DRIFT)}) {
            assertTrue(Rac1HydroPresentation.fresh(clank(),h,120,120));
            assertEquals(Rac1ClankAv.Selection.NONE,Rac1ClankAv.select(clank(),h,true,0));
        }
    }
}
