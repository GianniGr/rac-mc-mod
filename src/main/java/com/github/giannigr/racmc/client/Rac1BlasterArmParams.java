package com.github.giannigr.racmc.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * The parameters of the new pose of the arm of the game of blocks.
 *
 * <p>The pose of the arm of the game of blocks is an enum that NeoForge lets a mod add a value
 * to. The value is added with the file {@code META-INF/enumextensions.json} of the mod, and the
 * transformer of the pose is taken out of this class.
 *
 * <p>The class holds the transformer of the pose and nothing else, so that it can be read out
 * before the game of blocks is ready.
 */
public final class Rac1BlasterArmParams {

    private Rac1BlasterArmParams() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The parameters of the new pose of the arm.
     *
     * <p>The method is called once for every parameter of the constructor of the new value of the
     * pose, with the number of the parameter and the type of the parameter. The type of the first
     * parameter is the type of a primitive, and {@link Class#cast(Object)} refuses a primitive, so
     * the value of a primitive is not cast at all.
     *
     * @param index the number of the parameter
     * @param type the type of the parameter
     * @return the value of the parameter
     */
    public static Object getArmPoseTransformer(int index, Class<?> type) {
        Object value = switch (index) {
            // The pose of the arm is the pose of one hand.
            case 0 -> Boolean.FALSE;
            case 1 -> (IArmPoseTransformer) Rac1BlasterArmParams::aimTheWeapon;
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + index);
        };
        return type.isPrimitive() ? value : type.cast(value);
    }

    /**
     * Turns the arm of the player towards the place the player looks at.
     *
     * <p>The custom arm uses the same local player-look inputs as shot authority, not the reused
     * vanilla model's render-history-dependent swimming head pitch. Vanilla still applies attack,
     * crouch and bob afterward. Only its attack head term is bridged for this custom arm; the
     * player's head and other arm are not changed.
     *
     * @param model the model of the player
     * @param entity the player
     * @param arm the arm that is turned
     */
    private static void aimTheWeapon(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)) return;
        float partial = Rac1BlasterClient.renderPartial(entity);
        var look = com.github.giannigr.racmc.item.weapon.RangedPlayerPose.look(player, partial);
        var attackArm = player.swingingArm == net.minecraft.world.InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        applyAim(model, arm, look, model.attackTime > 0 && attackArm == arm);
    }

    /** Pure renderer bridge, separately exercisable without a Minecraft singleton or entity. */
    static void applyAim(HumanoidModel<?> model, HumanoidArm arm,
                         com.github.giannigr.racmc.item.weapon.RangedPlayerPose.Look look, boolean attacked) {
        var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float pitch = look.pitch() * ((float) Math.PI / 180);
        part.xRot = pitch - (float) (Math.PI / 2);
        part.yRot = look.headYaw() * ((float) Math.PI / 180);
        part.zRot = 0.0f;
        if (attacked)
            part.xRot += com.github.giannigr.racmc.item.weapon.RangedArmPose.attackHeadTerm(model.attackTime, model.head.xRot)
                    - com.github.giannigr.racmc.item.weapon.RangedArmPose.attackHeadTerm(model.attackTime, pitch);
    }
}
