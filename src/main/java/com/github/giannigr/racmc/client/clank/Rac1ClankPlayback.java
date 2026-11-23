package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import java.util.*;

/** Per-player/component clocks. Never draws or retargets the source hero body. */
public final class Rac1ClankPlayback {
    private record Key(UUID player,Role role) {}
    private static final Map<Key,Channel> CHANNELS=new HashMap<>();
    private static final class Channel {
        Object model;
        int sequence=-1;
        long actionSerial=-1;
        double switched;
        Rac1ClankPoseBlend.Bone[] last,from;
    }
    private Rac1ClankPlayback() {}
    public static float[] pose(UUID player,Role role,Rac1AnimatedAsset model,
            Rac1ClankSession.Snapshot view,double serverTime) {
        var selected=Rac1ClankAnimations.select(view.equipment().selected(),view.action(),
                Rac1ClankClock.Profile.ORIGINAL_60);
        int sequence=role==Role.CLANK?selected.clankSequence():selected.packSequence();
        var clip=Arrays.stream(model.animations()).filter(c->c.name().equals("sequence_"+sequence))
                .findFirst().orElseThrow();
        Channel c=CHANNELS.computeIfAbsent(new Key(player,role),key->new Channel());
        double now=serverTime*3;
        double phase=view.phaseSourceSteps()+Math.max(0,serverTime-view.serverTick())*3;
        if(c.model!=model||c.sequence!=sequence||(sequence!=1&&c.actionSerial!=view.actionSerial())) {
            c.from=c.model==model?c.last:null;
            c.model=model;c.sequence=sequence;c.switched=now-phase;c.actionSerial=view.actionSerial();
        }
        int blend=role==Role.HELI?selected.packBlendSteps():2;
        double elapsed=Math.max(0,now-c.switched);
        float seconds;
        if(elapsed<blend&&c.from!=null)seconds=0;
        else if(view.equipment().selected()==Module.HELI
                &&(view.action()==Action.HIGH_JUMP||view.action()==Action.LONG_JUMP))
            seconds=authoredSeconds(clip,heroFrame(view.action(),phase));
        else {
            // Converted key seconds retain the original interval/override rates, not flat FPS.
            // Thruster long keeps the SAME601/608 idle clip. Rebasing that running clip
            // to phase0 on entry produced a first-frame pack/nozzle jump even with no ghosts.
            // Long-only correction: leave approved high/glide and all Heli clocks untouched.
            boolean continuousLong=view.equipment().selected()==Module.THRUSTER
                    &&view.action()==Action.LONG_JUMP&&sequence==1
                    &&(role==Role.CLANK||role==Role.THRUSTER);
            double time=view.action()==Action.IDLE||continuousLong?serverTime/20:
                    Math.max(0,phase-selected.packBlendSteps())/60;
            double duration=clip.times()[clip.keyCount()-1];
            seconds=(float)(clip.loop()&&duration>0?time%duration:Math.min(time,duration));
        }
        var target=Rac1ClankPoseBlend.sample(model,clip,seconds);
        c.last=Rac1ClankPoseBlend.blend(c.from,target,
                (float)Rac1ClankComponentClock.blendWeight((int)elapsed,blend));
        // A singular imported joint cannot create NaNs in a snapshot decomposition.
        return c.last==null?Rac1AnimationEvaluator.evaluate(model.skeleton(),clip,seconds,1)
                :Rac1ClankPoseBlend.skin(model,c.last);
    }
    /** Authored frames and source state time are separate. Original high retime deadlines35/86. */
    public static double heroFrame(Action action,double phase) {
        if(action==Action.HIGH_JUMP) {
            if(phase<=35)return segment(8,29,phase,35);
            if(phase<=86)return segment(29,50,phase-35,51);
            return segment(50,64,phase-86,44);
        }
        // Heli long authored10 -> apex18 at its ballistic apex, then hold29.
        double apex=5+Math.sqrt(2*1.95/11)*60;
        return phase<=apex?segment(10,18,phase,apex):segment(18,29,phase-apex,apex-5);
    }
    private static double segment(double from,double to,double time,double length) {
        return from+(to-from)*Math.clamp(time/length,0,1);
    }
    public static float authoredSeconds(Rac1Animation clip,double frame) {
        var timing=clip.sourceTiming();
        int[] mapping=timing.sampledSourceKeys(),headers=timing.headerTimes16();
        float[] times=clip.times();
        for(int i=0;i<mapping.length-1;i++) {
            if(mapping[i]<0||mapping[i+1]<0)break;
            double a=headers[mapping[i]]/16.0,b=headers[mapping[i+1]]/16.0;
            if(frame<=a)return times[i];
            if(frame<=b&&b>a)return (float)(times[i]+(times[i+1]-times[i])*(frame-a)/(b-a));
        }
        return times[times.length-1];
    }
    public static void forget(UUID player){CHANNELS.keySet().removeIf(k->k.player().equals(player));}
    public static void clear(){CHANNELS.clear();}
    public static float[] chain(UUID player,Role role,Rac1AnimatedAsset model) {
        var channel=CHANNELS.get(new Key(player,role));
        return channel==null||channel.model!=model?null:Rac1ClankPoseBlend.chain(model,channel.last);
    }
}
