package com.github.giannigr.racmc.item;

/**
 * The ways of play that decide whether the Blaster may fire.
 *
 * <p>The enum holds no Minecraft types on purpose: the rules of the Blaster can then be checked
 * without the game and without a running server.
 */
public enum Rac1GameMode {

    /**
     * The creative mode. The Blaster fires without ammunition and consumes none.
     */
    CREATIVE,

    /**
     * The survival mode. The Blaster needs one ordinary arrow and consumes it.
     */
    SURVIVAL,

    /**
     * The adventure mode. The rules are the rules of the survival mode, because the game lets a
     * player of the adventure mode use the weapon.
     */
    ADVENTURE,

    /**
     * The spectator mode. The Blaster never fires, because the spectator mode does not let a
     * player use anything.
     */
    SPECTATOR
}
