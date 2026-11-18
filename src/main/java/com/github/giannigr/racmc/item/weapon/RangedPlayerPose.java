package com.github.giannigr.racmc.item.weapon;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/** MC1.21.1/NeoForge21.1.265 resolved firing pose. All numbers are derived locally from player
 * state; no client-supplied coordinates. Server evaluates the accepted-shot tick boundary,
 * client supplies its locally interpolated velocity and the agreed skin-model enum.
 */
public final class RangedPlayerPose {
    private RangedPlayerPose() {}
    public record Look(float bodyYaw, float headYaw, float pitch) {}

    /** LivingEntityRenderer59..88: passenger body/head clamp precedes upside-down head mapping. */
    public static Look look(Player player, float partial) {
        float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float head = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float relative = head - body;
        if (player.isPassenger() && player.getVehicle() != null && player.getVehicle().shouldRiderSit()
                && player.getVehicle() instanceof LivingEntity vehicle) {
            body = Mth.rotLerp(partial, vehicle.yBodyRotO, vehicle.yBodyRot);
            float clamped = Mth.clamp(Mth.wrapDegrees(head - body), -85, 85);
            body = head - clamped;
            if (clamped * clamped > 2500) body += clamped * .2f;
            relative = head - body;
        }
        float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        if (upsideDown(player)) { pitch = -pitch; relative = -relative; }
        return new Look(body, Mth.wrapDegrees(relative), pitch);
    }

    public static HandPoseSnapshot of(Player player, float partial, boolean slim) {
        return of(player, partial, slim, player.getDeltaMovement());
    }

    public static HandPoseSnapshot of(Player player, float partial, boolean slim, Vec3 velocity) {
        var look = look(player, partial);
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        var pose = HandPoseSnapshot.standing(new Vector(
                Mth.lerp(partial, player.xo, player.getX()), Mth.lerp(partial, player.yo, player.getY()),
                Mth.lerp(partial, player.zo, player.getZ())), look.bodyYaw(),
                look.headYaw(), look.pitch(), left, slim, player.getScale());
        boolean crouch = player.isCrouching();
        boolean attackLeft = player.swingingArm == net.minecraft.world.InteractionHand.MAIN_HAND
                ? left : !left;
        var arm = RangedArmPose.resolve(look.headYaw() * ((float) Math.PI / 180),
                look.pitch() * ((float) Math.PI / 180), left, attackLeft,
                player.getAttackAnim(partial), player.tickCount + partial, crouch, slim);
        var position = pose.position();
        if (crouch) position = new Vector(position.x(), position.y() - player.getScale() * .125, position.z());
        if (player.hasPose(Pose.SLEEPING) && player.getBedOrientation() != null) {
            var direction = player.getBedOrientation();
            double offset = player.getEyeHeight(Pose.STANDING) - .1f;
            position = new Vector(position.x() - direction.getStepX() * offset, position.y(),
                    position.z() - direction.getStepZ() * offset);
        }
        return new HandPoseSnapshot(position, look.bodyYaw(), pose.entityScale(), left,
                arm.pivot(), arm.degrees(), pose.armScale(), .9375, 1.501,
                root(player, partial, look.bodyYaw(), velocity));
    }

    /** LivingEntityRenderer181..210 followed by PlayerRenderer220..250, in stack order. */
    public static HandRoot root(Player player, float partial, float resolvedBodyYaw, Vec3 velocity) {
        var steps = new java.util.ArrayList<HandRoot.Step>();
        float body = resolvedBodyYaw;
        if (player.isFullyFrozen()) body += (float) (Math.cos(player.tickCount * 3.25) * Math.PI * .4f);
        if (!player.hasPose(Pose.SLEEPING)) steps.add(HandRoot.Step.y(180 - body));
        if (player.deathTime > 0) {
            float progress = Mth.sqrt((player.deathTime + partial - 1) / 20f * 1.6f);
            steps.add(HandRoot.Step.z(Math.min(1, progress) * 90));
        } else if (player.isAutoSpinAttack()) {
            steps.add(HandRoot.Step.x(-90 - player.getXRot()));
            steps.add(HandRoot.Step.y((player.tickCount + partial) * -75));
        } else if (player.hasPose(Pose.SLEEPING)) {
            var direction = player.getBedOrientation();
            float sleepingYaw = direction == null ? body : switch (direction) {
                case SOUTH -> 90; case WEST -> 0; case NORTH -> 270; case EAST -> 180; default -> 0;
            };
            steps.add(HandRoot.Step.y(sleepingYaw));
            steps.add(HandRoot.Step.z(90));
            steps.add(HandRoot.Step.y(270));
        } else if (upsideDown(player)) {
            steps.add(HandRoot.Step.translate(0, (player.getBbHeight() + .1f) / player.getScale(), 0));
            steps.add(HandRoot.Step.z(180));
        }
        if (player.isFallFlying()) {
            float ticks = player.getFallFlyingTicks() + partial;
            if (!player.isAutoSpinAttack())
                steps.add(HandRoot.Step.x(Mth.clamp(ticks * ticks / 100, 0, 1) * (-90 - player.getViewXRot(partial))));
            var view = player.getViewVector(partial);
            double a = velocity.horizontalDistanceSqr(), b = view.horizontalDistanceSqr();
            if (a > 0 && b > 0) {
                double dot = (velocity.x * view.x + velocity.z * view.z) / Math.sqrt(a * b);
                double cross = velocity.x * view.z - velocity.z * view.x;
                steps.add(HandRoot.Step.y(Math.toDegrees((float) (Math.signum(cross) * Math.acos(Math.clamp(dot, -1, 1))))));
            }
        } else if (player.getSwimAmount(partial) > 0) {
            boolean fluid = player.isInWater() || player.isInFluidType((type, height) -> player.canSwimInFluidType(type));
            steps.add(HandRoot.Step.x(player.getSwimAmount(partial) * (fluid ? -90 - player.getXRot() : -90)));
            if (player.isVisuallySwimming()) steps.add(HandRoot.Step.translate(0, -1, .3));
        }
        return new HandRoot(steps);
    }

    private static boolean upsideDown(Player player) {
        String name = net.minecraft.ChatFormatting.stripFormatting(player.getName().getString());
        return ("Dinnerbone".equals(name) || "Grumm".equals(name)) && player.isModelPartShown(PlayerModelPart.CAPE);
    }
}
