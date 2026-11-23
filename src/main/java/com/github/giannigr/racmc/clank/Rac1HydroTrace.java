package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.RacMcMod;

/** Explicit JVM opt-in; disabled by default, cap applies across client/server/audio threads. */
public final class Rac1HydroTrace {
    public static final Rac1HydroDiagnostics LOG=new Rac1HydroDiagnostics(
            Boolean.getBoolean("racmc.hydro.diagnostics"),160,line->RacMcMod.LOGGER.info("{}",line));
    private Rac1HydroTrace() {}
    public static void snapshot(String channel,Rac1HydroWire.Snapshot h,String pose) {
        if(!LOG.enabled())return;
        String state=h.epoch()+":"+h.access()+":"+h.wet()+":"+h.water()+":"+h.controlled()+":"+h.motion().stage()+":"+pose;
        LOG.transition(channel,state,()->"tick="+h.tick()+" ack="+h.acknowledged()+" hydroEpoch="+h.epoch()
                +" clankEpoch="+h.clankEpoch()+" access="+h.access()+" wet="+h.wet()+" water="+h.water()
                +" controlled="+h.controlled()+" stage="+h.motion().stage()+" phase="+h.motion().phase()
                +" stroke="+h.motion().stroke()+" speed="+h.motion().speed()+" pitch="+h.motion().pitch()
                +" pose="+pose);
    }
}
