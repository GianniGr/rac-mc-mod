package com.github.giannigr.racmc.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * The keys of the mod.
 *
 * <p>The class holds the keys of the client side. It is only loaded when the client uses it, so
 * that a dedicated server never creates a key of the game.
 */
public final class Rac1ClientKeys {

    /** The key that opens the demo of the Blaster. */
    private static KeyMapping blasterTest;

    private Rac1ClientKeys() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The key that opens the demo of the Blaster.
     *
     * @return the key
     */
    public static KeyMapping blasterTest() {
        if (blasterTest == null) {
            blasterTest = new KeyMapping("key.racmc.blaster_test", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_B, "key.categories.racmc");
        }
        return blasterTest;
    }
}
