package com.github.giannigr.racmc.clank;

/** Source-update pack lifecycle independent of motion admission and native body pose. */
public final class Rac1HydroVisual {
    public enum Phase { DRY, PUT_AWAY, GAP, UNFOLD, WET, FOLD, RESTORE_GAP }
    private Phase phase=Phase.DRY;
    private int time;
    public void target(boolean wet) {
        if(wet&&(phase==Phase.DRY||phase==Phase.FOLD||phase==Phase.RESTORE_GAP)){phase=Phase.PUT_AWAY;time=0;}
        else if(!wet&&(phase==Phase.PUT_AWAY||phase==Phase.GAP||phase==Phase.UNFOLD||phase==Phase.WET)){
            phase=Phase.FOLD;time=0;
        }
    }
    public void step() {
        if(phase==Phase.DRY||phase==Phase.WET)return;
        time++;
        int limit=switch(phase){case PUT_AWAY->2;case GAP,RESTORE_GAP->1;case UNFOLD->14;case FOLD->6;default->1;};
        if(time>=limit) {
            phase=switch(phase){case PUT_AWAY->Phase.GAP;case GAP->Phase.UNFOLD;case UNFOLD->Phase.WET;
                case FOLD->Phase.RESTORE_GAP;default->Phase.DRY;};time=0;
        }
    }
    public Phase phase(){return phase;}
    public int time(){return time;}
    public void restore(Phase value,int steps){phase=value;time=steps;}
    public void clear(){phase=Phase.DRY;time=0;}
}
