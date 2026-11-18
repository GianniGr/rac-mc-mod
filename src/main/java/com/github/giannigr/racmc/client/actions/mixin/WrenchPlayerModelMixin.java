package com.github.giannigr.racmc.client.actions.mixin;

import com.github.giannigr.racmc.client.actions.WrenchPlayerPoseHook;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla setupAnim otherwise overwrites an early RenderPlayerEvent pose. */
@Mixin(PlayerModel.class)
abstract class WrenchPlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL"))
    private void racmc$wrenchPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float headYaw, float headPitch, CallbackInfo callback) {
        WrenchPlayerPoseHook.afterSetup((PlayerModel<?>) (Object) this, entity);
    }
}
