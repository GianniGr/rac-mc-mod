package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.clank.Rac1ClankSession;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.effect.EffectClock;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/** Native-body adaptation: current skin/body pose at older roots, not particle or screen-space blur. */
final class Rac1ClankAfterimages {
    private record BodyTrail(Rac1ClankTrail history,net.minecraft.world.phys.Vec3 origin) {}
    private static final Map<UUID,BodyTrail> TRAILS=new HashMap<>();
    private Rac1ClankAfterimages() {}
    static void clear(){TRAILS.clear();}
    static void retain(Set<UUID> actors){TRAILS.keySet().retainAll(actors);}
    static void forget(UUID actor){TRAILS.remove(actor);}
    static void draw(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,
                     PlayerModel<AbstractClientPlayer> model,Rac1ClankSession.Snapshot view,float partial) {
        var capability=Rac1EffectClient.localPresentation();
        boolean valid=capability!=null&&capability.profile()==EffectClock.Profile.NTSC
                &&Rac1ClankClient.fresh(player.getUUID(),player.level().getGameTime())
                &&player.isAlive()&&!player.isSpectator()&&!player.isInvisible()&&!player.isPassenger()
                &&!player.isInWaterOrBubble()&&!player.isInLava()
                &&Minecraft.getInstance().player!=null&&player.distanceToSqr(Minecraft.getInstance().player)<=48*48;
        if(!valid||view==null){TRAILS.remove(player.getUUID());return;}
        if(TRAILS.size()>=16&&!TRAILS.containsKey(player.getUUID()))return;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var channel=TRAILS.computeIfAbsent(player.getUUID(),id->new BodyTrail(new Rac1ClankTrail(),player.position()));
        var origin=channel.origin();
        var root=new Matrix4f().translation((float)(camera.x-origin.x),(float)(camera.y-origin.y),
                (float)(camera.z-origin.z)).mul(pose.last().pose());
        long step=(long)Math.floor((player.level().getGameTime()+(double)partial)*3);
        double serverTime=Rac1ClankClient.VIEWS.serverTime(player.getUUID(),player.level().getGameTime()+partial);
        int phase=view.phaseSourceSteps()+(int)Math.floor(Math.max(0,serverTime-view.serverTick())*3);
        var ghosts=channel.history().sample(view,step,phase,root,true);
        if(ghosts.isEmpty())return;
        var out=buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        for(var ghost:ghosts) {
            var at=new PoseStack();
            at.mulPose(new Matrix4f().translation((float)(origin.x-camera.x),(float)(origin.y-camera.y),
                    (float)(origin.z-camera.z)).mul(ghost.root()));
            int argb=(ghost.alpha()*255/128)<<24|0xffffff;
            // Does not rerun layers, setupAnim, mutate the parent pose, or duplicate armor/cape/equipment.
            model.renderToBuffer(at,out,light,OverlayTexture.NO_OVERLAY,argb);
        }
    }
}
