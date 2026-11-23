package com.github.giannigr.racmc.client.bomb.mixin;

import com.github.giannigr.racmc.client.bomb.BombGloveRenderScope;
import com.github.giannigr.racmc.client.bomb.BombGloveWornDrawing;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Changes one player's skin draw only; the native armor pass still renders and owns its own model. */
@Mixin(LivingEntityRenderer.class)
abstract class BombGlovePlayerRenderMixin {
    @Unique private BombGloveRenderScope racmc$bombGloveScope;

    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void racmc$withBombGloveScope(LivingEntity entity, float yaw, float partial,
            PoseStack poses, MultiBufferSource buffers, int light, Operation<Void> original) {
        var previous = racmc$bombGloveScope;
        var scope = entity instanceof AbstractClientPlayer player && BombGloveWornDrawing.eligible(player)
                ? new BombGloveRenderScope(player, partial, new Matrix4f(poses.last().pose()), buffers) : null;
        racmc$bombGloveScope = scope;
        try (scope) {
            original.call(entity, yaw, partial, poses, buffers, light);
        } finally {
            racmc$bombGloveScope = previous;
        }
    }

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void racmc$skinWithBombGlove(EntityModel<?> model, PoseStack poses,
            VertexConsumer skin, int light, int overlay, int color, Operation<Void> original) {
        var scope = racmc$bombGloveScope;
        var prep = scope != null && model instanceof PlayerModel<?> playerModel
                ? BombGloveWornDrawing.prepare(playerModel, scope.player(), scope.partial()) : null;
        if (prep == null) {
            original.call(model, poses, skin, light, overlay, color);
            return;
        }
        var arm = prep.part();
        var sleeve = prep.sleeve();
        try (var mask = new BombGloveRenderScope.SkinMask(arm, sleeve)) {
            original.call(model, poses, skin, light, overlay, color);
        }
        try (var frame = new BombGloveRenderScope.PoseFrame(poses)) {
            BombGloveWornDrawing.align(poses, scope.entityFrame());
            BombGloveWornDrawing.draw(prep, poses, scope.buffers(), skin, skin, light, overlay, color);
        }
        scope.rememberArmorArm(arm);
        BombGloveWornDrawing.alignArmor(prep, poses, scope.entityFrame());
    }
}
