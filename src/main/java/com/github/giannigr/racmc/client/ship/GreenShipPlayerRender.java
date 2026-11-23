package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.ship.Rac1TravelShip;
import com.github.giannigr.racmc.ship.ShipCockpitProfile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Consumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Third-person native player presentation; all external state is read-only. */
public final class GreenShipPlayerRender {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private GreenShipPlayerRender() {}

    public static void wrap(LivingEntityRenderer<?, ?> renderer, LivingEntity actor, float yaw,
                            float partialTick, PoseStack poses, MultiBufferSource buffers,
                            int light, Operation<Void> vanilla) {
        render(actor, renderer.getModel(), partialTick, poses,
                stack -> vanilla.call(actor, yaw, partialTick, stack, buffers, light));
    }

    public static void render(LivingEntity actor, Object model, float partialTick, PoseStack parent,
                              Consumer<PoseStack> vanilla) {
        if (!(actor instanceof AbstractClientPlayer player) || !(model instanceof PlayerModel<?> steve)
                || !(actor.getVehicle() instanceof Rac1TravelShip ship)
                || ship.isRemoved() || ship.getFirstPassenger() != actor || !actor.isAlive()
                || actor.isSpectator()) {
            isolatedPassthrough(parent, vanilla);
            return;
        }
        var snapshot = ship.accessSnapshot();
        if (ship.hasRefitCockpit()) {
            var routed = GreenShipAccessVisual.routed(snapshot, ship.accessPath(), ship.accessPathProgress());
            if (routed == null) {
                isolatedPassthrough(parent, vanilla);
                return;
            }
            float yaw = Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot());
            float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            // Native passenger interpolation already supplies feet; applying a second path offset snaps the rider.
            renderScoped(player, steve, routed, parent, Vec3.ZERO, Mth.wrapDegrees(bodyYaw - yaw), true, vanilla);
            return;
        }
        long now = ship.level().getGameTime();
        var sill = ShipCockpitProfile.CURRENT.sillLocal();
        var sample = GreenShipAccessVisual.sample(snapshot, now, partialTick, sill);
        if (sample == null) {
            isolatedPassthrough(parent, vanilla);
            return;
        }
        float yaw = Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot());
        var target = ship.getPosition(partialTick).add(sample.base().yRot(-yaw * Mth.DEG_TO_RAD));
        var offset = target.subtract(player.getPosition(partialTick));
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float desiredYaw = GreenShipAccessVisual.facing(snapshot, now, partialTick, sill, yaw);
        renderScoped(player, steve, sample.angles(), parent, offset, Mth.wrapDegrees(bodyYaw - desiredYaw), vanilla);
    }

    private static void isolatedPassthrough(PoseStack parent, Consumer<PoseStack> vanilla) {
        Scope previous = ACTIVE.get();
        if (previous == null) {
            vanilla.accept(parent);
            return;
        }
        ACTIVE.remove();
        try {
            vanilla.accept(parent);
        } finally {
            ACTIVE.set(previous);
        }
    }

    /**
     * Give the native player draw a private stack. An exception, cancellation or mismatched
     * push/pop inside another renderer cannot leave the caller's matrices changed.
     */
    static void renderScoped(Object actor, PlayerModel<?> model, GreenShipPilotPose.Angles angles,
                             PoseStack parent, Vec3 offset, float yawCorrection,
                             Consumer<PoseStack> vanilla) {
        renderScoped(actor, model, angles, parent, offset, yawCorrection, false, vanilla);
    }

    /**
     * Refitted cockpit contact assumes the authored limb pitch without vanilla riding yaw/roll.
     * Never change head/hat rotations or affect the first-person camera.
     */
    static void renderRefitSeat(Object actor, PlayerModel<?> model, PoseStack parent, Vec3 offset,
                                float yawCorrection, Consumer<PoseStack> vanilla) {
        renderScoped(actor, model, GreenShipCockpitInterior.POSE, parent, offset, yawCorrection, true, vanilla);
    }

    private static void renderScoped(Object actor, PlayerModel<?> model, GreenShipPilotPose.Angles angles,
                                     PoseStack parent, Vec3 offset, float yawCorrection,
                                     boolean squareLimbs, Consumer<PoseStack> vanilla) {
        PoseStack local = new PoseStack();
        local.mulPose(parent.last().pose());
        local.last().normal().set(parent.last().normal());
        local.translate(offset.x, offset.y, offset.z);
        local.mulPose(Axis.YP.rotationDegrees(yawCorrection));
        Scope previous = ACTIVE.get();
        var current = new Scope(actor, model, angles, squareLimbs);
        ACTIVE.set(current);
        try {
            vanilla.accept(local);
        } finally {
            current.restore();
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    /** Runs after vanilla and PlayerModel setup hooks, before the skin and wearable layers draw. */
    public static void afterSetup(Object actor, Object model) {
        Scope scope = ACTIVE.get();
        if (scope != null && scope.actor == actor && scope.model == model) {
            scope.apply();
        }
    }

    private static final class Scope {
        private final Object actor;
        private final PlayerModel<?> model;
        private final ModelPart[] parts;
        private final double[] target;
        private final float[] before;
        private final boolean squareLimbs;
        private boolean applied;

        private Scope(Object actor, PlayerModel<?> model, GreenShipPilotPose.Angles pose, boolean squareLimbs) {
            this.actor = actor;
            this.model = model;
            parts = new ModelPart[] {model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
                    model.jacket, model.rightSleeve, model.leftSleeve, model.rightPants, model.leftPants};
            target = new double[] {pose.torso(), pose.rightArm(), pose.leftArm(), pose.rightLeg(), pose.leftLeg()};
            this.squareLimbs = squareLimbs;
            before = new float[parts.length * 3];
            if (!squareLimbs) remember();
        }

        private void apply() {
            if (applied) return;
            applied = true;
            if (squareLimbs) remember(); // Keep the native setup result, including riding splay.
            for (int i = 0; i < parts.length; i++) {
                var part = parts[i];
                part.xRot = (float) Math.toRadians(target[i % target.length]);
                if (squareLimbs) {
                    part.yRot = 0;
                    part.zRot = 0;
                }
            }
        }

        private void remember() {
            for (int i = 0; i < parts.length; i++) {
                before[i * 3] = parts[i].xRot;
                before[i * 3 + 1] = parts[i].yRot;
                before[i * 3 + 2] = parts[i].zRot;
            }
        }

        private void restore() {
            if (applied) {
                for (int i = 0; i < parts.length; i++) {
                    parts[i].xRot = before[i * 3];
                    if (squareLimbs) {
                        parts[i].yRot = before[i * 3 + 1];
                        parts[i].zRot = before[i * 3 + 2];
                    }
                }
            }
        }
    }
}
