package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;

/** Observational landing latch only. No motion changes or raw-ground/jump-command bursts. */
public final class Rac1ClankTouchdown {
    private record Last(long tick,Rac1ClankSession.Snapshot state,boolean grounded,double vertical,Point position) {}
    private final Map<UUID,Last> history=new HashMap<>();
    public void clear(){history.clear();}
    public void retain(Set<UUID> ids){history.keySet().retainAll(ids);}
    /** 0=no burst, 1=initial jump pulse, 5=stationary glide LAND. Unmapped skid/run cases stay closed. */
    public int observe(long tick,Rac1ClankSession.Snapshot state,boolean valid,boolean grounded,
                           double vertical,double horizontalSquared,Point position) {
        var id=state.player();
        if(!valid||!state.equipment().worn()||!Double.isFinite(vertical)) {history.remove(id);return 0;}
        if(history.size()>=16&&!history.containsKey(id))return 0;
        var old=history.put(id,new Last(tick,state,grounded,vertical,position));
        if(old==null||tick-old.tick()!=1||old.grounded()||!grounded||old.vertical()>=0
                ||old.position().distanceSquared(position)>16||old.state().epoch()!=state.epoch()
                ||old.state().actionSerial()!=state.actionSerial()
                ||old.state().equipment().selected()!=state.equipment().selected())return 0;
        var previous=old.state().action();
        if(state.action()!=Rac1ClankCapability.Action.IDLE&&state.action()!=previous)return 0;
        if(previous==Rac1ClankCapability.Action.GLIDE)
            return horizontalSquared<.0004?5:0; // No unproven run/slip branch.
        if(previous!=Rac1ClankCapability.Action.HIGH_JUMP&&previous!=Rac1ClankCapability.Action.LONG_JUMP)return 0;
        var module=old.state().equipment().selected();
        if(module==Rac1ClankEquipment.Module.NONE||module==Rac1ClankEquipment.Module.HELI
                &&previous==Rac1ClankCapability.Action.LONG_JUMP)return 0; // Original immediately enters skid.
        return old.state().phaseSourceSteps()>Rac1ClankMotion.parameters(module,previous).takeoff60()?1:0;
    }
}
