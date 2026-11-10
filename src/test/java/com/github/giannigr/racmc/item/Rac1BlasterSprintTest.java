package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the sprint of a player that holds the trigger of the Blaster.
 *
 * <p>The game of blocks refuses every player that uses an item the right to start to sprint. The
 * rules of the game of blocks are kept as they are here, and only the use of the item is put
 * aside, and only for the Blaster in the main hand.
 */
class Rac1BlasterSprintTest {

    /** The conditions of a player that may start to sprint. */
    private static final Rac1BlasterSprint.Conditions READY = new Rac1BlasterSprint.Conditions(
            true, false, false, false, false, false, true, false, false, 20, false, 1.0f, false);

    @Test
    @DisplayName("The key of the sprint starts the sprint")
    void theSprintKeyStartsTheSprint() {
        Rac1BlasterSprint.Conditions conditions = with(READY, true, 1.0f);
        assertTrue(Rac1BlasterSprint.mayStart(conditions),
                "A player that may sprint may not start to sprint");
        assertTrue(Rac1BlasterSprint.enoughImpulse(conditions),
                "The share of the keyboard is not enough");
    }

    @Test
    @DisplayName("The trigger of the sprint starts the sprint")
    void theTriggerStartsTheSprint() {
        Rac1BlasterSprint.Trigger trigger = new Rac1BlasterSprint.Trigger();
        // The player lets go of the key of the forward: the trigger is armed.
        Rac1BlasterSprint.Conditions released = with(READY, false, 0.0f);
        assertTrue(Rac1BlasterSprint.mayArm(released),
                "The trigger of the sprint may not be armed");
        trigger.arm();
        assertTrue(trigger.armed(), "The trigger of the sprint is not armed");
        // The player presses the key of the forward again: the sprint starts.
        trigger.tick();
        assertTrue(trigger.armed(), "The trigger of the sprint is not armed any more");
    }

    @Test
    @DisplayName("The trigger of the sprint is forgotten after its time")
    void theTriggerIsForgotten() {
        Rac1BlasterSprint.Trigger trigger = new Rac1BlasterSprint.Trigger();
        trigger.arm();
        for (int tick = 0; tick < Rac1BlasterSprint.TRIGGER_TICKS; tick++) {
            trigger.tick();
        }
        assertFalse(trigger.armed(), "The trigger of the sprint stays armed for ever");
    }

    @Test
    @DisplayName("The share of the keyboard of the game of blocks is kept")
    void theShareOfTheKeyboardIsKept() {
        assertFalse(Rac1BlasterSprint.enoughImpulse(with(READY, false, 0.79f)),
                "A share that is too small starts the sprint");
        assertTrue(Rac1BlasterSprint.enoughImpulse(with(READY, false, 0.8f)),
                "A share that is enough does not start the sprint");
    }

    @Test
    @DisplayName("A player without food does not sprint")
    void aPlayerWithoutFoodDoesNotSprint() {
        assertFalse(Rac1BlasterSprint.mayStart(withFood(6)),
                "A player without food starts to sprint");
        assertTrue(Rac1BlasterSprint.mayStart(withFood(7)),
                "A player with food does not start to sprint");
    }

    @Test
    @DisplayName("A blinded player does not sprint")
    void aBlindedPlayerDoesNotSprint() {
        Rac1BlasterSprint.Conditions blinded = new Rac1BlasterSprint.Conditions(true, false, false,
                false, true, false, true, false, false, 20, false, 1.0f, false);
        assertFalse(Rac1BlasterSprint.mayStart(blinded), "A blinded player starts to sprint");
    }

    @Test
    @DisplayName("The trigger of the sprint is not armed while the player crouches")
    void aCrouchingPlayerDoesNotArmTheTrigger() {
        Rac1BlasterSprint.Conditions crouching = new Rac1BlasterSprint.Conditions(true, false,
                false, true, false, false, true, false, false, 20, false, 0.0f, false);
        assertFalse(Rac1BlasterSprint.mayArm(crouching),
                "The trigger of the sprint is armed while the player crouches");
    }

