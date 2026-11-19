package com.github.giannigr.racmc.clank;

/** Pure proposed episode ledger. No damage event/hook is installed by this class. */
public final class Rac1ClankFallEpisode {
    private boolean active;
    private double debt, descent;
    public void begin(double existingFallDistance) {
        requireDistance(existingFallDistance);
        if (active) throw new IllegalStateException("Cannot refill an active fall episode");
        active = true;
        debt = existingFallDistance;
        descent = 0;
    }
    public void moved(double actualY) {
        if (!Double.isFinite(actualY)) throw new IllegalArgumentException("Invalid displacement");
        if (active && actualY < 0) descent -= actualY;
    }
    public double landingDistance(double nativeDistance) {
        requireDistance(nativeDistance);
        if (!active) return nativeDistance;
        double remaining = Math.max(debt, nativeDistance - descent);
        active = false;
        return remaining;
    }
    public double cancelDistance(double nativeDistance) {
        requireDistance(nativeDistance);
        if (!active) return nativeDistance;
        double remaining = Math.max(nativeDistance, debt + descent);
        active = false;
        return remaining;
    }
    public boolean active() { return active; }
    public double debt() { return debt; }
    public double actionDescent() { return descent; }
    private static void requireDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > 1_000_000)
            throw new IllegalArgumentException("Invalid fall bookkeeping");
    }
}
