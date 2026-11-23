package com.github.giannigr.racmc.shop;

/** Unsaved public physical projection; no player/transaction/asset identities. Reference frames are60Hz. */
public final class Rac1ShopMotion {
    public enum Phase { CLOSED, UNFOLD, OPEN, POWER_OFF, FOLD }
    public record Stamp(Phase phase,long startFrame,long changes) {}
    public record Frame(Phase phase,float seconds,float screenPower) {}
    private Phase phase=Phase.CLOSED;
    private long started,last,changes;
    private boolean wanted;
    public Stamp update(boolean active,long frame) {
        if (frame<last || frame>Long.MAX_VALUE-1000) throw new IllegalArgumentException("Invalid physical frame");
        for (int i=0;i<5;i++) {
            int duration=switch (phase) { case UNFOLD,FOLD -> 40; case POWER_OFF -> 8; default -> -1; };
            if (duration<0 || frame<started+duration) break;
            long boundary=started+duration;
            enter(switch (phase) {
                case UNFOLD -> wanted ? Phase.OPEN:Phase.POWER_OFF;
                case POWER_OFF -> Phase.FOLD;
                case FOLD -> wanted ? Phase.UNFOLD:Phase.CLOSED;
                default -> throw new IllegalStateException();
            },boundary);
        }
        wanted=active;
        if (phase==Phase.CLOSED && active) enter(Phase.UNFOLD,frame);
        else if (phase==Phase.OPEN && !active) enter(Phase.POWER_OFF,frame);
        last=frame;
        return new Stamp(phase,started,changes);
    }
    private void enter(Phase next,long frame) { phase=next; started=frame; changes++; }
    /** Predict only inevitable continuations, never another viewer's future open intent. */
    public static Frame display(Phase phase,long started,double frame) {
        if (phase==null || started<0 || !Double.isFinite(frame) || frame<0)
            throw new IllegalArgumentException("Invalid physical display");
        double elapsed=Math.max(0,frame-started);
        if (phase==Phase.UNFOLD && elapsed>=40) { phase=Phase.OPEN; elapsed-=40; }
        if (phase==Phase.POWER_OFF && elapsed>=8) { phase=Phase.FOLD; elapsed-=8; }
        if (phase==Phase.FOLD && elapsed>=40) { phase=Phase.CLOSED; elapsed=0; }
        float power=switch (phase) {
            case OPEN -> (float)Math.min(1,elapsed/8);
            case POWER_OFF -> (float)Math.max(0,1-elapsed/8);
            default -> 0;
        };
        return new Frame(phase,(float)(elapsed/60),power);
    }
}
