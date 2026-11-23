package com.github.giannigr.racmc.item.weapon;

import static com.github.giannigr.racmc.item.weapon.BlasterEquipTransition.Cue.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlasterEquipTransitionTest {
    @Test void seededHoldingIsSilentAndEachFreshDrawHasOneOrderedPair() {
        var transition = new BlasterEquipTransition();
        assertEquals(NONE, transition.observe(true));
        assertEquals(NONE, transition.observe(true));
        for (int i = 0; i < 2; i++) {
            assertEquals(NONE, transition.observe(false));
            assertEquals(START, transition.observe(true));
            assertEquals(NONE, transition.observe(true));
            assertEquals(FINISH, transition.observe(true));
            assertEquals(NONE, transition.observe(true));
        }
    }

    @Test void switchingAwayCancelsThePendingSecondCue() {
        var transition = new BlasterEquipTransition();
        assertEquals(NONE, transition.observe(false));
        assertEquals(START, transition.observe(true));
        assertEquals(NONE, transition.observe(true));
        assertEquals(NONE, transition.observe(false));
        assertEquals(START, transition.observe(true));
        assertEquals(NONE, transition.observe(true));
        assertEquals(FINISH, transition.observe(true));
    }
}
