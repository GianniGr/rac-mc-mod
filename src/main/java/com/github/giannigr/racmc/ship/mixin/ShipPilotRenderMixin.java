package com.github.giannigr.racmc.ship.mixin;

import com.github.giannigr.racmc.client.ship.GreenShipPlayerRender;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No transformed instruction should reference a mod-owned type within the mixin package. */
@Mixin(LivingEntityRenderer.class)
abstract class ShipPilotRenderMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void racmc$shipRenderScope(LivingEntity actor, float yaw, float partialTick,
                                     PoseStack poses, MultiBufferSource buffers, int light,
                                     Operation<Void> vanilla) {
        GreenShipPlayerRender.wrap((LivingEntityRenderer<?, ?>) (Object) this,
                actor, yaw, partialTick, poses, buffers, light, vanilla);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void racmc$shipAfterSetup(LivingEntity actor, float yaw, float partialTick,
                                      PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        GreenShipPlayerRender.afterSetup(actor, ((LivingEntityRenderer<?, ?>) (Object) this).getModel());
    }
}
