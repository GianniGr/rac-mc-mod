package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import org.junit.jupiter.api.Test;

class Rac1HydroAudioLifecycleTest {
    static final Point AT=new Point(0,0,0);
    static final UUID ID=new UUID(0,36);
    static class Backend implements Rac1HydroAudioLifecycle.Backend {
        int starts,releases,aborts;
        final Set<Object> done=new HashSet<>();
        public Object start(UUID id,Point at){starts++;return new Object();}
        public void position(Object v,Point at){}
        public void release(Object v){releases++;}
        public void abort(Object v){aborts++;}
        public boolean finished(Object v){return done.contains(v);}
    }
    @Test void releaseRetriggerKeepsRetiringRunAndBoundsRapidChatter() {
        var b=new Backend();var c=new Rac1HydroAudioLifecycle(b);
        for(int i=0;i<10;i++) {
            c.update(ID,1,i*2,true,3,true,AT);c.update(ID,1,i*2,true,3,true,AT);
            c.update(ID,1,i*2+1,false,1,true,AT);
        }
        assertEquals(10,b.starts);assertEquals(10,b.releases);assertEquals(8,b.aborts);
        c.clear();assertEquals(10,b.aborts);c.clear();assertEquals(10,b.aborts);
    }
    @Test void unavailableOrLateEdgesAreConsumedAndEpochLossHardAbortsWithoutTail() {
        var b=new Backend();var c=new Rac1HydroAudioLifecycle(b);
        c.update(ID,1,0,true,3,false,AT);c.update(ID,1,1,true,6,true,AT);
        assertEquals(0,b.starts);
        c.update(ID,2,2,true,40,true,AT);assertEquals(0,b.starts);
        c.update(ID,2,3,false,0,true,AT);c.update(ID,2,4,true,2,true,AT);
        c.retain(Set.of());assertEquals(1,b.aborts);assertEquals(0,b.releases);
        c.update(ID,3,5,true,1,true,AT);c.update(ID,4,6,false,0,true,AT);
        assertEquals(2,b.aborts);assertEquals(0,b.releases);
    }
    @Test void skippedReleaseThenNewThrustPhaseResetIsRecognizedOnce() {
        var b=new Backend();var c=new Rac1HydroAudioLifecycle(b);
        c.update(ID,1,0,true,3,true,AT);c.update(ID,1,1,true,30,true,AT);
        c.update(ID,1,2,true,2,true,AT);c.update(ID,1,2,true,2,true,AT);
        assertEquals(2,b.starts);assertEquals(1,b.releases);c.clear();assertEquals(2,b.aborts);
    }
    @Test void rejectedChannelAndRepeatedOrBackwardSnapshotsNeverRestartHeldThrust() {
        var b=new Backend(){@Override public boolean finished(Object v){return true;}};
        var c=new Rac1HydroAudioLifecycle(b);
        c.update(ID,1,10,true,3,true,AT);
        c.update(ID,1,10,false,0,true,AT); // Same-tick conflicting packet must not release.
        c.update(ID,1,9,true,1,true,AT);
        c.update(ID,1,11,true,6,true,AT);
        assertEquals(1,b.starts);assertEquals(1,b.aborts);assertEquals(0,b.releases);
        c.clear();assertEquals(1,b.aborts);
    }
}
