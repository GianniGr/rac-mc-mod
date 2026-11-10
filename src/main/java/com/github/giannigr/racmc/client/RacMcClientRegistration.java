package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.render.BlasterItemRenderer;
import com.github.giannigr.racmc.client.render.Rac1BlasterArrowRenderer;
import com.github.giannigr.racmc.item.RacMcEntities;
import com.github.giannigr.racmc.item.RacMcItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The registration of the client side of the items of the mod.
 *
 * <p>The Blaster of the game is not a model of the game of blocks, so the item is given a
 * renderer of its own. The renderer is built when the item is drawn for the first time, because
 * the models of the entities of the game are not ready when the event of the registration is
 * fired.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class RacMcClientRegistration {

    /** The renderer of the Blaster, when it was built. */
    private static BlasterItemRenderer renderer;

    private RacMcClientRegistration() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gives the arrow of the Blaster its own renderer.
     *
     * <p>The renderer of the game of blocks is kept for the arrows of the bow, of the crossbow and
     * of the other mods: only the arrows that the Blaster fired are drawn by the renderer of the
     * mod, and they are not drawn while they are at the mouth of the weapon.
     *
     * @param event the event that registers the renderers of the entities
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RacMcEntities.BLASTER_ARROW.get(),
                Rac1BlasterArrowRenderer::new);
    }

    /**
     * Gives the Blaster its own renderer and its own pose of the arm.
     *
     * @param event the event that registers the client side of the items
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer();
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player,
                    HumanoidArm arm, ItemStack itemInHand, float partialTick, float equipProgress,
                    float swingProgress) {
                if (arm != player.getMainArm()
                        || !player.getMainHandItem().is(RacMcItems.BLASTER.get())) {
                    // The other hand keeps the pose of the game of blocks.
                    return false;
                }
                boolean using = player.isUsingItem()
                        && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
                Rac1BlasterHand.apply(poseStack, arm, using, swingProgress);
                return true;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand,
                                                    ItemStack stack) {
                if (hand != InteractionHand.MAIN_HAND) {
                    // The other arm keeps the pose of the game of blocks.
                    return null;
                }
                if (!Rac1BlasterClient.isFiring(entity)) {
                    // The Blaster is only held and not fired: the arm of the game of blocks holds
                    // the weapon the way it holds every other item.
                    return null;
                }
                // The shot was accepted and the arm of the player points the bore of the weapon
                // at the place the player looks at.
                return Rac1BlasterArmPose.aim();
            }
        }, RacMcItems.BLASTER.get());
    }

    /**
     * The renderer of the Blaster.
     *
     * @return the renderer
     */
    private static BlockEntityWithoutLevelRenderer renderer() {
        if (renderer == null) {
            Minecraft minecraft = Minecraft.getInstance();
            BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
            EntityModelSet models = minecraft.getEntityModels();
            renderer = new BlasterItemRenderer(dispatcher, models);
        }
        return renderer;
    }
}
