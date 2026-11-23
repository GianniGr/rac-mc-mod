package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import java.io.IOException;
import java.util.*;

/** Only the first high-jump trigger, reached before either original windup can retime the hero. */
public final class Rac1ClankHighEntryAudio {
    public enum Cue {
        HELI(49,58,267,.25f),THRUSTER(1,23,206,.5f);
        public final int local,bankCue,grain;
        private final float rate;
        Cue(int local,int bankCue,int grain,float rate){this.local=local;this.bankCue=bankCue;this.grain=grain;this.rate=rate;}
    }
    public record Catalog(Map<Cue,Rac1AudioCatalog.Clip> clips,Map<Cue,String> failures) {
        public Catalog {clips=Map.copyOf(clips);failures=Map.copyOf(failures);}
        public static final Catalog EMPTY=new Catalog(Map.of(),Map.of());
    }
    private record Owner(long epoch,long serial,Module module) {}
    private static final class Clock {
        Owner owner;int step;float t;boolean fired,expired;
        Clock(Owner owner,int phase){this.owner=owner;step=phase;expired=phase>5;}
    }
    private final Map<UUID,Clock> clocks=new HashMap<>();
    public void clear(){clocks.clear();}
    public void retain(Set<UUID> actors){clocks.keySet().retainAll(actors);}
    /** Uses accepted source-step count to advance a header-rate clock, never a key-down timer. */
    public Optional<Cue> observe(Rac1ClankSession.Snapshot state,boolean valid) {
        var id=state.player();var module=state.equipment().selected();
        if(!valid||!state.equipment().worn()||state.action()!=Action.HIGH_JUMP
                ||module!=Module.HELI&&module!=Module.THRUSTER) {
            clocks.remove(id);return Optional.empty();
        }
        // Motion publishes the completed entry physics step as phase1. SetAnim follows that
        // update's animation advance in the source; its first blend advance is the NEXT step.
        int phase=Math.max(0,state.phaseSourceSteps()-1);
        var owner=new Owner(state.epoch(),state.actionSerial(),module);
        var c=clocks.get(id);
        if(c==null||!c.owner.equals(owner)) {
            if(clocks.size()>=16&&!clocks.containsKey(id))return Optional.empty();
            c=new Clock(owner,phase);clocks.put(id,c);
        }
        if(phase<c.step||phase-c.step>4)c.expired=true;
        if(c.expired||c.fired)return Optional.empty();
        var cue=module==Module.HELI?Cue.HELI:Cue.THRUSTER;
        // The original advances at speed1 until takeoff9. Both first triggers occur before that.
        // Five snapshot-blend updates suppress triggers, including the update landing the blend.
        for(;c.step<Math.min(phase,9);) {
            int step=++c.step;
            if(step<=5)continue;
            int before=(int)(16*c.t);c.t+=cue.rate;int after=(int)(16*c.t);
            if(Rac1ClankAuthoredSoundKeys.first(new int[]{(8<<16)|cue.local},before,after,false).isPresent()) {
                c.fired=true;return Optional.of(cue);
            }
        }
        return Optional.empty();
    }
    static Rac1AudioCatalog.Clip verified(Cue cue,Rac1SoundMap.Definition def,Rac1SoundBank.Tone tone,String bank)
            throws IOException {
        byte[] metadata=def.metadata();boolean heli=cue==Cue.HELI;
        if(metadata.length!=32||def.cue()!=cue.bankCue||SoundBytes.view(metadata).getShort(26)!=cue.local
                ||tone.cue()!=cue.bankCue||tone.grain()!=cue.grain||tone.toneFlags()!=1
                ||tone.centerNote()!=(heli?-84:77)||tone.fine()!=(heli?124:6)
                ||tone.cueVolume()!=(heli?100:127)||tone.toneVolume()!=(heli?110:127)
                ||tone.decoded().repeat()||SoundBytes.view(tone.rawGrain()).getInt(4)!=0)
            throw new IOException("High-jump entry differs from scoped verified cue");
        var d=tone.decoded();
        return new Rac1AudioCatalog.Clip(d.pcm(),tone.sampleRate(),metadata,tone.rawCue(),tone.rawGrain(),
                bank,tone.adpcmHash(),tone.cue(),d.loopStart(),d.loopEnd(),false);
    }
    /** Def bend uses original [lo,hi), tone bend is semitones over the signed32768 control range.
     * Per-owner random choice is a local presentation adaptation, not the original global RNG. */
    public static float pitch(Rac1AudioCatalog.Clip clip,Random random) {
        var definition=SoundBytes.view(clip.definition());int lo=definition.getInt(16),hi=definition.getInt(20);
        if(lo<Short.MIN_VALUE||hi>Short.MAX_VALUE||hi<lo)throw new IllegalArgumentException("Unbounded entry bend");
        int bend=hi==lo?lo:lo+random.nextInt(hi-lo);
        byte[] grain=clip.grain();int semitones=grain[bend<0?16:17];
        int fineDelta=bend*128*semitones/(bend<0?32768:32767);
        return (float)StrictMath.pow(2,fineDelta/(128.0*12));
    }
}
