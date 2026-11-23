package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;

/** Actor-following loop requests. An unsupported/unavailable binding never becomes a fallback sound. */
public final class Rac1ClankLoopLifecycle {
    public interface Backend {
        Object start(UUID actor,Rac1ClankAv.Loop cue,Point position);
        void position(Object handle,Point position);
        void stop(Object handle);
        default boolean alive(Object handle){return true;}
    }
    private record Active(long epoch,long actionSerial,Rac1ClankAv.Loop cue,Object handle) {}
    private final Map<UUID,Active> active=new HashMap<>();
    private final Backend backend;
    private final int limit;
    public Rac1ClankLoopLifecycle(Backend backend){this(backend,16);}
    public Rac1ClankLoopLifecycle(Backend backend,int limit){
        this.backend=Objects.requireNonNull(backend);
        if(limit<1||limit>16)throw new IllegalArgumentException("Invalid loop budget");
        this.limit=limit;
    }
    public void update(UUID actor,long epoch,Rac1ClankAv.Loop cue,Point position) {
        update(actor,epoch,0,cue,position);
    }
    public void update(UUID actor,long epoch,long actionSerial,Rac1ClankAv.Loop cue,Point position) {
        var old=active.get(actor);
        if(old!=null&&(old.epoch()!=epoch||old.actionSerial()!=actionSerial
                ||old.cue()!=cue||!backend.alive(old.handle()))) {
            backend.stop(old.handle());active.remove(actor);old=null;
        }
        if(cue==null)return;
        if(old!=null){backend.position(old.handle(),position);return;}
        if(active.size()>=limit)return;
        Object handle=backend.start(actor,cue,position);
        if(handle!=null)active.put(actor,new Active(epoch,actionSerial,cue,handle)); // Retry later if no verified voice.
    }
    public void retain(Set<UUID> actors) {
        active.entrySet().removeIf(e->{if(actors.contains(e.getKey()))return false;backend.stop(e.getValue().handle());return true;});
    }
    public void clear(){active.values().forEach(a->backend.stop(a.handle()));active.clear();}
    public int size(){return active.size();}
}
