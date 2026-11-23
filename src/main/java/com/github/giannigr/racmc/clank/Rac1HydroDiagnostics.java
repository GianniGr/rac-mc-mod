package com.github.giannigr.racmc.clank;

import java.util.*;
import java.util.function.*;

/** Capped opt-in transitions: only the last state per bounded channel, no PCM or payload dumps. */
public final class Rac1HydroDiagnostics {
    private final boolean enabled;
    private final int limit;
    private final Consumer<String> output;
    private final Map<String,String> states=new HashMap<>();
    private int entries;
    public Rac1HydroDiagnostics(boolean enabled,int limit,Consumer<String> output) {
        if(limit<1||limit>256)throw new IllegalArgumentException("Invalid Hydro diagnostic cap");
        this.enabled=enabled;this.limit=limit;this.output=Objects.requireNonNull(output);
    }
    public boolean enabled(){return enabled;}
    public synchronized boolean transition(String channel,String state,Supplier<String> details) {
        if(!enabled||entries>=limit||state.equals(states.get(channel)))return false;
        if(states.size()>=32&&!states.containsKey(channel))return false;
        states.put(channel,state);entries++;
        output.accept("[Hydro diag "+entries+"/"+limit+"] "+channel+" "+details.get()
                +(entries==limit?" [cap reached; further Hydro diagnostics suppressed]":""));
        return true;
    }
    public synchronized int entries(){return entries;}
}
