package com.github.giannigr.racmc.client;

import net.minecraft.client.model.HumanoidModel;

/**
 * The pose of the arm that holds the Blaster of the game.
 *
 * <p>The pose of the arm of the game of blocks is an enum that NeoForge lets a mod add a value
 * to. The value is added with the file {@code META-INF/enumextensions.json} of the mod, and the
 * name of the value is {@value #NAME}.
 *
 * <p>The pose turns the arm of the player towards the place the player looks at, so that the
 * bore of the weapon points at the crosshair of the screen.
 */
public final class Rac1BlasterArmPose {

    /** The name of the new value of the pose of the arm of the game of blocks. */
    public static final String NAME = "RACMC_AIM";

    private Rac1BlasterArmPose() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The pose of the arm that holds the Blaster.
     *
     * @return the pose, or {@code null} when the game of blocks does not know it
     */
    public static HumanoidModel.ArmPose aim() {
        try {
            return HumanoidModel.ArmPose.valueOf(NAME);
        } catch (IllegalArgumentException exception) {
            // The game of blocks does not know the new value of the pose: the arm of the player
            // keeps the pose of the game of blocks for an item that is held.
            return null;
        }
    }
}
