package com.github.giannigr.racmc.client.actions;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.actions.WrenchActionPresentation;
import com.github.giannigr.racmc.actions.WrenchSteveRig;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.RacMcClientRegistration;
import com.github.giannigr.racmc.serverassets.Rac1WrenchData;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

/** Real skin-arm and original wrench mesh submissions; presentation never advances simulation. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class WrenchActionRendering {
    private static Object content;
    private static WrenchSteveRig rig;
    private static boolean installed;
    private static long modelEpoch = -1;
    private static final Map<Boolean, PlayerModel<AbstractClientPlayer>> HAND_MODELS = new HashMap<>();

    private WrenchActionRendering() {}

    @SubscribeEvent
    public static void initialize(ClientTickEvent.Pre event) {
        if (!installed) {
            WrenchPlayerPoseHook.install(WrenchActionRendering::pose, WrenchActionRendering::hand);
            installed = true;
        }
    }

    private static WrenchSteveRig rig() {
        var next = RacMcClient.assets().content().orElse(null);
        if (next != content) {
            content = next;
            rig = null;
            if (next != null) {
                try {
                    rig = new WrenchSteveRig(Rac1WrenchData.from(next));
                } catch (IllegalArgumentException unavailable) {
                    // Core rendering still works; original action rendering is fail-closed.
                }
            }
        }
        return rig;
    }

    private static WrenchSteveRig.Pose sample(Player player, WrenchActionPresentation view,
            double weight) {
        var current = rig();
        return current == null ? null : current.sample(view.bodySequence(), view.clipSeconds(),
                view.wrenchSequence(), view.wrenchClipSeconds(),
                player.getMainArm() == HumanoidArm.LEFT, WrenchActionClient.slim(player), weight,
                player.isCrouching());
    }

    private static WrenchPlayerPoseHook.Pose pose(Player player, WrenchActionPresentation view,
            float partialTick) {
        // The setupAnim-tail hook blends from its captured current native pose, not bind/zero.
        var pose = sample(player, view, 1);
        if (pose == null) return null;
        float nativeYaw = net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float correction = (float) Math.toRadians(Math.IEEEremainder(
                WrenchActionClient.facingYaw(player, partialTick) - nativeYaw, 360))
                * (float) view.bodyBlendWeight();
        return new WrenchPlayerPoseHook.Pose(new org.joml.Matrix4f().rotateY(correction)
                .mul(WrenchSteveRig.blendRoot(pose.root(), player.isCrouching(), view.bodyBlendWeight())),
                part(pose.rightArm()), part(pose.leftArm()), correction);
    }

    private static WrenchPlayerPoseHook.Part part(WrenchSteveRig.Part part) {
        return new WrenchPlayerPoseHook.Part(part.x(), part.y(), part.z(),
                part.xRot(), part.yRot(), part.zRot());
    }

    public static org.joml.Matrix4f itemAdjustment(Player player, WrenchActionPresentation view) {
        var pose = sample(player, view, 1);
        return pose == null ? new org.joml.Matrix4f() : pose.itemAdjustment();
    }

    private static boolean hand(RenderHandEvent event, Player actor, WrenchActionPresentation view,
            WrenchPlayerPoseHook.PoseSource source) {
        if (!(actor instanceof AbstractClientPlayer player)) return false;
        view = WrenchActionClient.presentation(player, event.getPartialTick());
        var pose = sample(player, view, view.bodyBlendWeight());
        if (pose == null) return false;
        if (event.getHand() != InteractionHand.MAIN_HAND) return true; // Both arms drawn below.
        Minecraft mc = Minecraft.getInstance();
        long epoch = com.github.giannigr.racmc.client.render.Rac1ImportedItemDrawing.epoch();
        if (modelEpoch != epoch) {
            HAND_MODELS.clear();
            modelEpoch = epoch;
        }
        boolean slim = WrenchActionClient.slim(player);
        PlayerModel<AbstractClientPlayer> model = HAND_MODELS.computeIfAbsent(slim,
                key -> new PlayerModel<>(mc.getEntityModels().bakeLayer(
                        key ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), key));
        apply(model.rightArm, pose.rightArm());
        apply(model.leftArm, pose.leftArm());
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        float yawCorrection = (float) Math.toRadians(Math.IEEEremainder(
                WrenchActionClient.facingYaw(player, event.getPartialTick()) - player.getYRot(), 360))
                * (float) view.bodyBlendWeight();
        var facing = new org.joml.Matrix4f().rotateY(yawCorrection);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        try {
            // Viewmodel-only offset: world contact still uses WrenchSteveRig.world, never this.
            poses.translate(0, -0.2, -0.35);
            poses.scale(-0.9375F, -0.9375F, 0.9375F);
            poses.pushPose();
            try {
                poses.mulPose(facing);
                poses.mulPose(pose.root());
                var skin = event.getMultiBufferSource().getBuffer(
                        RenderType.entitySolid(player.getSkin().texture()));
                model.rightArm.render(poses, skin, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
                model.leftArm.render(poses, skin, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
                var wear = event.getMultiBufferSource().getBuffer(
                        RenderType.entityTranslucent(player.getSkin().texture()));
                if (player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE)) {
                    model.rightSleeve.render(poses, wear, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
                }
                if (player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE)) {
                    model.leftSleeve.render(poses, wear, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
                }
            } finally {
                poses.popPose();
            }
            if (view.heldVisible()) {
                poses.mulPose(facing);
                poses.mulPose(pose.socket());
                RacMcClientRegistration.wrenchRenderer().renderAction(poses,
                        event.getMultiBufferSource(), event.getPackedLight(), view, false);
            }
        } finally {
            poses.popPose();
        }
        return true;
    }

    private static void apply(net.minecraft.client.model.geom.ModelPart target,
            WrenchSteveRig.Part value) {
        target.setPos(value.x(), value.y(), value.z());
        target.setRotation(value.xRot(), value.yRot(), value.zRot());
    }

    @SubscribeEvent
    public static void detached(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || rig() == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        PoseStack poses = event.getPoseStack();
        for (var player : mc.level.players()) {
            var view = WrenchActionClient.presentation(player, partial);
            if (view.heldVisible()) continue;
            var position = view.detachedPosition();
            poses.pushPose();
            try {
                poses.translate(position.x() - camera.x, position.y() - camera.y,
                        position.z() - camera.z);
                var q = view.detachedRotation();
                poses.mulPose(new Quaternionf(q.x(), q.y(), q.z(), q.w()));
                poses.scale(0.9375F * player.getScale(), 0.9375F * player.getScale(),
                        0.9375F * player.getScale());
                poses.mulPose(new Quaternionf().rotationY((float) view.localSpinRadians()));
                int light = LevelRenderer.getLightColor(mc.level,
                        BlockPos.containing(position.x(), position.y(), position.z()));
                RacMcClientRegistration.wrenchRenderer().renderAction(poses, buffers, light, view, true);
            } finally {
                poses.popPose();
            }
        }
    }
}
