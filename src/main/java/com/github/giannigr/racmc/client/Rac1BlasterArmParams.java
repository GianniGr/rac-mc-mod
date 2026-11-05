package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.item.Rac1BlasterGrip;
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
     * <p>The head of the player is already turned in the model of the player when the pose is
     * applied, so the turn of the head around the body and the turn of the head around the side
     * axis are read out of the model itself.
     *
     * @param model the model of the player
     * @param entity the player
     * @param arm the arm that is turned
     */
    private static void aimTheWeapon(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        if (arm != HumanoidArm.RIGHT) {
            return;
        }
        Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(Math.toDegrees(model.head.yRot),
                Math.toDegrees(model.head.xRot), 0.0);
        Rac1BlasterGrip.ArmTurn turn = Rac1BlasterGrip.aim(look);
        model.rightArm.xRot = (float) Math.toRadians(turn.x());
        model.rightArm.yRot = (float) Math.toRadians(turn.y());
        model.rightArm.zRot = 0.0f;
    }
}
