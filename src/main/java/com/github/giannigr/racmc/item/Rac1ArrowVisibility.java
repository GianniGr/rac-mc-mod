package com.github.giannigr.racmc.item;

/**
 * The visibility of an arrow of the Blaster in the first ticks of its flight.
 *
 * <p>The arrow of the Blaster is not drawn while it is still at the mouth of the weapon: it is
 * drawn from the second tick of its flight, when it has left the weapon. The rule is a rule of
 * the drawing only: the arrow flies from the moment it is fired, it collides from the moment it
 * is fired, it does its damage from the moment it is fired and it is never picked up.
 *
 * <p>The arrows of the bow, of the crossbow and of the other mods are not touched by the rule:
 * they are drawn from the first tick of their flight, as they always were.
 *
 * <p>The class holds no Minecraft types, so the rule can be checked on a plain virtual machine.
 */
public final class Rac1ArrowVisibility {

    /**
     * The number of ticks the arrow of the Blaster is not drawn.
     *
     * <p>One tick is a twentieth of a second at the speed of twenty ticks of the second.
     */
    public static final int HIDDEN_TICKS = 1;

    private Rac1ArrowVisibility() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Whether an arrow of the Blaster is still hidden.
     *
     * @param tickCount the number of ticks the arrow has been in the world
     * @param partialTick the share of the tick that is drawn
     * @return {@code true} when the arrow is not drawn
     */
    public static boolean hidden(int tickCount, float partialTick) {
        return tickCount + partialTick < HIDDEN_TICKS;
    }
}
