package com.github.giannigr.racmc.sound;

import java.util.*;

/**
 * Later jump dispatch boundary: consumes actual consecutive source animation advances,
 * never elapsed action/input/pose time. No native producer is fabricated here.
 */
public final class Rac1ClankReachedAudio {
    public record Advance(long epoch,long serial,long step,int sequence,int before16,int after16,
                          boolean blendingBefore,boolean wrapped) {
        public Advance {
            if(epoch<0||serial<0||step<0||before16<0||after16<0)
                throw new IllegalArgumentException("Invalid reached animation advance");
        }
    }
    private long epoch=-1,serial=-1,step=-1;
    private int sequence=-1;
    private boolean interrupted;
    /** Call on a verified SetAnim/entry, not on the first late observed pose. */
    public void enter(long epoch,long serial,long beforeFirstStep,int sequence) {
        if(epoch<0||serial<0||beforeFirstStep<0)throw new IllegalArgumentException("Invalid source owner");
        words(sequence);this.epoch=epoch;this.serial=serial;step=beforeFirstStep;
        this.sequence=sequence;interrupted=false;
    }
    public OptionalInt advance(Advance a) {
        if(interrupted||a.epoch()!=epoch||a.serial()!=serial||a.sequence()!=sequence)return OptionalInt.empty();
        if(a.step()==step)return OptionalInt.empty(); // Duplicate render/sample is not another source advance.
        if(a.step()!=step+1){interrupted=true;return OptionalInt.empty();}
        step=a.step();
        if(a.wrapped())return OptionalInt.empty();
        return Rac1ClankAuthoredSoundKeys.first(words(sequence),a.before16(),a.after16(),a.blendingBefore());
    }
    public void interrupt(){interrupted=true;}
    private static int[] words(int sequence) {
        return switch(sequence) {
            case 0x12 -> new int[]{8<<16|43,8<<16|62,208<<16|31};
            case 0x15 -> new int[]{8<<16|49,80<<16|46,440<<16|30,464<<16|31};
            case 0x26 -> new int[]{8<<16|1};
            case 0x11 -> new int[]{8<<16|1,96<<16|62,320<<16|31,352<<16|30};
            case 5 -> new int[]{32<<16|12};
            default -> throw new IllegalArgumentException("Outside scoped hero sequences");
        };
    }
}
