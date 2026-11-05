package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.render.BlasterItemRenderer;
import com.github.giannigr.racmc.item.RacMcItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand,
                                                    ItemStack stack) {
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
