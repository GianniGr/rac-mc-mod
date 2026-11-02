package com.github.giannigr.racmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks that the mod id can be used as a Minecraft resource namespace.
 *
 * <p>Every block, item, data file and translation key added later is addressed with the mod id
 * as its namespace, so an invalid mod id would only surface as a runtime failure inside the game.
 * This test runs in the NeoForge aware unit test environment, so it can use Minecraft types
 * directly.
 */
class ModNamespaceTest {

    @Test
    @DisplayName("The mod id is a valid Minecraft resource namespace")
    void modIdIsAValidNamespace() {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "bootstrap");

        assertEquals(RacMcConstants.MOD_ID, location.getNamespace());
        assertEquals("bootstrap", location.getPath());
    }

    @Test
    @DisplayName("A resource location in the mod namespace can be parsed")
    void modNamespacedLocationCanBeParsed() {
        ResourceLocation location = ResourceLocation.parse(RacMcConstants.MOD_ID + ":bootstrap");

        assertNotNull(location);
        assertEquals(RacMcConstants.MOD_ID, location.getNamespace());
    }
}
