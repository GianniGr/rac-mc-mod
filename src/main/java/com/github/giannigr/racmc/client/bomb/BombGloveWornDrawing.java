package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.effect.EffectMeshAsset;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.client.render.Rac1ImportedItemDrawing;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveClientTracking;
import com.github.giannigr.racmc.item.weapon.bomb.BombGlovePose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

/** A camera-local first-person rig submitted under the same projection as world flight. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class BombGloveWornDrawing {
    private static final WearTextures LEASES = new WearTextures();
    private static final Map<Boolean, PlayerModel<AbstractClientPlayer>> FIRST_MODELS = new HashMap<>();
    private static long firstEpoch = -1;
    private static Rac1AnimatedAsset fitOwner;
    private static Bounds fitBounds;
    private static Rac1Mesh rightMesh;
    private static Rac1Mesh leftMesh;
    private static EffectMeshAsset radiusOwner;
    private static float shellClearance;

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
        rightMesh = null;
        leftMesh = null;
        radiusOwner = null;
        shellClearance = 0;
    }

    public record Snapshot(Vec3 feet, Vec3 palm, Quaternionf armRotation, boolean right) {}

    static Snapshot sample(Vec3 feet, double eyeHeight, float yaw, boolean right) {
        return sample(feet, eyeHeight, yaw, right, new BombGloveStevePose.Motion(0, 0, 0));
    }

    static Snapshot sample(Vec3 feet, double eyeHeight, float yaw, boolean right, BombGloveStevePose.Motion motion) {
        var side = right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        var forward = Vec3.directionFromRotation(0, yaw);
        var bodyRight = new Vec3(-forward.z, 0, forward.x);
        var release = BombGlovePose.palm(feet, eyeHeight, yaw, side);
        double sign = right ? 1 : -1;
        var shoulder = feet.add(0, eyeHeight - .245, 0)
                .add(bodyRight.scale(.3125 * sign));
        // Keep native low carry and the original gameplay socket. Apply the
        // standing throw's low outside sweep and rising inward follow-through.
        var rest = shoulder.add(forward.scale(.065)).add(0, -.72, 0);
        var path = BombGloveStevePose.castOffset(motion);
        var palm = (motion.toRelease() == 1 ? release : rest.lerp(release, motion.toRelease()))
                .add(forward.scale(path.z)).add(bodyRight.scale(sign * path.x)).add(0, path.y, 0);
        // Blend from actual vanilla wrist roll at the endpoints; don't impose
        // the cast wrist orientation at idle.
        var rotation = BombGloveStevePose.roll(BombGloveStevePose.frame(palm.subtract(shoulder), bodyRight), right, motion);
        return new Snapshot(feet, palm, rotation, right);
    }

    private static BombGloveStevePose.Motion motion(AbstractClientPlayer player, float partial) {
        return BombGloveStevePose.motion(BombGloveClientTracking.visualPhase(player),
                player.level().getGameTime(), partial);
    }

    public static boolean eligible(AbstractClientPlayer p) {
        return p.getMainHandItem().is(RacMcItems.BOMB_GLOVE.get()) && p.isAlive() && !p.isSpectator()
                && !p.isInvisible() && !p.isSleeping();
    }

    public record Prepared(ModelPart part, ModelPart sleeve, BombGloveArmMask.UpperArm upper,
                           BombGloveArmMask.UpperArm outer, Rac1AnimatedAsset art, Bounds bounds,
                           BombGloveCuff.ArmFit fit, Snapshot socket, float cuff, float clearance,
                           boolean nativeIdle) {}

    public static Prepared prepare(PlayerModel<?> model, AbstractClientPlayer player, float partial,
                                   PoseStack poses, Matrix4f entityFrame) {
        var motion = motion(player, partial);
        float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
        var target = sample(player.getPosition(partial), player.getEyeHeight(), yaw,
                player.getMainArm() == HumanoidArm.RIGHT, motion);
        var prepared = prepare(model, player, target, motion.idle(), false);
        if (prepared == null || motion.idle() || motion.stance() >= .25) return prepared;
        poses.pushPose();
        try {
            prepared.part.translateAndRotate(poses);
            var nativeArm = new Matrix4f(entityFrame).invert().mul(poses.last().pose());
            var socket = blendCarry(target, nativeArm, prepared.fit.centerX(), prepared.clearance, motion.stance());
            return new Prepared(prepared.part, prepared.sleeve, prepared.upper, prepared.outer,
                    prepared.art, prepared.bounds, prepared.fit, socket, prepared.cuff, prepared.clearance, false);
        } finally {
            poses.popPose();
        }
    }

    static Snapshot blendCarry(Snapshot target, Matrix4f nativeArm, float centerX, float clearance, double stance) {
        if (stance >= .25) return target;
        var grip = nativeArm.transformPosition(BombGloveStevePose.armOrigin(centerX, clearance).negate());
        var rest = target.feet.add(grip.x, grip.y, grip.z);
        float t = (float) (4 * stance);
        float weight = t * t * (3 - 2 * t);
        var rotation = nativeArm.getUnnormalizedRotation(new Quaternionf()).normalize()
                .slerp(target.armRotation, weight);
        return new Snapshot(target.feet, rest.lerp(target.palm, weight), rotation, target.right);
    }

    private static Prepared prepare(PlayerModel<?> model, AbstractClientPlayer player, Snapshot socket,
                                    boolean nativeIdle, boolean firstPerson) {
        var art = Rac1EffectClient.bombGlove();
        if (!eligible(player) || art == null) return null;
        if (fitOwner != art) {
            fitOwner = art;
            fitBounds = Bounds.of(art.mesh().positions());
            rightMesh = BombGloveStevePose.mesh(art.mesh(), true);
            leftMesh = BombGloveStevePose.mesh(art.mesh(), false);
        }
        if (fitBounds == null) return null;
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        var fit = slim ? (socket.right ? BombGloveCuff.ArmFit.SLIM_RIGHT : BombGloveCuff.ArmFit.SLIM_LEFT)
                : (socket.right ? BombGloveCuff.ArmFit.STANDARD_RIGHT : BombGloveCuff.ArmFit.STANDARD_LEFT);
        ModelPart arm = socket.right ? model.rightArm : model.leftArm;
        ModelPart sleeve = socket.right ? model.rightSleeve : model.leftSleeve;
        // Steve fit uses measured source dimensions and the *inflated* vanilla sleeve cross section.
        float cuff = fitBounds.cuff(fit.width);
        var upper = BombGloveArmMask.prepare(arm, fit, false, cuff, firstPerson);
        var outer = BombGloveArmMask.prepare(sleeve, fit, true, cuff, firstPerson);
        if (upper.isEmpty() || outer.isEmpty() || upper.get().faces().isEmpty()) return null;
        var bomb = Rac1EffectClient.bomb();
        if (bomb != radiusOwner) {
            radiusOwner = bomb;
            shellClearance = bomb == null ? 0 : BombGloveStevePose.shellClearance(bomb.mesh().positions());
        }
        return new Prepared(arm, sleeve, upper.get(), outer.get(), art, fitBounds, fit, socket, cuff,
                shellClearance, nativeIdle);
    }

    /** Draw inputs are entity-relative, not a camera-offset palm. */
    public static void draw(Prepared p, PoseStack poses, MultiBufferSource buffers,
                            VertexConsumer skin, VertexConsumer sleeve, int light, int overlay, int color) {
        var palm = p.socket.palm.subtract(p.socket.feet);
        poses.pushPose();
        try {
            poses.translate(palm.x, palm.y, palm.z);
            poses.mulPose(p.socket.armRotation);
            var armOrigin = BombGloveStevePose.armOrigin(p.fit.centerX(), p.clearance);
            poses.translate(armOrigin.x, armOrigin.y, armOrigin.z);
            drawInArm(p, poses, buffers, skin, sleeve, light, overlay, color);
        } finally {
            poses.popPose();
        }
    }

    private static void drawInArm(Prepared p, PoseStack poses, MultiBufferSource buffers,
                                  VertexConsumer skin, VertexConsumer sleeve, int light, int overlay, int color) {
        p.upper.drawLocal(poses, skin, light, overlay, color);
        if (sleeve != null) p.outer.drawLocal(poses, sleeve, light, overlay, color);
        poses.pushPose();
        try {
            poses.mulPose(BombGloveStevePose.fittedShell(p.bounds, p.fit, p.cuff, p.socket.right));
            var textures = LEASES.glove.get(p.art, Rac1ImportedItemDrawing.epoch(), p.art.textures())
                    .toArray(ResourceLocation[]::new);
            Rac1MeshRenderer.render(poses, buffers, p.socket.right ? rightMesh : leftMesh, textures, light, false);
        } finally {
            poses.popPose();
        }
    }

    public static void drawNativeCarry(Prepared p, PoseStack poses, MultiBufferSource buffers,
                                      VertexConsumer skin, int light, int overlay, int color, boolean held) {
        poses.pushPose();
        try {
            p.part.translateAndRotate(poses);
            drawInArm(p, poses, buffers, skin, skin, light, overlay, color);
            if (held) {
                var origin = BombGloveStevePose.armOrigin(p.fit.centerX(), p.clearance);
                drawHeld(poses, buffers, new Vec3(-origin.x, -origin.y, -origin.z), light);
            }
        } finally {
            poses.popPose();
        }
    }

    public static void drawFromSharedSocket(Prepared p, PoseStack poses, MultiBufferSource buffers, int light) {
        drawHeld(poses, buffers, p.socket.palm.subtract(p.socket.feet), light);
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
            drawFirst(mc.player, partial, camera, event.getCamera().rotation(),
                    BombGloveFirstPersonFrame.Lens.of(event.getProjectionMatrix()), poses, buffers);
        }
        // Third-person held art is submitted by the scoped player draw with that arm.
    }

    private static void drawFirst(AbstractClientPlayer player, float partial, Vec3 camera,
                                  Quaternionf cameraToWorld, BombGloveFirstPersonFrame.Lens lens,
                                  PoseStack poses, MultiBufferSource buffers) {
        long epoch = Rac1ImportedItemDrawing.epoch();
        if (firstEpoch != epoch) {
            FIRST_MODELS.clear();
            firstEpoch = epoch;
        }
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        var model = FIRST_MODELS.computeIfAbsent(slim, s -> new PlayerModel<>(
                Minecraft.getInstance().getEntityModels().bakeLayer(s ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), s));
        var m = motion(player, partial);
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
        Vec3 release = BombGlovePose.palm(player.getPosition(partial), player.getEyeHeight(), yaw, player.getMainArm());
        Vec3 physical = BombGloveStevePose.cameraLocal(release.subtract(camera), cameraToWorld);
        var frame = BombGloveFirstPersonFrame.sample(lens, right, physical, m);
        Vec3 palm = frame.palm();
        var prep = prepare(model, player, new Snapshot(Vec3.ZERO, palm, frame.rotation(), right), false, true);
        if (prep == null) return;
        var skinId = player.getSkin().texture();
        poses.pushPose();
        try {
            // Anchor is camera-local, then converted to the same camera-relative world
            // pass as flight. No second hand FOV, world yaw, or view roll is applied.
            poses.mulPose(new Quaternionf(cameraToWorld));
            var skin = buffers.getBuffer(RenderType.entityTranslucent(skinId));
            boolean sleeveOn = player.isModelPartShown(prep.socket.right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
            // Both top layers use the same entity render type to avoid invalidating a queued consumer.
            var sleeve = sleeveOn ? skin : null;
            int light = light(player, release);
            draw(prep, poses, buffers, skin, sleeve, light, OverlayTexture.NO_OVERLAY, -1);
            if (BombGloveClientTracking.heldBomb(player) != null) drawHeld(poses, buffers, palm, light);
        } finally {
            poses.popPose();
        }
    }

    private static void drawHeld(PoseStack poses, MultiBufferSource buffers, Vec3 point, int light) {
        var bomb = Rac1EffectClient.bomb();
        if (bomb == null) return;
        var textures = LEASES.held.get(bomb, Rac1ImportedItemDrawing.epoch(),
                bomb.textures().toArray(Rac1Texture[]::new)).toArray(ResourceLocation[]::new);
        poses.pushPose();
        try {
            poses.translate(point.x, point.y, point.z);
            Rac1MeshRenderer.render(poses, buffers, bomb.mesh(), textures, light, false);
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
            // Extend the proximal casing from the fixed wrist; never translate
            // the fingers or shift the palm to make the glove look larger.
            float body = Math.min((armWidth + 2.25f) / width(), BombGloveStevePose.shellDepth() / depth());
            return BombGloveStevePose.WRIST_Y - Math.clamp(length() * body * .83f, 6.5f, 7.75f);
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
                .translate(BombGloveStevePose.armOrigin(p.fit.centerX(), p.clearance));
        var pivot = arm.getTranslation(new Vector3f()).mul(16);
        var angles = arm.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());
        p.part.setPos(pivot.x, pivot.y, pivot.z);
        p.part.setRotation(angles.x, angles.y, angles.z);
    }
}
