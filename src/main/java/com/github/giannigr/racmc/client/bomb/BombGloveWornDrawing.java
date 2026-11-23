package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.client.render.Rac1ImportedItemDrawing;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveClientTracking;
import com.github.giannigr.racmc.item.weapon.bomb.BombGlovePose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Steve adaptation. Local first-person is a world pass, so it shares the flight projection. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class BombGloveWornDrawing {
    private static final float PIXEL = 1f / 16;
    private static final WearTextures LEASES = new WearTextures();
    private static final Map<Boolean, PlayerModel<AbstractClientPlayer>> FIRST_MODELS = new HashMap<>();
    private static long firstEpoch = -1;
    private static Rac1AnimatedAsset fitOwner;
    private static Bounds fitBounds;

    private BombGloveWornDrawing() {}

    private static final class WearTextures {
        final com.github.giannigr.racmc.client.render.Rac1ItemTextureLease<ResourceLocation> glove =
                Rac1ImportedItemDrawing.textures("bomb_glove_worn");
        final com.github.giannigr.racmc.client.render.Rac1ItemTextureLease<ResourceLocation> held =
                Rac1ImportedItemDrawing.textures("bomb_glove_palm");
    }

    public static void clearTextures() {
        LEASES.glove.clear();
        LEASES.held.clear();
        fitOwner = null;
        fitBounds = null;
    }

    public record Snapshot(Vec3 feet, Vec3 palm, Quaternionf armRotation, boolean right) {}

    static Snapshot sample(Vec3 feet, double eyeHeight, float yaw, boolean right) {
        var side = right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        var palm = BombGlovePose.palm(feet, eyeHeight, yaw, side);
        var forward = Vec3.directionFromRotation(0, yaw);
        double sign = right ? 1 : -1;
        var shoulder = feet.add(0, eyeHeight - .245, 0)
                .add(-forward.z * .3125 * sign, 0, forward.x * .3125 * sign);
        var direction = palm.subtract(shoulder).normalize();
        var rotation = new Quaternionf().rotateTo(0, 1, 0,
                (float) direction.x, (float) direction.y, (float) direction.z);
        return new Snapshot(feet, palm, rotation, right);
    }

    private static Snapshot sample(AbstractClientPlayer player, float partial) {
        float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
        return sample(player.getPosition(partial), player.getEyeHeight(), yaw, player.getMainArm() == HumanoidArm.RIGHT);
    }

    public static boolean eligible(AbstractClientPlayer p) {
        return p.getMainHandItem().is(RacMcItems.BOMB_GLOVE.get()) && p.isAlive() && !p.isSpectator()
                && !p.isInvisible() && !p.isSleeping();
    }

    public record Prepared(ModelPart part, ModelPart sleeve, BombGloveArmMask.UpperArm upper,
                           BombGloveArmMask.UpperArm outer, Rac1AnimatedAsset art, Bounds bounds,
                           BombGloveCuff.ArmFit fit, Snapshot socket, float cuff) {}

    public static Prepared prepare(PlayerModel<?> model, AbstractClientPlayer player, float partial) {
        var art = Rac1EffectClient.bombGlove();
        if (!eligible(player) || art == null) return null;
        if (fitOwner != art) {
            fitOwner = art;
            fitBounds = Bounds.of(art.mesh().positions());
        }
        if (fitBounds == null) return null;
        var socket = sample(player, partial);
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        var fit = slim ? (socket.right ? BombGloveCuff.ArmFit.SLIM_RIGHT : BombGloveCuff.ArmFit.SLIM_LEFT)
                : (socket.right ? BombGloveCuff.ArmFit.STANDARD_RIGHT : BombGloveCuff.ArmFit.STANDARD_LEFT);
        ModelPart arm = socket.right ? model.rightArm : model.leftArm;
        ModelPart sleeve = socket.right ? model.rightSleeve : model.leftSleeve;
        // Steve fit uses measured source dimensions and the *inflated* vanilla sleeve cross section.
        float cuff = fitBounds.cuff(fit.width);
        var upper = BombGloveArmMask.prepare(arm, fit, false, cuff);
        var outer = BombGloveArmMask.prepare(sleeve, fit, true, cuff);
        if (upper.isEmpty() || outer.isEmpty() || upper.get().faces().isEmpty()) return null;
        return new Prepared(arm, sleeve, upper.get(), outer.get(), art, fitBounds, fit, socket, cuff);
    }

    /** Draw inputs are entity-relative, not a camera-offset palm. */
    public static void draw(Prepared p, PoseStack poses, MultiBufferSource buffers,
                            VertexConsumer skin, VertexConsumer sleeve, int light, int overlay, int color) {
        var palm = p.socket.palm.subtract(p.socket.feet);
        poses.pushPose();
        try {
            poses.translate(palm.x, palm.y, palm.z);
            poses.mulPose(p.socket.armRotation);
            poses.translate(-p.fit.centerX() * PIXEL, -10 * PIXEL, 0);
            p.upper.drawLocal(poses, skin, light, overlay, color);
            if (sleeve != null) p.outer.drawLocal(poses, sleeve, light, overlay, color);
            var bounds = p.bounds;
            var asset = p.art;
            // Source long axis is imported +Z (cuff end). In the Steve arm frame +Y goes
            // toward the hand. Fit the two cross sections independently; mirror X only on left.
            poses.translate(p.fit.centerX() * PIXEL, p.cuff * PIXEL, 0);
            poses.scale((p.socket.right ? 1 : -1) * (p.fit.width + .5f) * PIXEL / bounds.width(),
                    (10 - p.cuff) * PIXEL / bounds.length(), 4.5f * PIXEL / bounds.depth());
            poses.mulPose(Axis.XP.rotationDegrees(90));
            poses.translate(-bounds.centerX(), -bounds.centerY(), -bounds.maxZ);
            var textures = LEASES.glove.get(asset, Rac1ImportedItemDrawing.epoch(), asset.textures())
                    .toArray(ResourceLocation[]::new);
            var mesh = asset.mesh();
            Rac1MeshRenderer.render(poses, buffers, mesh, mesh.positions(), mesh.normals(), textures, light, false);
        } finally {
            poses.popPose();
        }
    }

    @SubscribeEvent
    public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean first = mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == mc.player;
        if (first && !mc.options.hideGui && eligible(mc.player)) {
            drawFirst(mc.player, partial, camera, poses, buffers);
        }
        var bomb = Rac1EffectClient.bomb();
        if (bomb == null || Rac1EffectClient.bombGlove() == null) return;
        var texture = LEASES.held.get(bomb, Rac1ImportedItemDrawing.epoch(),
                bomb.textures().toArray(Rac1Texture[]::new)).toArray(ResourceLocation[]::new);
        for (var player : mc.level.players()) {
            if (!eligible(player) || (first && player == mc.player && mc.options.hideGui)) continue;
            var held = BombGloveClientTracking.heldBomb(player);
            if (held == null || event.getCamera().getPosition().distanceToSqr(player.position()) > 64 * 64) continue;
            var socket = sample(player, partial);
            var relative = socket.palm.subtract(camera);
            poses.pushPose();
            try {
                poses.translate(relative.x, relative.y, relative.z);
                Rac1MeshRenderer.render(poses, buffers, bomb.mesh(), texture, light(player, socket.palm), false);
            } finally {
                poses.popPose();
            }
        }
    }

    private static void drawFirst(AbstractClientPlayer player, float partial, Vec3 camera,
                                  PoseStack poses, MultiBufferSource buffers) {
        long epoch = Rac1ImportedItemDrawing.epoch();
        if (firstEpoch != epoch) {
            FIRST_MODELS.clear();
            firstEpoch = epoch;
        }
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        var model = FIRST_MODELS.computeIfAbsent(slim, s -> new PlayerModel<>(
                Minecraft.getInstance().getEntityModels().bakeLayer(s ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), s));
        var prep = prepare(model, player, partial);
        if (prep == null) return;
        var relative = prep.socket.feet.subtract(camera);
        var skinId = player.getSkin().texture();
        poses.pushPose();
        try {
            poses.translate(relative.x, relative.y, relative.z);
            var skin = buffers.getBuffer(RenderType.entityTranslucent(skinId));
            boolean sleeveOn = player.isModelPartShown(prep.socket.right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
            // Both top layers use the same entity render type to avoid invalidating a queued consumer.
            var sleeve = sleeveOn ? skin : null;
            draw(prep, poses, buffers, skin, sleeve, light(player, prep.socket.palm), OverlayTexture.NO_OVERLAY, -1);
        } finally {
            poses.popPose();
        }
    }

    private static int light(AbstractClientPlayer player, Vec3 at) {
        return LevelRenderer.getLightColor(player.level(), BlockPos.containing(at));
    }

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float width() { return maxX - minX; }
        float depth() { return maxY - minY; }
        float length() { return maxZ - minZ; }
        float centerX() { return (minX + maxX) / 2; }
        float centerY() { return (minY + maxY) / 2; }

        float cuff(int armWidth) {
            return 10 - Math.clamp(length() * Math.max((armWidth + .5f) / width(), 4.5f / depth()), 4, 8);
        }

        static Bounds of(float[] points) {
            var box = com.github.giannigr.racmc.client.Rac1ImportedItemPose.bounds(points);
            if (box == null) return null;
            var b = new Bounds(box[0], box[1], box[2], box[3], box[4], box[5]);
            return b.width() > 0 && b.depth() > 0 && b.length() > 0 ? b : null;
        }
    }

    /** Align the existing model coordinate frame to the renderer's saved entity-relative frame. */
    public static void align(PoseStack poses, Matrix4f entityFrame) {
        poses.mulPose(new Matrix4f(poses.last().pose()).invert().mul(entityFrame));
    }

    /** Give unchanged native armor layers the same posing of this active arm. */
    public static void alignArmor(Prepared p, PoseStack poses, Matrix4f entityFrame) {
        var palm = p.socket.palm.subtract(p.socket.feet);
        var arm = new Matrix4f(poses.last().pose()).invert().mul(entityFrame)
                .translate((float) palm.x, (float) palm.y, (float) palm.z)
                .rotate(p.socket.armRotation)
                .translate(-p.fit.centerX() * PIXEL, -10 * PIXEL, 0);
        var pivot = arm.getTranslation(new Vector3f()).mul(16);
        var angles = arm.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());
        p.part.setPos(pivot.x, pivot.y, pivot.z);
        p.part.setRotation(angles.x, angles.y, angles.z);
    }
}
