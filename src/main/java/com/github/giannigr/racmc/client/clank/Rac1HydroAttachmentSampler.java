package com.github.giannigr.racmc.client.clank;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Attachment-only native body pose for the unrendered first-person wearer.
 * Owns its model; never renders a skin/layer, emits vertices, or mutates the real renderer.
 */
final class Rac1HydroAttachmentSampler extends PlayerRenderer {
    private static Rac1HydroAttachmentSampler sampler;
    private Rac1HydroAttachmentSampler(EntityRendererProvider.Context context) {super(context,false);}
    static void install(EntityRendererProvider.Context context) {sampler=new Rac1HydroAttachmentSampler(context);}
    static void capture(AbstractClientPlayer player,com.github.giannigr.racmc.clank.Rac1ClankSession.Snapshot clank,
            float partial,Vec3 camera) {
        if(sampler==null||clank==null||player.isInvisible()
                ||player.isPassenger()||player.isSleeping()||player.isFallFlying()
                ||!player.isAlive()||player.isSpectator())return;
        sampler.sample(player,clank,partial,camera);
    }
    private void sample(AbstractClientPlayer player,com.github.giannigr.racmc.clank.Rac1ClankSession.Snapshot clank,
            float partial,Vec3 camera) {
        // Match LevelRenderer's interpolated origin and LivingEntityRenderer's root/body frame.
        // PlayerRenderer's private arm/skin visibility selection does not affect this torso frame.
        var offset=getRenderOffset(player,partial);
        var pose=new PoseStack();
        pose.translate(Mth.lerp((double)partial,player.xOld,player.getX())-camera.x+offset.x,
                Mth.lerp((double)partial,player.yOld,player.getY())-camera.y+offset.y,
                Mth.lerp((double)partial,player.zOld,player.getZ())-camera.z+offset.z);
        float scale=player.getScale(),bob=getBob(player,partial);
        float bodyYaw=Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot);
        float headYaw=Mth.rotLerp(partial,player.yHeadRotO,player.yHeadRot)-bodyYaw;
        float pitch=Mth.lerp(partial,player.xRotO,player.getXRot());
        if(isEntityUpsideDown(player)){headYaw=-headYaw;pitch=-pitch;}
        pose.scale(scale,scale,scale);
        setupRotations(player,pose,bob,bodyYaw,partial,scale);
        pose.scale(-1,-1,1);scale(player,pose,partial);pose.translate(0,-1.501,0);
        model.attackTime=getAttackAnim(player,partial);model.riding=false;model.young=player.isBaby();
        model.crouching=player.isCrouching();
        model.leftArmPose=model.rightArmPose=HumanoidModel.ArmPose.EMPTY;
        float speed=Math.min(1,player.walkAnimation.speed(partial));
        float walk=player.walkAnimation.position(partial)*(player.isBaby()?3:1);
        model.prepareMobModel(player,walk,speed,partial);
        model.setupAnim(player,walk,speed,bob,Mth.wrapDegrees(headYaw),pitch);
        Rac1ClankSteveMount.attach(pose,model.body);
        Rac1HydroLayer.capture(player.getUUID(),clank,pose,partial);
    }
}
