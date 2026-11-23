package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.clank.*;
import net.minecraft.client.player.LocalPlayer;

/** Read-only opt-in observation, deliberately separate from the physical-key control adapter. */
final class Rac1HydroClientDiagnostics {
    private Rac1HydroClientDiagnostics() {}
    static void snapshot(LocalPlayer player,Rac1HydroWire.Snapshot state) {
        if(Rac1HydroTrace.LOG.enabled())Rac1HydroTrace.snapshot("client-snapshot",state,
                player.getPose()+"/swimming="+player.isSwimming()+"/vanillaSprint="+player.isSprinting());
    }
    static void input(LocalPlayer player,Rac1HydroBoost.Controls frame,Rac1HydroBoost boost,
                      boolean active,Rac1HydroWater.Sample water) {
        if(!Rac1HydroTrace.LOG.enabled())return;
        String state=active+":"+boost+":"+frame.sprintDown()+":"+frame.forward()+":"+frame.rise()+":"+frame.dive()
                +":"+water.mode()+":"+water.propulsion()+":"+water.lowAir()+":"+player.getPose()+":"+player.isSwimming();
        Rac1HydroTrace.LOG.transition("client-input",state,()->"physicalSprint="+frame.sprintDown()+" boost="+boost
                +" forward="+frame.forward()+" rise="+frame.rise()+" dive="+frame.dive()
                +" activeGameplay="+active+" water="+water.mode()+" compatible="+water.compatible()
                +" propulsion="+water.propulsion()+" lowAir="+water.lowAir()+" air="+player.getAirSupply()
                +" lookPitch="+player.getXRot()+" pose="+player.getPose()
                +" swimming="+player.isSwimming()+" vanillaSprint="+player.isSprinting());
    }
}
