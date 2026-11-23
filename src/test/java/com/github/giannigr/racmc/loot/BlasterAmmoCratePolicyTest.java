package com.github.giannigr.racmc.loot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlasterAmmoCratePolicyTest {
    @Test
    void uniformFiveWayRollProducesFour25UnitAndOne50UnitOutcome() {
        assertEquals(5, BlasterAmmoCratePolicy.ROLL_BOUND);
        assertEquals(25, BlasterAmmoCratePolicy.UNITS_PER_PICKUP);
        assertEquals(2, BlasterAmmoCratePolicy.pickups(0));
        for (int roll = 1; roll < 5; roll++) assertEquals(1, BlasterAmmoCratePolicy.pickups(roll));
        assertThrows(IllegalArgumentException.class, () -> BlasterAmmoCratePolicy.pickups(-1));
        assertThrows(IllegalArgumentException.class, () -> BlasterAmmoCratePolicy.pickups(5));
    }
}
