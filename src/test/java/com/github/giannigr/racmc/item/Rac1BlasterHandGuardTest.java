package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.ModSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the guards of the hand of the Blaster.
 *
 * <p>The Blaster is fired with the main hand only. A pull of the trigger with the other hand is
 * refused before anything happens: no shot, no ammunition, no state of the use and no animation.
 *
 * <p>The guards live in the classes of the game of blocks, and a player of the game of blocks
 * cannot be built on a plain virtual machine. The order of the guards is therefore read out of
 * the source of the mod, so that a guard that is moved behind the shot of the weapon is caught.
 */
class Rac1BlasterHandGuardTest {

    @Test
    @DisplayName("The other hand is refused before the weapon fires and before it is used")
    void theOtherHandIsRefusedFirst() {
        String body = ModSource.methodOf(
                ModSource.of("com/github/giannigr/racmc/item/BlasterItem.java"),
                "public InteractionResultHolder<ItemStack> use(");
        int refusal = body.indexOf("InteractionHand.OFF_HAND");
        int firing = body.indexOf("fireFirstShot");
        int using = body.indexOf("startUsingItem");
        assertTrue(refusal >= 0, "the other hand is not refused");
        assertTrue(firing >= 0, "the weapon does not fire");
        assertTrue(using >= 0, "the weapon is not used");
        assertTrue(refusal < firing, "the other hand is refused after the weapon fires");
        assertTrue(firing < using, "the weapon is used before it fires");
        assertTrue(body.contains("InteractionResultHolder.pass"),
                "the other hand is not refused without changing the stack of the hand");
    }

    @Test
    @DisplayName("The shot of the weapon is made from the stack of the Blaster in the main hand")
    void theShotIsMadeFromTheBlasterOfTheMainHand() {
        String source = ModSource.of("com/github/giannigr/racmc/item/Rac1BlasterServer.java");
        assertTrue(source.contains("getMainHandItem()"),
                "the weapon of the shot does not come from the main hand");
        assertFalse(source.contains("getItemInHand("),
                "the weapon of the shot comes out of an arbitrary hand of the player");
        assertTrue(source.contains("weapon.isEmpty()"),
                "the shot is not refused when the main hand holds no Blaster");
    }

    @Test
    @DisplayName("The rules of the weapon are only asked for the main hand")
    void theRulesAreOnlyAskedForTheMainHand() {
        String source = ModSource.of("com/github/giannigr/racmc/item/Rac1BlasterServer.java");
        assertTrue(source.contains("Rac1FirePolicy.Hand.MAIN"),
                "the rules of the weapon are not asked for the main hand");
        assertFalse(source.contains("Rac1FirePolicy.Hand.OFF"),
                "the rules of the weapon are asked for the other hand");
    }
}
