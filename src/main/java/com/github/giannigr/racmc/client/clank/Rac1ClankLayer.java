package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Vector3f;
import java.util.List;
import com.github.giannigr.racmc.effect.EffectSignal.Point;

/** Regular textured mesh foundation. Native Steve/armor/cape remain; no root/pose hook. */
public final class Rac1ClankLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public Rac1ClankLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }
    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
        var view = Rac1ClankClient.VIEWS.get(player.getUUID());
        if (view == null || !view.equipment().worn() || player.isInvisible()) {
            Rac1ClankAfterimages.forget(player.getUUID());return;
        }
        Rac1ClankAfterimages.draw(pose,buffers,light,player,getParentModel(),view,partial);
        Rac1ClankAvClient.captureFeet(player.getUUID(),List.of(
                foot(pose,getParentModel().leftLeg),foot(pose,getParentModel().rightLeg)));
        pose.pushPose();
        try {
            Rac1ClankSteveMount.attach(pose,getParentModel().body); // exactly once after established root
            if(Rac1HydroLayer.draw(player.getUUID(),view,pose,buffers,light,partial))return;
            double serverTime = Rac1ClankClient.VIEWS.serverTime(player.getUUID(),
                    player.level().getGameTime() + partial);
            draw(Role.CLANK, pose, buffers, light, player.getUUID(),view,serverTime);
            if (view.equipment().selected() == Module.HELI)
                draw(Role.HELI, pose, buffers, light,player.getUUID(),view,serverTime);
            else if (view.equipment().selected() == Module.THRUSTER)
                draw(Role.THRUSTER, pose, buffers, light,player.getUUID(),view,serverTime);
        } finally {
            pose.popPose();
        }
    }
    private static Point foot(PoseStack pose,net.minecraft.client.model.geom.ModelPart leg) {
        pose.pushPose();
        try {
            leg.translateAndRotate(pose);
            var at=pose.last().pose().transformPosition(new Vector3f(0,.75f,0));
            var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            return new Point(camera.x+at.x,camera.y+at.y,camera.z+at.z);
        } finally {pose.popPose();}
    }
    private static void draw(Role role, PoseStack pose, MultiBufferSource buffers, int light,
            java.util.UUID player,com.github.giannigr.racmc.clank.Rac1ClankSession.Snapshot view,
            double serverTime) {
        var model = Rac1ClankResources.model(role);
        if (model == null) return;
        float[] matrices=Rac1ClankPlayback.pose(player,role,model,view,serverTime);
        if(role==Role.THRUSTER)Rac1ClankAvClient.capture(player,model,
                Rac1ClankPlayback.chain(player,role,model),pose.last().pose());
        float[] positions = new float[model.mesh().positions().length];
        float[] normals = new float[positions.length];
        Rac1AnimationEvaluator.pose(model.mesh(), matrices, positions, normals);
        Rac1MeshRenderer.render(pose, buffers, model.mesh(), positions, normals,
                Rac1ClankResources.textures(role), light, false);
    }
}
