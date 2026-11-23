package com.github.giannigr.racmc.client.belt;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.gear.Rac1BeltWorn;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** A separate native player layer, independent of Clank, Back, held items and gameplay. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1BoltGrabberLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private Rac1BoltGrabberLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer)
                renderer.addLayer(new Rac1BoltGrabberLayer(renderer));
        }
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
        var mc = Minecraft.getInstance();
        var body = getParentModel().body;
        if (!body.visible || !player.isAlive() || player.isRemoved() || player.isInvisible()
                || player.isSpectator() || !Rac1BeltWorn.showsBoltGrabber(player)
                || (mc.getCameraEntity() == player && mc.options.getCameraType().isFirstPerson())) return;
        var art = Rac1BoltGrabberResources.get();
        if (art == null) return;
        var fit = art.prepared().mount();
        var sample = art.prepared().neutral();
        var clearance = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem
                ? Rac1BeltWaist.Clearance.CHESTPLATE
                : player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof ArmorItem
                ? Rac1BeltWaist.Clearance.LEGGINGS
                : getParentModel().jacket.visible ? Rac1BeltWaist.Clearance.JACKET : Rac1BeltWaist.Clearance.SKIN;
        poses.pushPose();
        try {
            body.translateAndRotate(poses); // Inherit crouch/swim and existing body root exactly once.
            poses.mulPose(fit.local(clearance));
            Rac1MeshRenderer.render(poses, buffers, art.prepared().model().mesh(), sample.positions(),
                    sample.normals(), art.textures(), light, false);
        } finally {
            poses.popPose();
        }
    }
}
