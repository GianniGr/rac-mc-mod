package com.github.giannigr.racmc.item.weapon;

/** Per-player/weapon runtime state, independent of media and item-use animation.
 * Explicit snapshots retain once-per-pull dry state across server ticks.
 */
public final class FireState {
    public record Snapshot(long lastShot, long lastClick, boolean clicked) {}
    private final int cadence;
    private final int clickInterval;
    private long lastShot = Long.MIN_VALUE;
    private long lastClick = Long.MIN_VALUE;
    private boolean clicked;

    public FireState(int cadence, int clickInterval) {
        if (cadence < 1 || clickInterval < 1) throw new IllegalArgumentException("Invalid firing intervals");
        this.cadence = cadence;
        this.clickInterval = clickInterval;
    }
    public boolean shotDue(long now) { return due(lastShot, now, cadence); }
    public boolean dryClickAllowed(long now) { return !clicked && due(lastClick, now, clickInterval); }
    private static boolean due(long previous, long now, int interval) {
        return previous == Long.MIN_VALUE || now < previous || now - previous >= interval;
    }
    public void recordShot(long now) { lastShot = now; }
    public void recordDryClick(long now) { lastClick = now; clicked = true; }
    public void beginPull() { clicked = false; }
    /** Initial accepted shot remains immediate on a new pull; click rate spans releases. */
    public void endPull() { lastShot = Long.MIN_VALUE; clicked = false; }
    public Snapshot snapshot() { return new Snapshot(lastShot, lastClick, clicked); }
    public void restore(Snapshot snapshot) {
        lastShot = snapshot.lastShot();
        lastClick = snapshot.lastClick();
        clicked = snapshot.clicked();
    }
}
