package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/** Effective609 override inside the existing once-transformed native torso layer. */
final class Rac1HydroLayer {
    private Rac1HydroLayer() {}
    static boolean draw(UUID player,Rac1ClankSession.Snapshot clank,PoseStack pose,MultiBufferSource buffers,int light,float partial) {
        var view=Rac1HydroClient.view(player);
        if(view==null||view.state().clankEpoch()!=clank.epoch())return false;
        var state=view.state();if(state.visual()==Rac1HydroVisual.Phase.DRY)return false;
        var visual=new Rac1HydroVisual();visual.restore(state.visual(),state.visualTime());
        var level=Minecraft.getInstance().level;
        double elapsed=level==null?0:Math.max(0,level.getGameTime()+partial-view.localTick());
        for(int i=0;i<Math.min(180,(int)(elapsed*3));i++)visual.step();
        var phase=visual.phase();if(phase==Rac1HydroVisual.Phase.DRY)return false;
        boolean surface=phase==Rac1HydroVisual.Phase.WET&&state.water()==Rac1HydroWater.Mode.SURFACE
                &&Math.hypot(state.motion().effective().x(),state.motion().effective().z())>.1;
        float seconds=phase==Rac1HydroVisual.Phase.WET?(float)((state.tick()+elapsed)/20):visual.time()/60f;
        double sourceTime=(state.tick()+elapsed)*3;
        draw(player,sourceTime,Rac1ClankResources.model(Role.CLANK),surface?16:1,seconds,
                Rac1ClankResources.textures(Role.CLANK),pose,buffers,light);
        if(phase==Rac1HydroVisual.Phase.PUT_AWAY) {
            if(state.dry()!=Rac1ClankEquipment.Module.NONE) {
                var role=Rac1ClankItemPose.packRole(state.dry());
                draw(player,sourceTime,Rac1ClankResources.model(role),2,seconds,Rac1ClankResources.textures(role),pose,buffers,light);
            }
        }else if(phase!=Rac1HydroVisual.Phase.GAP&&phase!=Rac1HydroVisual.Phase.RESTORE_GAP) {
            int sequence=phase==Rac1HydroVisual.Phase.UNFOLD?0:phase==Rac1HydroVisual.Phase.FOLD?2:surface?3:1;
            draw(player,sourceTime,Rac1HydroResources.model(),sequence,seconds,Rac1HydroResources.textures(),pose,buffers,light);
        }
        return true;
    }
    private static void draw(UUID player,double sourceTime,Rac1AnimatedAsset asset,int sequence,float seconds,
            net.minecraft.resources.ResourceLocation[] textures,PoseStack pose,MultiBufferSource buffers,int light) {
        if(asset==null)return;
        var matrices=Rac1HydroPlayback.pose(player,asset,sequence,seconds,sourceTime);
        float[] positions=new float[asset.mesh().positions().length],normals=new float[positions.length];
        Rac1AnimationEvaluator.pose(asset.mesh(),matrices,positions,normals);
        Rac1MeshRenderer.render(pose,buffers,asset.mesh(),positions,normals,textures,light,false);
    }
}
