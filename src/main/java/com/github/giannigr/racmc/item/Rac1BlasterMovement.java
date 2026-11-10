package com.github.giannigr.racmc.item;

/**
 * The share of the input of a player that holds the trigger of the Blaster.
 *
 * <p>The game of blocks slows a player down that uses an item: the share of the input of the
 * keyboard is multiplied by {@value #VANILLA_USING_ITEM_SCALE}, so that a player that holds a
 * bow walks at a fifth of the speed of a player that walks with empty hands.
 *
 * <p>The Blaster of the game is not a bow: a player that holds the trigger of the Blaster walks
 * at the speed of a player that walks with empty hands. The game of blocks offers one narrow
 * place to change the input of a player - the event of the update of the input of the movement,
 * which is fired before the game of blocks multiplies the input - so the share that the game of
 * blocks takes away is put back there, and only there, and only for the Blaster.
 *
 * <p>The rules hold no Minecraft types, so they can be checked on a plain virtual machine.
 */
public final class Rac1BlasterMovement {

    /**
     * The share of the input of the keyboard that the game of blocks leaves to a player that
     * uses an item.
     */
    public static final float VANILLA_USING_ITEM_SCALE = 0.2f;

    /**
     * The factor that puts the whole input of the keyboard back.
     *
     * <p>The factor is the inverse of the share of the game of blocks, so that the input of the
     * player is the same as the input of the keyboard after the game of blocks has taken its
     * share.
     */
    public static final float COMPENSATION = 1.0f / VANILLA_USING_ITEM_SCALE;

    private Rac1BlasterMovement() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Puts the share of the game of blocks back into one share of the input of the keyboard.
     *
     * @param impulse the share of the input of the keyboard
     * @return the share that is left after the game of blocks has taken its share
     */
    public static float compensate(float impulse) {
        return impulse * COMPENSATION;
    }
}
