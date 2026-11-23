package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;

/** Read-only semantic requests, independent of VFX readiness, poses, raw keys and landing grace. */
public final class Rac1ClankAudioScene {
    public static final int MAX_VOICES=2;
    public static final double RANGE_SQUARED=32*32;
    public record Actor(Rac1ClankSession.Snapshot clank,Rac1HydroWire.Snapshot hydro,
                        boolean valid,double verticalVelocity,double distanceSquared,Point position) {}
    public record Request(UUID actor,long epoch,long actionSerial,Rac1ClankAv.Loop cue,Point position) {}
    private Rac1ClankAudioScene() {}
    public static List<Request> requests(List<Actor> actors,Rac1ClankAudioCatalog catalog) {
        if(catalog==null)return List.of();
        var sorted=new ArrayList<>(actors);
        sorted.sort(Comparator.comparingDouble(Actor::distanceSquared)
                .thenComparing(a->a.clank()==null?new UUID(0,0):a.clank().player()));
        var result=new ArrayList<Request>();
        var seen=new HashSet<UUID>();
        for(var actor:sorted) {
            if(actor.clank()==null||!actor.valid()||!Double.isFinite(actor.distanceSquared())
                    ||actor.distanceSquared()<0||actor.distanceSquared()>RANGE_SQUARED)continue;
            var selected=Rac1ClankAv.select(actor.clank(),actor.hydro(),true,actor.verticalVelocity());
            if(selected.loop()==null||!catalog.clips().containsKey(selected.loop()))continue;
            var id=actor.clank().player();
            if(!seen.add(id))continue;
            result.add(new Request(id,actor.clank().epoch(),actor.clank().actionSerial(),selected.loop(),actor.position()));
            if(result.size()==MAX_VOICES)break;
        }
        return List.copyOf(result);
    }
}
