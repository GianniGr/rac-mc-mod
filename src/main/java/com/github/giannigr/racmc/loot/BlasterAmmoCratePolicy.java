package com.github.giannigr.racmc.loot;

/** Reviewed class-511 pickup-count draw, shared by all routed ammo types. */
public final class BlasterAmmoCratePolicy {
    public static final int UNITS_PER_PICKUP = com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy.BLASTER.pickupUnits();
    public static final int ROLL_BOUND = 5;

    private BlasterAmmoCratePolicy() {}

    public static int pickups(int roll) {
        if (roll < 0 || roll >= ROLL_BOUND) throw new IllegalArgumentException("Invalid ammo drop roll");
        return roll == 0 ? 2 : 1;
    }
}
