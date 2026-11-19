package com.github.giannigr.racmc.clank;

/** One airborne fall's release grace. Runtime owns identity, admission and lifecycle checks. */
public final class Rac1ClankLandingGrace {
    public static final int TICKS=40;
    private long releaseTick=-1,actionSerial=-1,inputSerial=-1;
    public void start(long tick,long action,long serial) {
        if(tick<0||action<0||serial<0)throw new IllegalArgumentException("Invalid release proof");
        releaseTick=tick;actionSerial=action;inputSerial=serial;
    }
    /** Half-open window [releaseTick, releaseTick+40); no wall-clock or motion-clock coupling. */
    public boolean covers(long tick) {
        return active()&&tick>=releaseTick&&tick-releaseTick<TICKS;
    }
    public boolean land(long tick) {
        boolean covered=covers(tick);clear();return covered;
    }
    public void clear(){releaseTick=-1;actionSerial=-1;inputSerial=-1;}
    public boolean active(){return releaseTick>=0;}
    public long releaseTick(){return releaseTick;}
    public long actionSerial(){return actionSerial;}
    public long inputSerial(){return inputSerial;}
}
