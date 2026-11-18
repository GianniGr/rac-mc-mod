package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Bolts;
import com.github.giannigr.racmc.loot.Rac1BoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class Rac1BoltRenderer extends EntityRenderer<Rac1BoltEntity> {
    public Rac1BoltRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(Rac1BoltEntity entity, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((entity.level().getGameTime() + partialTick) * 6));
        Rac1BoltDrawing.draw(Rac1Bolts.key(entity.value()), pose, buffers, light);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(Rac1BoltEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
