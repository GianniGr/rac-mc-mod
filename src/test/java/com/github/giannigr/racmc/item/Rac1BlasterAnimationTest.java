package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the animation of the Blaster.
 *
 * <p>The animation holds no Minecraft types, so it is checked on a plain virtual machine.
 */
class Rac1BlasterAnimationTest {

    /** The size of the tolerance of the checks. */
    private static final float TOLERANCE = 1.0e-4f;

    @Test
    @DisplayName("The blend of the weapon takes two to three ticks")
    void blendTakesTwoToThreeTicks() {
        assertTrue(Rac1BlasterAnimation.BLEND_TICKS >= 2.0f);
        assertTrue(Rac1BlasterAnimation.BLEND_TICKS <= 3.0f);
        assertEquals(0.12f, Rac1BlasterAnimation.BLEND_TICKS / 20.0f, TOLERANCE);
    }

    @Test
    @DisplayName("The cycle of the shot is half a second long and holds five shots")
    void cycleHoldsFiveShots() {
        assertEquals(0.5f, Rac1BlasterAnimation.CYCLE_SECONDS, TOLERANCE);
        assertEquals(5, Rac1BlasterAnimation.SHOTS_PER_CYCLE);
        assertEquals(10.0f, Rac1BlasterAnimation.SHOTS_PER_CYCLE
                / Rac1BlasterAnimation.CYCLE_SECONDS, TOLERANCE);
    }

    @Test
    @DisplayName("The weapon of a player that does not fire is held")
    void weaponOfAPlayerThatDoesNotFireIsHeld() {
        Rac1BlasterAnimation animation = new Rac1BlasterAnimation();
        assertFalse(animation.firing());
        assertEquals(0.0f, animation.weight(0.0f), TOLERANCE);
        assertEquals(0.0f, animation.weight(100.0f), TOLERANCE);
    }

    @Test
    @DisplayName("The weapon is blended into the shot once for a burst")
    void weaponIsBlendedIntoTheShotOnce() {
        Rac1BlasterAnimation animation = new Rac1BlasterAnimation();
        animation.setFiring(true, 100.0f);
        assertTrue(animation.firing());
        assertEquals(0.0f, animation.weight(100.0f), TOLERANCE);
        assertEquals(0.5f, animation.weight(100.0f + Rac1BlasterAnimation.BLEND_TICKS / 2.0f),
                TOLERANCE);
        assertEquals(1.0f, animation.weight(100.0f + Rac1BlasterAnimation.BLEND_TICKS), TOLERANCE);
        assertEquals(1.0f, animation.weight(100.0f + 100.0f), TOLERANCE);
    }

    @Test
    @DisplayName("A burst of many shots does not stutter the weapon")
    void burstDoesNotStutter() {
        Rac1BlasterAnimation animation = new Rac1BlasterAnimation();
        animation.setFiring(true, 0.0f);
        for (int tick = 1; tick < 100; tick++) {
            // The state of the weapon is looked at once a tick. It does not change while the
            // weapon fires, so the blend is not started again.
            animation.setFiring(true, tick);
            if (tick >= Rac1BlasterAnimation.BLEND_TICKS) {
                assertEquals(1.0f, animation.weight(tick), TOLERANCE,
                        "The weapon must not be blended again while it fires");
            }
        }
    }

    @Test
    @DisplayName("The weapon is blended back into the pose of the held weapon")
    void weaponIsBlendedBackOnRelease() {
        Rac1BlasterAnimation animation = new Rac1BlasterAnimation();
        animation.setFiring(true, 0.0f);
        animation.setFiring(false, 50.0f);
        assertFalse(animation.firing());
        assertEquals(1.0f, animation.weight(50.0f), TOLERANCE);
        assertEquals(0.5f, animation.weight(50.0f + Rac1BlasterAnimation.BLEND_TICKS / 2.0f),
                TOLERANCE);
        assertEquals(0.0f, animation.weight(50.0f + Rac1BlasterAnimation.BLEND_TICKS), TOLERANCE);
        assertEquals(0.0f, animation.weight(200.0f), TOLERANCE);
    }

    @Test
    @DisplayName("The shot repeats itself in the cycle of the shot")
    void shotRepeatsItself() {
        Rac1BlasterAnimation animation = new Rac1BlasterAnimation();
        animation.setFiring(true, 0.0f);
        assertEquals(0.0f, animation.fireTime(0.0f), TOLERANCE);
        assertEquals(0.25f, animation.fireTime(5.0f), TOLERANCE);
        assertEquals(0.0f, animation.fireTime(10.0f), TOLERANCE);
        assertEquals(0.1f, animation.fireTime(12.0f), TOLERANCE);
    }

    @Test
    @DisplayName("The shot keeps running while the weapon is blended back")
    void shotKeepsRunningWhileTheWeaponIsBlendedBack() {
        Rac1BlasterAnimation animation = new Rac1BlasterAnimation();
        animation.setFiring(true, 0.0f);
        animation.setFiring(false, 4.0f);
        assertEquals(0.2f, animation.fireTime(4.0f), TOLERANCE);
        assertEquals(0.25f, animation.fireTime(5.0f), TOLERANCE);
    }

    @Test
    @DisplayName("The weapon is held again after the burst is forgotten")
    void weaponIsHeldAgainAfterTheBurst() {
        Rac1BlasterAnimation animation = new Rac1BlasterAnimation();
        animation.setFiring(true, 0.0f);
        animation.reset();
        assertFalse(animation.firing());
        assertEquals(0.0f, animation.weight(10.0f), TOLERANCE);
    }
}
