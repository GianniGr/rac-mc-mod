package com.github.giannigr.racmc.item;

/**
 * The lifetime of an arrow that the Blaster fired.
 *
 * <p>The proof of concept gives the arrow of the Blaster the lifetime of the bolt of the game,
 * which is about six tenths of a second. Only the arrows of the Blaster are shortened: the arrows
 * of the game keep their own life, and the life of an arrow of the game is not touched.
 *
 * <p>The class holds no Minecraft types, so the rule can be checked on a plain virtual machine.
 */
public final class Rac1ArrowLifetime {

    /** The number of ticks an arrow of the Blaster flies before it is removed. */
    public static final int LIFETIME_TICKS = 12;

    private Rac1ArrowLifetime() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Whether an arrow of the Blaster has outlived its flight.
     *
     * @param birthTick the tick the arrow was fired on
     * @param currentTick the tick that is checked
     * @return {@code true} when the arrow has to be removed
     */
    public static boolean expired(long birthTick, long currentTick) {
        return currentTick - birthTick >= LIFETIME_TICKS;
    }
}
