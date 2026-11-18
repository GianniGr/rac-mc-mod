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

/** Regular textured mesh foundation. Native Steve/armor/cape remain; no root/pose hook. */
public final class Rac1ClankLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public Rac1ClankLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }
    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
        var view = Rac1ClankClient.VIEWS.get(player.getUUID());
        if (view == null || !view.equipment().worn() || player.isInvisible()) return;
        // Action playback/motion remains inactive until its admission/controller is reviewed.
        if (view.action() != Action.IDLE) return;
        pose.pushPose();
        try {
            getParentModel().body.translateAndRotate(pose); // exactly ONCE, after established root
            pose.mulPose(Rac1ClankSteveMount.local());
            double serverTime = Rac1ClankClient.VIEWS.serverTime(player.getUUID(),
                    player.level().getGameTime() + partial);
            double seconds = Math.max(0, serverTime - view.actionStartTick()) / 20;
            draw(Role.CLANK, pose, buffers, light, seconds);
            if (view.equipment().selected() == Module.HELI)
                draw(Role.HELI, pose, buffers, light, seconds);
            else if (view.equipment().selected() == Module.THRUSTER)
                draw(Role.THRUSTER, pose, buffers, light, seconds);
        } finally {
            pose.popPose();
        }
    }
    private static void draw(Role role, PoseStack pose, MultiBufferSource buffers, int light, double seconds) {
        var model = Rac1ClankResources.model(role);
        if (model == null) return;
        var clip = java.util.Arrays.stream(model.animations())
                .filter(c -> c.name().equals("sequence_1")).findFirst().orElseThrow();
        double duration = clip.times()[clip.keyCount() - 1];
        float sample = (float) (clip.loop() && duration > 0 ? seconds % duration : Math.min(seconds, duration));
        float[] matrices = Rac1AnimationEvaluator.evaluate(model.skeleton(), clip, sample, 1);
        float[] positions = new float[model.mesh().positions().length];
        float[] normals = new float[positions.length];
        Rac1AnimationEvaluator.pose(model.mesh(), matrices, positions, normals);
        Rac1MeshRenderer.render(pose, buffers, model.mesh(), positions, normals,
                Rac1ClankResources.textures(role), light, false);
    }
}
