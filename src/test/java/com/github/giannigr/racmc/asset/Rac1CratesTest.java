package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the table of the crates of the game.
 *
 * <p>The checks hold the numbers of the classes of the crates of the game, so that a change of
 * the table is noticed. The numbers were measured on the disc of the game.
 */
class Rac1CratesTest {

    @Test
    @DisplayName("The mod has the crates of the game")
    void theModHasThreeCrates() {
        List<Rac1CrateDefinition> definitions = Rac1Crates.definitions();

        assertEquals(4, definitions.size(),
                "The mod has the bolt crate, the health crate, the ammo crate and the explosive "
                        + "crate");
    }

    @Test
    @DisplayName("The crates of the game are the classes of the game")
    void theCratesAreTheClassesOfTheGame() {
        assertEquals(500, Rac1Crates.BOLT.classNumber(),
                "The bolt crate is the class 500 of the game");
        assertEquals(501, Rac1Crates.HEALTH.classNumber(),
                "The health crate is the class 501 of the game");
        assertEquals(511, Rac1Crates.AMMO.classNumber(),
                "The ammo crate is the class 511 of the game");
        assertEquals(505, Rac1Crates.EXPLOSIVE.classNumber(),
                "The explosive crate is the class 505 of the game");
    }

    @Test
    @DisplayName("The crates of the game have their own keys")
    void theCratesHaveTheirOwnKeys() {
        Set<String> keys = new HashSet<>();
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            assertFalse(definition.key().isBlank(), "A crate needs a key");
            assertTrue(keys.add(definition.key()),
                    "Two crates of the game have the key " + definition.key());
        }
    }

    @Test
    @DisplayName("A crate of the game is found by its key")
    void aCrateIsFoundByItsKey() {
        Optional<Rac1CrateDefinition> bolt = Rac1Crates.byKey("bolt_crate");

        assertTrue(bolt.isPresent(), "The bolt crate has to be found by its key");
        assertEquals(Rac1Crates.BOLT, bolt.get());
        assertTrue(Rac1Crates.byKey("metal_crate").isEmpty(),
                "The mod has no crate with the key metal_crate");
    }

    @Test
    @DisplayName("A description of a crate needs a key and a class")
    void aDescriptionNeedsAKeyAndAClass() {
        assertThrows(IllegalArgumentException.class, () -> new Rac1CrateDefinition(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> new Rac1CrateDefinition("crate", -1));
        assertNotNull(new Rac1CrateDefinition("crate", 1).key());
    }
}
