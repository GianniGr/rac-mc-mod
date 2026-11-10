package com.github.giannigr.racmc.asset;

import java.util.Objects;

/**
 * The description of a crate of the game.
 *
 * <p>A crate of the game is a class of the game like every other object of the game: it has a
 * number, and the number is what the converter looks for when it reads the crate out of the
 * disc. The description holds that number and the key the crate is known by inside the mod.
 *
 * <p>The description is data and nothing else: it holds no geometry and no texture, so that the
 * table of the crates can be written down without reading the disc.
 *
 * @param key the key of the crate inside the mod, for example {@code bolt_crate}
 * @param classNumber the number of the class of the crate in the game
 */
public record Rac1CrateDefinition(String key, int classNumber) {

    /**
     * Creates a description of a crate.
     *
     * @param key the key of the crate inside the mod
     * @param classNumber the number of the class of the crate in the game
     */
    public Rac1CrateDefinition {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("A crate needs a key");
        }
        if (classNumber < 0) {
            throw new IllegalArgumentException("A crate needs the number of its class");
        }
    }
}
