package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopKioskPose;

/**
 * Uninstalled, shop-local physical presentation clock. Input is an authoritative
 * aggregate (viewers > 0), never proximity or an individual screen's lifetime.
 * NTSC reference-frame timing; no camera, purchases, packet or world mutation.
 */
public final class Rac1ShopKioskTransition {
    public enum Phase { CLOSED, UNFOLD, OPEN, POWER_OFF, FOLD }
    public record Frame(Phase phase, Rac1ShopKioskPose.Stage pose, float poseSeconds,
                        float screenPower) {}
    public static final double REFERENCE_HZ=60;
    public static final int OPEN_FRAMES=40, POWER_OFF_FRAMES=8, LEAVE_FRAMES=40;
    private Phase phase=Phase.CLOSED;
    private boolean wanted;
    private double last, started;

    /**
     * Apply a newly observed aggregate at now, after advancing the prior state.
     * Re-entry during a close queues a complete unfold after the fold; it never
     * snaps a partially folded body open. This overlap policy is an MC adaptation.
     */
    public Frame sample(boolean activeViewers,double now) {
        time(now);
        advance(now);
        wanted=activeViewers;
        if (phase==Phase.CLOSED && wanted) enter(Phase.UNFOLD,now);
        else if (phase==Phase.OPEN && !wanted) enter(Phase.POWER_OFF,now);
        last=now;
        return frame(now);
    }

    /** Resource/vendor loss: discard presentation immediately, NOT a normal exit/fold. */
    public void discard(double now) {
        time(now);
        wanted=false; enter(Phase.CLOSED,now); last=now;
    }

    private void advance(double now) {
        // No loop per tick/frame: at most one pending close and one queued reopen.
        for (int i=0;i<5;i++) {
            double duration=switch (phase) {
                case UNFOLD -> OPEN_FRAMES/REFERENCE_HZ;
                case POWER_OFF -> POWER_OFF_FRAMES/REFERENCE_HZ;
                case FOLD -> LEAVE_FRAMES/REFERENCE_HZ;
                default -> Double.POSITIVE_INFINITY;
            };
            if (now<started+duration) return;
            double boundary=started+duration;
            switch (phase) {
                case UNFOLD -> enter(wanted ? Phase.OPEN:Phase.POWER_OFF,boundary);
                case POWER_OFF -> enter(Phase.FOLD,boundary);
                case FOLD -> enter(wanted ? Phase.UNFOLD:Phase.CLOSED,boundary);
                default -> { return; }
            }
        }
    }

    private Frame frame(double now) {
        float elapsed=(float)(now-started);
        return switch (phase) {
            case CLOSED -> new Frame(phase,Rac1ShopKioskPose.Stage.CLOSED,0,0);
            case UNFOLD -> new Frame(phase,Rac1ShopKioskPose.Stage.UNFOLD,elapsed,0);
            case OPEN -> new Frame(phase,Rac1ShopKioskPose.Stage.OPEN,0,1);
            case POWER_OFF -> new Frame(phase,Rac1ShopKioskPose.Stage.OPEN,0,
                    (float)Math.max(0,1-(now-started)*REFERENCE_HZ/POWER_OFF_FRAMES));
            case FOLD -> new Frame(phase,Rac1ShopKioskPose.Stage.REVERSE_FOLD,elapsed,0);
        };
    }
    private void enter(Phase next,double now) { phase=next; started=now; }
    private void time(double now) {
        if (!Double.isFinite(now) || now<last)
            throw new IllegalArgumentException("Kiosk presentation requires finite monotonic seconds");
    }
}
