package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.ModSource;
import net.minecraft.world.entity.projectile.Arrow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the arrow of the Blaster.
 *
 * <p>The arrow of the Blaster is an arrow of the game of blocks with three differences of its
 * own: it flies straight, it is not drawn while it is at the mouth of the weapon, and it is a
 * kind of entity of its own, so that every client knows which arrows the Blaster fired. The
 * arrows of the bow, of the crossbow and of the other mods keep their own fall and their own
 * drawing.
 */
class Rac1BlasterArrowTest {

    @Test
    @DisplayName("The arrow of the Blaster is not drawn while it is at the mouth of the weapon")
    void theArrowIsHiddenAtTheMouth() {
        for (int tick = 0; tick < Rac1ArrowVisibility.HIDDEN_TICKS; tick++) {
            assertTrue(Rac1ArrowVisibility.hidden(tick, 0.0f),
                    "the arrow of the Blaster is drawn while it is at the mouth of the weapon");
            assertTrue(Rac1ArrowVisibility.hidden(tick, 0.99f),
                    "the arrow of the Blaster is drawn while it is at the mouth of the weapon");
        }
    }

    @Test
    @DisplayName("The arrow of the Blaster is drawn from the second tick of its flight")
    void theArrowIsDrawnFromTheSecondTick() {
        assertFalse(Rac1ArrowVisibility.hidden(Rac1ArrowVisibility.HIDDEN_TICKS, 0.0f),
                "the arrow of the Blaster is not drawn after the first tick");
        assertTrue(Rac1ArrowVisibility.hidden(Rac1ArrowVisibility.HIDDEN_TICKS - 1, 0.99f),
                "the arrow of the Blaster is drawn before the first tick is over");
    }

    @Test
    @DisplayName("The hidden tick of the arrow of the Blaster is a twentieth of a second")
    void theHiddenTickIsATwentiethOfASecond() {
        assertTrue(Rac1ArrowVisibility.HIDDEN_TICKS == 1,
                "the arrow of the Blaster is not hidden for one tick");
    }

    @Test
    @DisplayName("The arrow of the Blaster is an arrow of the game of blocks")
    void theArrowIsAnArrowOfTheGame() {
        assertTrue(Arrow.class.isAssignableFrom(Rac1BlasterArrow.class),
                "the arrow of the Blaster is not an arrow of the game of blocks");
    }

    @Test
    @DisplayName("The arrow of the Blaster flies straight")
    void theArrowFliesStraight() {
        String source = ModSource.of("com/github/giannigr/racmc/item/Rac1BlasterArrow.java");
        assertTrue(source.contains("setNoGravity(true)"),
                "the arrow of the Blaster falls with the gravity of the game of blocks");
    }

    @Test
    @DisplayName("The shot of the weapon is made with the arrow of the Blaster")
    void theShotIsMadeWithTheArrowOfTheBlaster() {
        String source = ModSource.of("com/github/giannigr/racmc/item/Rac1BlasterServer.java");
        assertTrue(source.contains("new Rac1BlasterArrow("),
                "the shot of the weapon is made with the arrow of the game of blocks");
        assertTrue(source.contains("RacMcEntities.BLASTER_ARROW.get()"),
                "the shot of the weapon is made with a kind of arrow that the clients do not know");
        assertFalse(source.contains("new Arrow("),
                "the shot of the weapon is made with the arrow of the game of blocks");
    }

    @Test
    @DisplayName("The shot of the weapon still leaves the mouth of the weapon")
    void theShotStillLeavesTheMouth() {
        String source = ModSource.of("com/github/giannigr/racmc/item/Rac1BlasterServer.java");
        assertTrue(source.contains("arrow.setPos(spawn.x, spawn.y, spawn.z)"),
                "the shot of the weapon does not leave the mouth of the weapon");
        assertTrue(source.contains("Pickup.DISALLOWED"),
                "the arrow of the Blaster can be picked up");
    }

    @Test
    @DisplayName("Only the arrow of the Blaster is drawn by the renderer of the mod")
    void onlyTheArrowOfTheBlasterHasItsOwnRenderer() {
        String source = ModSource.of(
                "com/github/giannigr/racmc/client/RacMcClientRegistration.java");
        assertTrue(source.contains("RacMcEntities.BLASTER_ARROW.get()"),
                "the arrow of the Blaster has no renderer of its own");
        assertFalse(source.contains("Arrow.class"),
                "the renderer of the mod is given to the arrows of the game of blocks");
    }

    @Test
    @DisplayName("The renderer of the arrow of the Blaster hides it while it is at the mouth")
    void theRendererHidesTheArrow() {
        String source = ModSource.of(
                "com/github/giannigr/racmc/client/render/Rac1BlasterArrowRenderer.java");
        assertTrue(source.contains("Rac1ArrowVisibility.hidden("),
                "the arrow of the Blaster is drawn while it is at the mouth of the weapon");
    }
}
