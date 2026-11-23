package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.clank.Rac1ClankMotion.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Rac1ClankActionClockTest {
    record Fired(int phase,int local) {}
    private static Rac1ClankActionClock.Accepted a(Module module,Action action,int phase,
                                                  double vy,boolean landed) {
        return new Rac1ClankActionClock.Accepted(1,2,module,
                new Step(true,phase==0,action,phase,new Vector(0,vy,0)),vy,
                Rac1ClankMotion.parameters(module,action).gravity(),landed);
    }
    @Test void actualApprovedMotionAndCollisionFeedBothLongEntriesAndRetimedHighPrograms() {
        assertEquals(List.of(43),motion(Module.HELI,true,3).stream().map(Fired::local).toList());
        assertEquals(List.of(1),motion(Module.THRUSTER,true,3).stream().map(Fired::local).toList());
        assertEquals(List.of(46),motion(Module.HELI,false,3).stream().map(Fired::local).toList());
        assertEquals(List.of(62),motion(Module.THRUSTER,false,3).stream().map(Fired::local).toList());
    }
    private static List<Fired> motion(Module module,boolean longer,int batch) {
        var motion=new Rac1ClankMotion(Rac1ClankClock.Profile.ORIGINAL_60);
        var equipment=new Rac1ClankEquipment(true,true,module==Module.THRUSTER,true,module);
        var clock=new Rac1ClankActionClock();var fired=new ArrayList<Fired>();
        motion.input(Rac1ClankInput.digital(false,false,true,false,false,longer,false,0));
        var initial=new Vector(0,0,longer?.03:0);
        for(int i=0;i<9;i++)motion.step(equipment,new Observation(true,0,false,true,false,2,initial));
        double y=0;boolean began=false;
        for(int tick=0;tick<70;tick++) {
            motion.input(Rac1ClankInput.digital(tick==0,tick==0,tick==0,false,false,longer,false,0));
            for(int sub=0;sub<batch;sub++) {
                var observation=new Observation(y<1e-9,Math.min(4096,Math.max(0,y)),false,true,false,2,
                        motion.action()==Action.IDLE?initial:motion.velocity());
                var step=motion.step(equipment,observation);
                if(!step.controlled()){if(began)return fired;continue;}
                began=true;var requested=step.displacement();
                double actualY=Math.max(-y,requested.y());y+=actualY;
                boolean landed=motion.resolved(new Vector(requested.x(),actualY,requested.z()),y<1e-9);
                var state=motion.state();
                var accepted=new Rac1ClankActionClock.Accepted(1,2,module,step,state.velocity().y(),state.gravity(),landed);
                clock.accepted(accepted).ifPresent(local->fired.add(new Fired(step.sourceTime(),local)));
                assertTrue(clock.accepted(accepted).isEmpty(),"Repeated accepted callback is inert");
                if(landed)return fired;
            }
        }
        return fired;
    }
    @Test void threeUpdateBatchPartitionDoesNotChangeSourceTriggerSteps() {
        for(var module:new Module[]{Module.HELI,Module.THRUSTER}) {
            // Hold equivalently for only source step0 in this pure trace; grouping does not drive clock.
            var singles=trace(module,Action.LONG_JUMP,1);
            assertEquals(List.of(new Fired(module==Module.HELI?8:9,module==Module.HELI?43:1)),singles);
            assertEquals(singles,trace(module,Action.LONG_JUMP,3));
            assertEquals(singles,trace(module,Action.LONG_JUMP,7));
        }
    }
    private static List<Fired> trace(Module module,Action action,int batch) {
        var c=new Rac1ClankActionClock();var out=new ArrayList<Fired>();
        for(int start=0;start<30;start+=batch)for(int phase=start;phase<Math.min(30,start+batch);phase++) {
            int p=phase;c.accepted(a(module,action,p,.08,false)).ifPresent(local->out.add(new Fired(p,local)));
        }
        return out;
    }
    @Test void lateJoinGapInterruptionAndTouchdownCannotManufactureLaterLandingOrSkidKeys() {
        var c=new Rac1ClankActionClock();
        assertTrue(c.accepted(a(Module.THRUSTER,Action.LONG_JUMP,7,.08,false)).isEmpty());
        c.accepted(a(Module.THRUSTER,Action.LONG_JUMP,0,.08,false));
        assertTrue(c.accepted(a(Module.THRUSTER,Action.LONG_JUMP,7,.08,false)).isEmpty());
        assertTrue(c.stopped());
        c=new Rac1ClankActionClock();
        for(int p=0;p<=6;p++)c.accepted(a(Module.THRUSTER,Action.LONG_JUMP,p,.08,p==6));
        assertTrue(c.stopped());
        assertTrue(c.accepted(a(Module.THRUSTER,Action.LONG_JUMP,7,.08,false)).isEmpty());
        c=new Rac1ClankActionClock();c.accepted(a(Module.HELI,Action.HIGH_JUMP,0,.08,false));c.abort();
        assertTrue(c.accepted(a(Module.HELI,Action.HIGH_JUMP,1,.08,false)).isEmpty());
    }
}
