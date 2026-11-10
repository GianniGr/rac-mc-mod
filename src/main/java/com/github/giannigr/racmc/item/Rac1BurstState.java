package com.github.giannigr.racmc.item;

/**
 * The bookkeeping of one burst of shots of one player.
 *
 * <p>The class holds three things: the tick of the last shot, the tick of the last click of the
 * empty weapon, and the fact that the click of the empty weapon was already played for the
 * current pull of the trigger.
 *
 * <p>The class holds no Minecraft types, so the rules of the burst can be checked on a plain
 * virtual machine.
 */
public final class Rac1BurstState {

    /** The tick of the last shot, or {@link Long#MIN_VALUE} when nothing was fired yet. */
    private long lastShotTick = Long.MIN_VALUE;

    /** The tick of the last click of the empty weapon. */
    private long lastDryClickTick = Long.MIN_VALUE;

    /** Whether the click of the empty weapon was already played for the current pull. */
    private boolean dryClickPlayed;

    /**
     * Whether the next shot is due.
     *
     * <p>The first shot of a burst is due at once. Every later shot is due
     * {@link Rac1FirePolicy#SHOT_INTERVAL_TICKS} ticks after the one before it, which is the ten
     * shots per second of the game.
     *
     * @param tick the tick that is checked
     * @return {@code true} when a shot may be fired
     */
    public boolean shotDue(long tick) {
        return this.lastShotTick == Long.MIN_VALUE
                || tick - this.lastShotTick >= Rac1FirePolicy.SHOT_INTERVAL_TICKS;
    }

    /**
     * Remembers that a shot was fired.
     *
     * @param tick the tick the shot was fired on
     */
    public void recordShot(long tick) {
        this.lastShotTick = tick;
    }

    /**
     * Whether the click of the empty weapon may be played.
     *
     * <p>The click is bound to two rules: it is played at most once for a pull of the trigger,
     * and it is played at most once every {@link Rac1FirePolicy#DRY_FIRE_COOLDOWN_TICKS} ticks,
     * so a held trigger cannot spam it.
     *
     * @param tick the tick that is checked
     * @return {@code true} when the click may be played
     */
    public boolean dryClickAllowed(long tick) {
        if (this.dryClickPlayed) {
            return false;
        }
        return this.lastDryClickTick == Long.MIN_VALUE
                || tick - this.lastDryClickTick >= Rac1FirePolicy.DRY_FIRE_COOLDOWN_TICKS;
    }

    /**
     * Remembers that the click of the empty weapon was played.
     *
     * @param tick the tick the click was played on
     */
    public void recordDryClick(long tick) {
        this.lastDryClickTick = tick;
        this.dryClickPlayed = true;
    }

    /**
     * Starts a new pull of the trigger.
     *
     * <p>The tick of the last click is kept, so that the bound on the rate of the click holds
     * over the pulls of the trigger as well.
     */
    public void beginPull() {
        this.dryClickPlayed = false;
    }

    /**
     * Forgets the whole burst.
     *
     * <p>The burst is forgotten when the trigger is released and when the player leaves, so that
     * the next pull of the trigger starts at once.
     */
    public void reset() {
        this.lastShotTick = Long.MIN_VALUE;
        this.lastDryClickTick = Long.MIN_VALUE;
        this.dryClickPlayed = false;
    }

    /**
     * The tick of the last shot.
     *
     * @return the tick, or {@link Long#MIN_VALUE} when nothing was fired yet
     */
    public long lastShotTick() {
        return this.lastShotTick;
    }

    /**
     * The tick of the last click of the empty weapon.
     *
     * @return the tick, or {@link Long#MIN_VALUE} when the click was never played
     */
    public long lastDryClickTick() {
        return this.lastDryClickTick;
    }

    /**
     * Restores the burst from two ticks.
     *
     * @param lastShotTick the tick of the last shot, or {@link Long#MIN_VALUE}
     * @param lastDryClickTick the tick of the last click, or {@link Long#MIN_VALUE}
     */
    public void restore(long lastShotTick, long lastDryClickTick) {
        this.lastShotTick = lastShotTick;
        this.lastDryClickTick = lastDryClickTick;
        this.dryClickPlayed = false;
    }
}
