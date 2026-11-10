package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.item.Rac1BlasterGrip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The place of the Blaster in the hand of the player in the first person.
 *
 * <p>The game of blocks moves the hand of a player that uses an item down by three fifths of a
 * block, and it moves the hand up again when the use is over. The share of the equip of the hand
 * is the share of the place of the hand of the player, and it is at its lowest while the item is
 * used. The weapon of the game would sink out of the screen while it is fired and jump back into
 * it when the trigger is released.
 *
 * <p>The weapon of the game is therefore given its own place in the hand of the player: the place
 * of the hand of the player, without the share of the equip of the hand. The weapon stands in the
 * same place in the hand of the player while it is only held, while it is fired and while the
 * trigger is released, and the animation of the shot of the weapon is the animation of the mesh
 * of the weapon, which is not touched here.
 */
public final class Rac1BlasterHand {

    /**
     * The share of the equip of the hand of the game of blocks, in blocks.
     *
     * <p>The game of blocks lowers the hand of a player that uses an item by three fifths of a
     * block, and it lowers the hand of a player that takes a new item in his hand by the same
     * measure. The weapon of the game does not take part in it.
     */
    public static final double EQUIP_DROP = 0.6;

    private Rac1BlasterHand() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Moves the hand of the player to the place of the weapon of the game.
     *
     * <p>The turn of the arm of the game of blocks while the player swings his arm is kept, so
     * that the weapon of the game swings with the arm of the player when the player swings his
     * arm.
     *
     * @param poseStack the stack of the poses of the game
     * @param arm the arm of the player that holds the weapon
     * @param using whether the weapon is used with the hand of the player
     * @param swingProgress the share of the swing of the arm of the player
     */
    public static void apply(PoseStack poseStack, HumanoidArm arm, boolean using,
            float swingProgress) {
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        if (!using) {
            // The move of the arm of the game of blocks while the player swings his arm.
            float f = -0.4f * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
            float f1 = 0.2f * Mth.sin(Mth.sqrt(swingProgress) * (float) (Math.PI * 2));
            float f2 = -0.2f * Mth.sin(swingProgress * (float) Math.PI);
            poseStack.translate(side * f, f1, f2);
        }
        // The place of the hand of the player, without the share of the equip of the hand.
        poseStack.translate(side * (float) Rac1BlasterGrip.FIRST_PERSON_HAND[0],
                (float) Rac1BlasterGrip.FIRST_PERSON_HAND[1],
                (float) Rac1BlasterGrip.FIRST_PERSON_HAND[2]);
        if (using) {
            // The weapon is used with the hand of the player: the game of blocks does not turn the
            // arm of the player while the weapon is used.
            return;
        }
        // The turn of the arm of the game of blocks while the player swings his arm.
        float f3 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0f + f3 * -20.0f)));
        float f4 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * f4 * -20.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(f4 * -80.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0f));
    }
}
