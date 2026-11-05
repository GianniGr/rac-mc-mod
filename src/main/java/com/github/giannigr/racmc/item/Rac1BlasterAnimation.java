package com.github.giannigr.racmc.item;

/**
 * The state of the animation of the Blaster of one player.
 *
 * <p>The weapon is held with the sequence of the held weapon and fires with the sequence of the
 * shot. The two are blended into each other: the blend into the shot takes
 * {@link #BLEND_TICKS} ticks and happens once for a burst, so that a burst of many shots does
 * not stutter. When the trigger is released, the weapon is blended back into the pose of the
 * held weapon over the same number of ticks.
 *
 * <p>The state holds no Minecraft types, so the rules of the animation can be checked on a plain
 * virtual machine.
 */
public final class Rac1BlasterAnimation {

    /**
     * The number of ticks the blend between the held weapon and the weapon that fires takes.
     *
     * <p>The blend is about six tenths of a tenth of a second, which is two to three ticks of the
     * game. The game of blocks blends the sequences of an item over a few ticks as well.
     */
    public static final float BLEND_TICKS = 2.4f;

    /**
     * The length of the cycle of the sequence of the shot, in seconds.
     *
     * <p>The cycle is half a second long, so the sequence of the shot runs twice a second. Five
     * shots fit into one cycle, which is the ten shots per second of the weapon.
     */
    public static final float CYCLE_SECONDS = 0.5f;

    /** The number of shots that fit into one cycle of the sequence of the shot. */
    public static final int SHOTS_PER_CYCLE = 5;

    /** The number of ticks of the game in a second. */
    private static final float TICKS_PER_SECOND = 20.0f;

    /** Whether the weapon is firing at the moment. */
    private boolean firing;

    /** Whether the weapon has fired at all, so that the blend has a beginning. */
    private boolean started;

    /** The tick at which the state of the weapon changed for the last time. */
    private float changeTick;

    /** The tick at which the burst of shots began. */
    private float burstTick;

    /**
     * Sets whether the weapon is firing.
     *
     * <p>The moment of the change is remembered, so that the blend can be measured from it.
     *
     * @param firing whether the weapon is firing
     * @param tick the tick of the change
     */
    public void setFiring(boolean firing, float tick) {
        if (!this.started) {
            if (!firing) {
                // The weapon has never fired: it is held with the pose of the held weapon.
                return;
            }
            this.started = true;
            this.firing = true;
            this.changeTick = tick;
            this.burstTick = tick;
            return;
        }
        if (this.firing == firing) {
            return;
        }
        this.firing = firing;
        this.changeTick = tick;
        if (firing) {
            this.burstTick = tick;
        }
    }

    /**
     * Whether the weapon is firing.
     *
     * @return {@code true} while the weapon fires
     */
    public boolean firing() {
        return this.firing;
    }

    /**
     * The share of the pose of the shot in the pose of the weapon.
     *
     * @param tick the tick that is measured, with its partial tick
     * @return the share, from {@code 0} (the held weapon) to {@code 1} (the shot)
     */
    public float weight(float tick) {
        if (!this.started) {
            return 0.0f;
        }
        float elapsed = tick - this.changeTick;
        if (elapsed < 0.0f) {
            elapsed = 0.0f;
        }
        float share = elapsed / BLEND_TICKS;
        if (share > 1.0f) {
            share = 1.0f;
        }
        return this.firing ? share : 1.0f - share;
    }

    /**
     * The moment of the sequence of the shot that is played.
     *
     * <p>The sequence of the shot repeats itself, so the moment is taken modulo the length of its
     * cycle. The moment keeps running while the weapon is blended back into the pose of the held
     * weapon, so that the shot does not freeze in the middle of its cycle.
     *
     * @param tick the tick that is measured, with its partial tick
     * @return the moment of the sequence in seconds
     */
    public float fireTime(float tick) {
        if (!this.started) {
            return 0.0f;
        }
        float elapsed = (tick - this.burstTick) / TICKS_PER_SECOND;
        if (elapsed < 0.0f) {
            elapsed = 0.0f;
        }
        return elapsed % CYCLE_SECONDS;
    }

    /**
     * Forgets the whole burst.
     *
     * <p>The weapon is held with the pose of the held weapon again.
     */
    public void reset() {
        this.firing = false;
        this.started = false;
        this.changeTick = 0.0f;
        this.burstTick = 0.0f;
    }
}
