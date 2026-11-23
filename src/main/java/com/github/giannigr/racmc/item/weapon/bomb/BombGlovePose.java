package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.item.RacMcItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared world-space release socket, independent of display/camera projection. */
public final class BombGlovePose {
    public static final int WINDUP_TICKS = 6;
    public static final int USE_DURATION = 72000;

    private BombGlovePose() {}

    public static boolean windingUp(Player player) {
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().is(RacMcItems.BOMB_GLOVE.get())
                && player.getTicksUsingItem() < WINDUP_TICKS;
    }

    public static Vec3 palmRoot(Player player) {
        return palmRoot(player.position(), player.getEyeHeight());
    }

    public static Vec3 palm(Player player) {
        return palm(player.position(), player.getEyeHeight(), player.getYRot(), player.getMainArm());
    }

    static Vec3 palmRoot(Vec3 feet, double eyeHeight) {
        return feet.add(0, Math.max(.25, eyeHeight - .38), 0);
    }

    public static Vec3 palm(Vec3 feet, double eyeHeight, float yaw, HumanoidArm side) {
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return palmRoot(feet, eyeHeight).add(forward.scale(.64))
                .add(right.scale(side == HumanoidArm.RIGHT ? .33 : -.33));
    }
}
