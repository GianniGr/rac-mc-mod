package com.github.giannigr.racmc.effect;

/** Fractional original-update clock driven by caller's monotonic game time, not render FPS.
 * Paused calls rebase time without catch-up. A long stall clears effects rather than replaying bursts. */
public final class EffectClock {
    public enum Profile {
        NTSC(60), PAL(50);
        private final int hz;
        Profile(int hz) { this.hz = hz; }
        public int hz() { return hz; }
        public int ticks(int ntscTicks) { return Math.round(ntscTicks * hz / 60.0f); }
    }
    public record Advance(int steps, double interpolation, boolean discontinuity) {}
    private final Profile profile;
    private double last = Double.NaN, remainder;
    public EffectClock(Profile profile) { this.profile = java.util.Objects.requireNonNull(profile); }
    public Profile profile() { return profile; }
    public void reset() { last = Double.NaN; remainder = 0; }
    public Advance advance(double gameSeconds, boolean paused) {
        if (!Double.isFinite(gameSeconds) || gameSeconds < 0)
            throw new IllegalArgumentException("Invalid effect clock time");
        if (Double.isNaN(last) || paused) {
            last = gameSeconds;
            return new Advance(0, remainder, false);
        }
        double delta = gameSeconds - last;
        last = gameSeconds;
        if (delta < 0 || delta > 0.25) {
            remainder = 0;
            return new Advance(0, 0, true);
        }
        double updates = remainder + delta * profile.hz();
        int steps = (int) Math.floor(updates + 1.0e-9);
        remainder = Math.max(0, updates - steps);
        return new Advance(steps, remainder, false);
    }
}
