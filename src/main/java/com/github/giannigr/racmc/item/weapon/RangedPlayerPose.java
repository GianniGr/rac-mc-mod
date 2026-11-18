package com.github.giannigr.racmc.item.weapon;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/** Common tick/render-time adapter. Ordinary adult aim includes crouch and vanilla arm bob.
 * Server does not have client skin geometry; slim is an explicit presentation input.
 * Swimming/riding/fall-flight renderer root rotations are not fabricated here.
 */
public final class RangedPlayerPose {
    private RangedPlayerPose() {}
    public static HandPoseSnapshot of(Player player, float partial, boolean slim) {
        double body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        double head = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        double pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        var pose = HandPoseSnapshot.standing(new Vector(
                Mth.lerp(partial, player.xo, player.getX()), Mth.lerp(partial, player.yo, player.getY()),
                Mth.lerp(partial, player.zo, player.getZ())), body,
                Mth.wrapDegrees((float) (head - body)), pitch, left, slim, player.getScale());
        double age = player.tickCount + partial, sign = left ? -1 : 1;
        // HumanoidModel.bobModelPart, applied after the custom arm pose.
        double x = pose.armRotation().x() + Math.toDegrees(sign * Math.sin(age * .067) * .05);
        double z = Math.toDegrees(sign * (Math.cos(age * .09) * .05 + .05));
        boolean crouch = player.isCrouching();
        if (crouch) x += Math.toDegrees(.4);
        var position = pose.position();
        if (crouch) position = new Vector(position.x(), position.y() - player.getScale() * .125, position.z());
        return new HandPoseSnapshot(position, body, pose.entityScale(), left,
                new Vector(pose.pivot().x(), crouch ? 5.2 : 2, 0),
                new Vector(x, pose.armRotation().y(), z), pose.armScale(), .9375, 1.501);
    }
}
