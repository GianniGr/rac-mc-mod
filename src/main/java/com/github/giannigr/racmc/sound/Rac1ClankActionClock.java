package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import java.util.OptionalInt;

/**
 * Read-only hero audio clock fed once per ACCEPTED collision-resolved source update.
 * ORIGINAL_60 host adaptation of pinned hero/anim + jump retiming, never a motion authority.
 */
public final class Rac1ClankActionClock {
    public record Accepted(long epoch,long serial,Module module,Rac1ClankMotion.Step step,
                           double resolvedY,double gravity,boolean landed) {
        public Accepted {
            if(epoch<0||serial<0||module==null||step==null||!Double.isFinite(resolvedY)
                    ||Math.abs(resolvedY)>1||!Double.isFinite(gravity)||gravity<=0||gravity>200)
                throw new IllegalArgumentException("Invalid accepted source audio observation");
        }
    }
    private final Rac1ClankReachedAudio dispatch=new Rac1ClankReachedAudio();
    private long epoch=-1,serial=-1;
    private Module module=Module.NONE;
    private Action action=Action.IDLE;
    private int phase=-1,sequence,key,blend;
    private float t,rate,speed=1;
    private boolean stopped=true;
    public void abort(){stopped=true;dispatch.interrupt();}
    public boolean stopped(){return stopped;}
    public int sequence(){return sequence;}
    public int keyTime16(){return key*16+(int)(t*16);}
    public float speed(){return speed;}
    public OptionalInt accepted(Accepted a) {
        var step=a.step();
        if(a.epoch()<epoch||a.epoch()==epoch&&a.serial()<serial)return OptionalInt.empty();
        if(!step.controlled()){abort();return OptionalInt.empty();}
        if(step.started()) {
            if(a.epoch()==epoch&&a.serial()==serial)return OptionalInt.empty();
            abort();
            if(step.sourceTime()!=0||a.module()==Module.NONE
                    ||step.action()!=Action.LONG_JUMP&&step.action()!=Action.HIGH_JUMP)
                return OptionalInt.empty();
            epoch=a.epoch();serial=a.serial();module=a.module();action=step.action();phase=0;
            sequence=module==Module.HELI?(action==Action.LONG_JUMP?0x12:0x15)
                    :(action==Action.LONG_JUMP?0x26:0x11);
            // Native admission is ground-only after >6 crouch updates: original 8-blend route absent.
            key=0;t=0;blend=5;rate=1f/5;speed=1;stopped=false;
            dispatch.enter(epoch,serial,0,sequence);
            if(a.landed())abort();
            return OptionalInt.empty(); // SetAnim follows this update's animation advance.
        }
        if(stopped)return OptionalInt.empty();
        if(a.epoch()!=epoch||a.serial()!=serial||a.module()!=module||step.action()!=action
                ||step.sourceTime()<phase){abort();return OptionalInt.empty();}
        if(step.sourceTime()==phase)return OptionalInt.empty();
        if(step.sourceTime()!=phase+1){abort();return OptionalInt.empty();}
        phase=step.sourceTime();
        int before=keyTime16();boolean blending=blend>0,wrapped=false;
        if(blending) {
            t+=rate;
            if(--blend==0){t=0;rate=keyRate(key);}
        } else {
            t+=speed*rate;
            if(t>.99f&&t<1.01f)t=1;
            if(t>-.01f&&t<.01f)t=0;
            for(int guard=0;t>=1&&guard<256;guard++) {
                t=(t-1)/rate;key++;
                if(key>=count()){key=0;wrapped=true;}
                rate=keyRate(key);t*=rate;
            }
            if(t>=1||!Float.isFinite(t)){abort();return OptionalInt.empty();}
        }
        var result=dispatch.advance(new Rac1ClankReachedAudio.Advance(epoch,serial,phase,sequence,
                before,keyTime16(),blending,wrapped));
        if(wrapped){abort();return OptionalInt.empty();}
        // First high cues remain on independently reviewed 6a3a5c8 path, not replayed here.
        if(action==Action.HIGH_JUMP&&result.isPresent()
                &&(result.getAsInt()==49||result.getAsInt()==1))result=OptionalInt.empty();
        if(a.landed()){abort();return result;}
        int takeoff=action==Action.LONG_JUMP?5:9;
        boolean curve=action==Action.HIGH_JUMP;
        int from=module==Module.HELI?35:9,to=module==Module.HELI?71:44;
        // Source curve transitions run before descent retiming during the scripted window.
        if(curve&&phase==takeoff+1) {
            if(from>phase)retime(module==Module.HELI?29:0,from-phase);
        } else if(curve&&phase>=from&&phase<to) {
            retime(module==Module.HELI?50:26,module==Module.HELI?51:36);
        } else if(phase==takeoff+1) {
            int n=(int)(a.resolvedY()/(a.gravity()/3600));
            if(n>0){retime(18,n);speed=Math.min(1.5f,speed);}
        } else if(phase>takeoff&&a.resolvedY()<.001&&(!curve||phase>to)) {
            // Original projected LandEta line / touchdown SetAnim / hold are not native mechanics.
            // End this certified segment instead of using a vertical-only guessed landing clock.
            abort();
        }
        return result;
    }
    private void retime(float target,int ticks) {
        float current=header(key)/16f;
        if(blend==0)current+=(header((key+1)%count())-header(key))*t/16f;
        float r=blend>0?keyRate(0):rate;
        float distance=current<target?target-current:target+((blend>0?2:.5f/r)*count()-current);
        float next=.5f*distance/(ticks*r);
        if(Float.isFinite(next)&&next>=0&&next<=16)speed=next;
        else abort(); // A rate outside the certified bounded segment is not synthesized.
    }
    private int count(){return switch(sequence){case 0x12->16;case 0x15->38;case 0x26->14;default->25;};}
    private float keyRate(int k) {
        return switch(sequence) {
            case 0x12 -> k>=9&&k<=12?.5f:.25f;
            case 0x15 -> k>=28&&k<=31?.5f:.25f;
            case 0x26 -> k>=9&&k<=11?.5f:.25f;
            default -> k<=5||k>=18&&k<=21?.5f:.25f;
        };
    }
    private int header(int k) {
        return switch(sequence) {
            case 0x12 -> k<=9?160+k*32:k<=13?448+(k-9)*16:512+(k-13)*32;
            case 0x15 -> k<=28?128+k*32:k<=32?1024+(k-28)*16:1088+(k-32)*32;
            case 0x26 -> k<=9?160+k*32:k<=12?448+(k-9)*16:496+(k-12)*32;
            default -> k<=6?112+k*16:k<=18?208+(k-6)*32:k<=22?592+(k-18)*16:656+(k-22)*32;
        };
    }
}
