package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;

/** Rising accepted thrust edges only; invalidation is never ordinary release. Main-thread owned. */
public final class Rac1HydroAudioLifecycle {
    public interface Backend {
        default void decision(UUID actor,String decision,int phase) {}
        Object start(UUID actor,Point at);
        void position(Object voice,Point at);
        void release(Object voice);
        void abort(Object voice);
        boolean finished(Object voice);
    }
    private static final class Owner {
        long epoch;
        boolean thrust;
        int phase;
        long tick=-1;
        Object active;
        final List<Object> tails=new ArrayList<>();
        Owner(long epoch){this.epoch=epoch;}
    }
    private final Backend backend;
    private final Map<UUID,Owner> owners=new HashMap<>();
    public Rac1HydroAudioLifecycle(Backend backend){this.backend=Objects.requireNonNull(backend);}
    public void update(UUID actor,long epoch,long sourceTick,boolean thrust,int phase,boolean ready,Point at) {
        var owner=owners.get(actor);
        if(owner!=null&&owner.epoch!=epoch){
            backend.decision(actor,"epoch-abort",phase);abort(owner);owners.remove(actor);owner=null;
        }
        if(owner==null){owner=new Owner(epoch);owners.put(actor,owner);}
        owner.tails.removeIf(v->{if(!backend.finished(v))return false;backend.abort(v);return true;});
        if(owner.active!=null&&backend.finished(owner.active)) {
            backend.decision(actor,"retire-finished-or-rejected",phase);
            backend.abort(owner.active);owner.active=null; // Do not retry while thrust held.
        }
        if(sourceTick<=owner.tick) {
            if(owner.active!=null)backend.position(owner.active,at);
            owner.tails.forEach(v->backend.position(v,at));return;
        }
        owner.tick=sourceTick;
        boolean restarted=thrust&&owner.thrust&&phase<owner.phase;
        if((!thrust||restarted)&&owner.thrust&&owner.active!=null) {
            backend.decision(actor,"ordinary-release",phase);
            backend.release(owner.active);owner.tails.add(owner.active);owner.active=null;
        }
        if(thrust&&(!owner.thrust||restarted)) {
            backend.decision(actor,!ready?"media-not-ready":phase>3?"late-start-consumed":"admit-thrust",phase);
        }
        if(thrust&&(!owner.thrust||restarted)&&ready&&phase<=3) {
            // At most one retiring run plus one new run per actor, independent release envelopes.
            while(owner.tails.size()>1)backend.abort(owner.tails.removeFirst());
            owner.active=backend.start(actor,at);
        }
        owner.thrust=thrust; // Consume unavailable/budget-denied edges, never late replay.
        owner.phase=phase;
        if(owner.active!=null)backend.position(owner.active,at);
        owner.tails.forEach(v->backend.position(v,at));
    }
    private void abort(Owner owner) {
        if(owner.active!=null)backend.abort(owner.active);
        owner.tails.forEach(backend::abort);
    }
    public void retain(Set<UUID> actors) {
        owners.entrySet().removeIf(e->{if(actors.contains(e.getKey()))return false;
            backend.decision(e.getKey(),"invalid-or-displaced-abort",e.getValue().phase);abort(e.getValue());return true;});
    }
    public void clear(){owners.forEach((id,owner)->{
        backend.decision(id,"world-media-resource-clear",owner.phase);abort(owner);});owners.clear();}
}
