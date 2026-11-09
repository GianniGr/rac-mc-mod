package com.github.giannigr.racmc.item;

/**
 * The rules that decide whether the Blaster may fire and what a shot costs.
 *
 * <p>The rules are free of Minecraft types, so they can be checked on a plain virtual machine.
 * The class holds the numbers of the proof of concept:
 *
 * <ul>
 *   <li>the Blaster fires every {@link #SHOT_INTERVAL_TICKS} ticks, which is the ten shots per
 *       second of the game;</li>
 *   <li>the click of an empty weapon may only be played again after
 *       {@link #DRY_FIRE_COOLDOWN_TICKS} ticks, so a held trigger cannot spam the click;</li>
 *   <li>the weapon is fired with the main hand only: a pull of the trigger with the other hand
 *       is refused and nothing happens at all;</li>
 *   <li>an arrow of the Blaster flies for {@link Rac1ArrowLifetime#LIFETIME_TICKS} ticks.</li>
 * </ul>
 */
public final class Rac1FirePolicy {

    /** The number of ticks between two shots of a burst. */
    public static final int SHOT_INTERVAL_TICKS = 2;

    /** The number of ticks that have to pass before the click of an empty weapon may play again. */
    public static final int DRY_FIRE_COOLDOWN_TICKS = 10;

    /**
     * The hand the trigger was pulled with.
     *
     * <p>The names are the names of the hands of the game of blocks, without the types of the
     * game, so that the rules can be checked on a plain virtual machine.
     */
    public enum Hand {

        /** The main hand of the player. */
        MAIN,

        /** The off hand of the player. */
        OFF
    }

    /** What the Blaster does with one pull of the trigger. */
    public enum Outcome {

        /** The shot is fired and no ammunition is consumed. */
        FIRE_WITHOUT_AMMO,

        /** The shot is fired and one ordinary arrow is consumed. */
        FIRE_WITH_AMMO,

        /**
         * No shot is fired. The click of the empty weapon is played once for this pull of the
         * trigger.
         */
        DRY_FIRE,

        /** Nothing happens at all: no shot, no click and no animation. */
        BLOCKED
    }

    private Rac1FirePolicy() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Decides what one pull of the trigger does.
     *
     * @param mode the way of play of the player
     * @param ordinaryArrows the number of ordinary arrows the player carries
     * @return the outcome of the pull
     */
    public static Outcome evaluate(Rac1GameMode mode, int ordinaryArrows) {
        return evaluate(mode, ordinaryArrows, Hand.MAIN);
    }

    /**
     * Decides what one pull of the trigger does.
     *
     * <p>The Blaster of the game is fired with the main hand only. A pull of the trigger with the
     * other hand is refused before anything else happens: no shot is fired, no click is played,
     * no ammunition is used and the weapon is not used at all.
     *
     * @param mode the way of play of the player
     * @param ordinaryArrows the number of ordinary arrows the player carries
     * @param hand the hand the trigger was pulled with
     * @return the outcome of the pull
     */
    public static Outcome evaluate(Rac1GameMode mode, int ordinaryArrows, Hand hand) {
        if (hand == Hand.OFF) {
            return Outcome.BLOCKED;
        }
        return switch (mode) {
            case CREATIVE -> Outcome.FIRE_WITHOUT_AMMO;
            case SURVIVAL, ADVENTURE -> ordinaryArrows > 0
                    ? Outcome.FIRE_WITH_AMMO
                    : Outcome.DRY_FIRE;
            case SPECTATOR -> Outcome.BLOCKED;
        };
    }

    /**
     * Whether the outcome refuses the shot altogether.
     *
     * @param outcome the outcome of a pull of the trigger
     * @return {@code true} when nothing happens at all
     */
    public static boolean isBlocked(Outcome outcome) {
        return outcome == Outcome.BLOCKED;
    }

    /**
     * Whether the outcome fires a shot.
     *
     * @param outcome the outcome of a pull of the trigger
     * @return {@code true} when a shot is fired
     */
    public static boolean firesShot(Outcome outcome) {
        return outcome == Outcome.FIRE_WITHOUT_AMMO || outcome == Outcome.FIRE_WITH_AMMO;
    }

    /**
     * Whether the outcome consumes an ordinary arrow.
     *
     * @param outcome the outcome of a pull of the trigger
     * @return {@code true} when one ordinary arrow is consumed
     */
    public static boolean consumesArrow(Outcome outcome) {
        return outcome == Outcome.FIRE_WITH_AMMO;
    }

    /**
     * Whether the outcome plays the click of an empty weapon.
     *
     * @param outcome the outcome of a pull of the trigger
     * @return {@code true} when the click is played
     */
    public static boolean playsDryFireClick(Outcome outcome) {
        return outcome == Outcome.DRY_FIRE;
    }
}
