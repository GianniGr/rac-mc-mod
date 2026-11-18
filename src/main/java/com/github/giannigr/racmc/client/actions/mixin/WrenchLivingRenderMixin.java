package com.github.giannigr.racmc.client.actions.mixin;

import com.github.giannigr.racmc.client.actions.WrenchPlayerPoseHook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flat native parts and armor/cape layers share one root; the native pop restores it. */
@Mixin(LivingEntityRenderer.class)
abstract class WrenchLivingRenderMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void racmc$connectedWrenchRoot(LivingEntity entity, float yaw, float partialTick,
            PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo callback) {
        WrenchPlayerPoseHook.applyModelRoot(entity, poses);
    }
}
