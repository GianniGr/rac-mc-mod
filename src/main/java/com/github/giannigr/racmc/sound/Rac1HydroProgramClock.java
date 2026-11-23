package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import java.util.*;

/**
 * Cue37 control flow from NTSC989snd v2.09 entry22 only. No audio sink/PCM or approximation.
 * 240-Hz delay belongs to the NEXT grain; release dispatch is synchronous, not key-up polling.
 */
public final class Rac1HydroProgramClock {
    public enum Kind { PITCH,KEY_ON_LOOP,KEY_OFF_LOOP,KEY_ON_TAIL,HARD_STOP }
    public record Command(Kind kind,int grain,int pitchBend) {}
    private int bend,register=-63,rampIterations,delay;
    private boolean started,forwardDone,released,aborted;
    public Rac1HydroProgramClock(Rac1HydroCueProgram program,Rac1DiscRevision revision) {
        Objects.requireNonNull(program);
        if(revision!=Rac1DiscRevision.NTSC_U_V1_00)
            throw new IllegalArgumentException("PAL IOP release has not been verified");
    }
    public int pitchBend(){return bend;}
    public int register0(){return register;}
    public int rampIterations(){return rampIterations;}
    public boolean forwardDone(){return forwardDone;}
    public boolean released(){return released;}
    public List<Command> start() {
        if(started||aborted)return List.of();started=true;
        bend=-(126*32768/127); // SET_PB signed conversion, entry22 [0x4f50,0x4fd4).
        var commands=new ArrayList<Command>();
        commands.add(new Command(Kind.PITCH,224,bend));
        commands.add(new Command(Kind.KEY_ON_LOOP,226,bend));
        increment(commands);return List.copyOf(commands);
    }
    private void increment(List<Command> commands) {
        register=Math.min(127,register+1);rampIterations++;
        bend=Math.clamp(bend+2*32767/127,-32768,32767);
        commands.add(new Command(Kind.PITCH,229,bend));
        delay=1; // Pre-dispatch delay of test grain230, not a render tick.
    }
    public List<Command> tick240() {
        if(!started||forwardDone||released||aborted)return List.of();
        if(--delay>0)return List.of();
        if(register>=0) {
            // TEST_REGISTER skips LOOP_END231. Marker232's ordinary dispatch sets cursor=count−1;
            // sequencer increments past233 without starting it; looping voice stays alive.
            forwardDone=true;return List.of();
        }
        var commands=new ArrayList<Command>();increment(commands);return List.copyOf(commands);
    }
    public List<Command> release() {
        if(!started||released||aborted)return List.of();
        released=true;
        // Stop0x15 ->b230->cfe8(type5)->451c: key off existing voices, clear owner voice mask,
        // cursor=marker+1, then595c synchronously executes233. PB/register are NOT reset.
        return List.of(new Command(Kind.KEY_OFF_LOOP,226,bend),new Command(Kind.KEY_ON_TAIL,233,bend));
    }
    public List<Command> abort() {
        if(aborted)return List.of();aborted=true;
        // Native lifecycle invalidation is a deliberate owned hard teardown, not ordinary StopSound.
        return started?List.of(new Command(Kind.HARD_STOP,-1,bend)):List.of();
    }
}
