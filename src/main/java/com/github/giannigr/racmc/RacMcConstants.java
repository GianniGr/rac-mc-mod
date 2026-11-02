package com.github.giannigr.racmc;

/**
 * Constants that describe the mod itself.
 *
 * <p>This class deliberately has no Minecraft or NeoForge imports so that it can be loaded by
 * plain JVM unit tests without bootstrapping the game. The values here are kept in sync with
 * {@code gradle.properties} and the generated {@code META-INF/neoforge.mods.toml} by the tests in
 * {@code src/test/java}.
 */
public final class RacMcConstants {

    /**
     * The unique mod identifier. Must match {@code mod_id} in {@code gradle.properties} and the
     * {@code modId} entry in {@code META-INF/neoforge.mods.toml}.
     */
    public static final String MOD_ID = "racmc";

    /**
     * The human readable mod name. Must match {@code mod_name} in {@code gradle.properties}.
     */
    public static final String MOD_NAME = "Ratchet & Clank Mod";

    private RacMcConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}