    @Test
    @DisplayName("A player in the water he may not swim in does not sprint")
    void aPlayerInTheWaterDoesNotSprint() {
        Rac1BlasterSprint.Conditions water = new Rac1BlasterSprint.Conditions(true, true, false,
                false, false, false, true, false, false, 20, false, 1.0f, false);
        assertFalse(Rac1BlasterSprint.mayStart(water),
                "A player in the water he may not swim in starts to sprint");
        Rac1BlasterSprint.Conditions swimmer = new Rac1BlasterSprint.Conditions(true, true, true,
                false, false, false, true, false, false, 20, false, 1.0f, false);
        assertTrue(Rac1BlasterSprint.mayStart(swimmer), "A player that may swim does not sprint");
    }

    @Test
    @DisplayName("A player that rides a vehicle that may not sprint does not sprint")
    void aPassengerDoesNotSprint() {
        Rac1BlasterSprint.Conditions passenger = new Rac1BlasterSprint.Conditions(true, false, false,
                false, false, true, false, false, false, 20, false, 1.0f, false);
        assertFalse(Rac1BlasterSprint.mayStart(passenger),
                "A player that rides a vehicle that may not sprint starts to sprint");
        Rac1BlasterSprint.Conditions riding = new Rac1BlasterSprint.Conditions(true, false, false,
                false, false, true, true, false, false, 20, false, 1.0f, false);
        assertTrue(Rac1BlasterSprint.mayStart(riding),
                "A player that rides a vehicle that may sprint does not sprint");
    }

    @Test
    @DisplayName("A player that falls with wings does not sprint")
    void aFallingPlayerDoesNotSprint() {
        Rac1BlasterSprint.Conditions falling = new Rac1BlasterSprint.Conditions(false, false, false,
                false, false, false, true, true, false, 20, false, 1.0f, false);
        assertFalse(Rac1BlasterSprint.mayStart(falling),
                "A player that falls with wings starts to sprint");
    }

    @Test
    @DisplayName("A player that watches does not sprint")
    void aSpectatorDoesNotSprint() {
        Rac1BlasterSprint.Conditions watching = new Rac1BlasterSprint.Conditions(false, false, false,
                false, false, false, true, false, true, 20, false, 1.0f, false);
        assertFalse(Rac1BlasterSprint.mayStart(watching), "A player that watches starts to sprint");
    }

    /**
     * The conditions of a player with another key of the sprint and another share of the
     * keyboard.
     *
     * @param conditions the conditions of the player
     * @param sprintKey whether the key of the sprint is held down
     * @param impulse the share of the forward of the keyboard
     * @return the conditions of the player
     */
    private static Rac1BlasterSprint.Conditions with(Rac1BlasterSprint.Conditions conditions,
            boolean sprintKey, float impulse) {
        return new Rac1BlasterSprint.Conditions(conditions.onGround(), conditions.inWater(),
                conditions.canSwim(), conditions.crouching(), conditions.blind(),
                conditions.passenger(), conditions.vehicleCanSprint(), conditions.fallFlying(),
                conditions.spectator(), conditions.food(), conditions.mayFly(), impulse,
                sprintKey);
    }

    /**
     * The conditions of a player with another food.
     *
     * @param food the food of the player
     * @return the conditions of the player
     */
    private static Rac1BlasterSprint.Conditions withFood(int food) {
        return new Rac1BlasterSprint.Conditions(READY.onGround(), READY.inWater(), READY.canSwim(),
                READY.crouching(), READY.blind(), READY.passenger(), READY.vehicleCanSprint(),
                READY.fallFlying(), READY.spectator(), food, READY.mayFly(),
                READY.forwardImpulse(), READY.sprintKey());
    }
}
