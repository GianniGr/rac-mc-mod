package com.github.giannigr.racmc.asset;

import java.util.List;
import java.util.Optional;

/**
 * The crates of the game that the mod imports.
 *
 * <p>The game of blocks has no crates of its own, so every crate of the mod is the crate of the
 * game: the geometry and the textures are the ones that were read out of the disc. The table
 * below names the classes of the game that hold the crates.
 *
 * <p>The numbers of the classes were measured on the disc of the game and are the same in every
 * level that has the crate. They are the numbers that the editor of the game shows for the
 * classes:
 *
 * <ul>
 *   <li>500 is the {@code bolt_crate} - the wooden crate that holds bolts;</li>
 *   <li>501 is the {@code nanotech_crate} - the crate of the nanotech, which is the health of
 *       the game;</li>
 *   <li>511 is the {@code ammo_crate} - the light grey crate of Gadgetron that holds the
 *       ammunition of the weapons.</li>
 * </ul>
 *
 * <p>The classes are read out of the first level of the game, the level of Novalis. Every crate
 * of the table stands exactly one unit high and is one unit wide and deep, so that it fits into
 * one block of the game of blocks without being scaled.
 */
public final class Rac1Crates {

    /**
     * The crate that holds the bolts of the game.
     *
     * <p>The crate is the wooden one of the game: a crate of light wood with a frame of yellow
     * metal around it.
     */
    public static final Rac1CrateDefinition BOLT = new Rac1CrateDefinition("bolt_crate", 500);

    /**
     * The crate that holds the nanotech of the game.
     *
     * <p>The nanotech is the health of the game, so this crate is the health crate. The crate is
     * the blue and grey one of the game.
     */
    public static final Rac1CrateDefinition HEALTH = new Rac1CrateDefinition("health_crate", 501);

    /**
     * The crate that holds the ammunition of the weapons.
     *
     * <p>The crate is the light grey one of Gadgetron with the red sign of Gadgetron on it.
     */
    public static final Rac1CrateDefinition AMMO = new Rac1CrateDefinition("ammo_crate", 511);

    /** The crates of the game, in the order they are converted and shown. */
    private static final List<Rac1CrateDefinition> DEFINITIONS = List.of(BOLT, HEALTH, AMMO);

    private Rac1Crates() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The crates of the game.
     *
     * @return the descriptions of the crates, in the order they are converted and shown
     */
    public static List<Rac1CrateDefinition> definitions() {
        return DEFINITIONS;
    }

    /**
     * The description of the crate with the given key.
     *
     * @param key the key of the crate
     * @return the description, or an empty optional when the mod has no such crate
     */
    public static Optional<Rac1CrateDefinition> byKey(String key) {
        for (Rac1CrateDefinition definition : DEFINITIONS) {
            if (definition.key().equals(key)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }
}
