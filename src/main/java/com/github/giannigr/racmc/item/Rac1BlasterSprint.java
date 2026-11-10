package com.github.giannigr.racmc.item;

/**
 * The sprint of a player that holds the trigger of the Blaster.
 *
 * <p>The game of blocks does not let a player that uses an item start to sprint: the rule sits in
 * a private method of the player of the client, and it refuses every player that uses an item,
 * whatever the item is and whatever hand the item is used with. The Blaster of the game is held
 * with the trigger, so a player that fires the Blaster cannot run, and the shot of the weapon
 * leaves a player that walks.
 *
 * <p>The rules of the game of blocks are kept as they are. The use of the item is the only rule
 * that is put aside, and it is put aside only for the Blaster in the main hand: the food of the
 * player, the water, the collision, the crouch, the blindness, the passenger, the fall of the
 * player and the other items all keep the rules of the game of blocks.
 *
 * <p>The rules hold no Minecraft types, so they can be checked on a plain virtual machine.
 */
public final class Rac1BlasterSprint {

    /**
     * The number of ticks the trigger of the sprint of the game of blocks stays armed.
     *
     * <p>The game of blocks arms the trigger of the sprint when a player that stands on the
     * ground lets go of the key of the forward, and it starts the sprint when the player presses
     * the key of the forward again while the trigger is armed. The trigger is the way the game
     * of blocks lets a player start to sprint without the key of the sprint.
     */
    public static final int TRIGGER_TICKS = 7;

    /**
     * The share of the input of the keyboard a player needs to start to sprint.
     *
     * <p>The share is the share of the game of blocks: the game of blocks starts to sprint when
     * the share of the forward of the keyboard reaches four fifths.
     */
    public static final float IMPULSE = 0.8f;

    /**
     * The food a player needs to start to sprint.
     *
     * <p>The game of blocks starts to sprint when the food of the player is more than six.
     */
    public static final int FOOD = 6;

    private Rac1BlasterSprint() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The conditions of the sprint of a player.
     *
     * @param onGround whether the player stands on the ground
     * @param inWater whether the player is in the water
     * @param canSwim whether the player may swim in the water he is in
     * @param crouching whether the player holds the key of the crouch
     * @param blind whether the player is blinded
     * @param passenger whether the player rides
     * @param vehicleCanSprint whether the vehicle of the player may sprint
     * @param fallFlying whether the player falls with wings
     * @param spectator whether the player watches
     * @param food the food of the player
     * @param mayFly whether the player may fly
     * @param forwardImpulse the share of the forward of the keyboard
     * @param sprintKey whether the key of the sprint is held down
     */
    public record Conditions(boolean onGround, boolean inWater, boolean canSwim, boolean crouching,
            boolean blind, boolean passenger, boolean vehicleCanSprint, boolean fallFlying,
            boolean spectator, int food, boolean mayFly, float forwardImpulse,
            boolean sprintKey) {
    }

    /**
     * Whether the share of the forward of the keyboard is enough to start to sprint.
     *
     * @param conditions the conditions of the sprint of the player
     * @return {@code true} when the share is enough
     */
    public static boolean enoughImpulse(Conditions conditions) {
        return conditions.forwardImpulse() >= IMPULSE;
    }

    /**
     * Whether the player may start to sprint.
     *
     * <p>The rules are the rules of the game of blocks: the player must not watch, must not fall
     * with wings, must not ride a vehicle that may not sprint, must have food enough to sprint,
     * must not be blinded and must not be in the water he may not swim in.
     *
     * @param conditions the conditions of the sprint of the player
     * @return {@code true} when the player may start to sprint
     */
    public static boolean mayStart(Conditions conditions) {
        if (conditions.spectator() || conditions.fallFlying()) {
            return false;
        }
        if (conditions.passenger() && !conditions.vehicleCanSprint()) {
            return false;
        }
        if (!conditions.passenger() && !conditions.mayFly() && conditions.food() <= FOOD) {
            return false;
        }
        if (conditions.blind()) {
            return false;
        }
        if (conditions.inWater() && !conditions.canSwim()) {
            return false;
        }
        return true;
    }

    /**
     * Whether the trigger of the sprint of the game of blocks may be armed.
     *
     * <p>The game of blocks arms the trigger when a player that stands on the ground, or that is
     * in the water he may swim in, lets go of the key of the forward. The trigger is not armed
     * while the player holds the key of the crouch, and it is not armed while the key of the
     * sprint is held down: the key of the sprint starts the sprint right away.
     *
     * @param conditions the conditions of the sprint of the player
     * @return {@code true} when the trigger may be armed
     */
    public static boolean mayArm(Conditions conditions) {
        if (!conditions.onGround() && !conditions.canSwim()) {
            return false;
        }
        return !conditions.crouching() && !conditions.sprintKey();
    }

    /**
     * The trigger of the sprint of a player.
     *
     * <p>The trigger is the way the game of blocks lets a player start to sprint without the
     * key of the sprint: the trigger is armed while the player lets go of the key of the
     * forward, and the sprint starts when the player presses the key of the forward again.
     */
    public static final class Trigger {

        /** The number of ticks the trigger is armed. */
        private int ticks;

        /**
         * Counts one tick of the trigger.
         */
        public void tick() {
            if (this.ticks > 0) {
                this.ticks--;
            }
        }

        /**
         * Arms the trigger.
         */
        public void arm() {
            this.ticks = TRIGGER_TICKS;
        }

        /**
         * Whether the trigger is armed.
         *
         * @return {@code true} when the trigger is armed
         */
        public boolean armed() {
            return this.ticks > 0;
        }

        /**
         * Forgets the trigger.
         */
        public void clear() {
            this.ticks = 0;
        }
    }
}
