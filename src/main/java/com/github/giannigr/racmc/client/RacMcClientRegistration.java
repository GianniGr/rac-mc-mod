package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.block.RacMcBlockEntities;
import com.github.giannigr.racmc.client.render.BlasterItemRenderer;
import com.github.giannigr.racmc.client.render.Rac1WrenchItemRenderer;
import com.github.giannigr.racmc.client.render.Rac1BlasterArrowRenderer;
import com.github.giannigr.racmc.client.render.Rac1CrateItemRenderer;
import com.github.giannigr.racmc.client.render.Rac1CrateRenderer;
import com.github.giannigr.racmc.client.render.Rac1FallingBlockRenderer;
import com.github.giannigr.racmc.client.render.Rac1VendorRenderer;
import com.github.giannigr.racmc.client.render.Rac1VendorItemRenderer;
import com.github.giannigr.racmc.client.render.Rac1VendorDrawing;
import com.github.giannigr.racmc.item.RacMcEntities;
import com.github.giannigr.racmc.item.RacMcItems;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

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
    private static Rac1VendorItemRenderer vendorRenderer;
    private static com.github.giannigr.racmc.client.render.Rac1BoltItemRenderer boltRenderer;
    private static Rac1WrenchItemRenderer wrenchRenderer;

    /** The renderers of the crates of the game, by the key of the crate. */
    private static final Map<String, Rac1CrateItemRenderer> crateRenderers =
            new LinkedHashMap<>();

    private RacMcClientRegistration() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gives the arrow of the Blaster and the crates of the game their own renderers.
     *
     * <p>The renderer of the game of blocks is kept for the arrows of the bow, of the crossbow and
     * of the other mods: only the arrows that the Blaster fired are drawn by the renderer of the
     * mod, and they are not drawn while they are at the mouth of the weapon.
     *
     * <p>The crates of the game are drawn by the renderer of the block entity, because they are
     * an arbitrary mesh and not a model of the game of blocks.
     *
     * @param event the event that registers the renderers of the entities
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RacMcEntities.BOLT.get(),
                com.github.giannigr.racmc.client.render.Rac1BoltRenderer::new);
        event.registerEntityRenderer(RacMcEntities.NANOTECH.get(),
                com.github.giannigr.racmc.client.render.Rac1NanotechRenderer::new);
        event.registerEntityRenderer(RacMcEntities.BLASTER_ARROW.get(),
                Rac1BlasterArrowRenderer::new);
        event.registerBlockEntityRenderer(RacMcBlockEntities.CRATE.get(), Rac1CrateRenderer::new);
        event.registerBlockEntityRenderer(RacMcBlockEntities.VENDOR.get(), Rac1VendorRenderer::new);
        // The crates of the game fall like the sand of the game of blocks, so they are carried
        // by the falling block of the game of blocks. Its renderer draws the model of the block
        // that falls, and the crate of the game has no model, so the renderer is replaced by one
        // that draws the crate of the game and hands every other block to the game of blocks.
        event.registerEntityRenderer(EntityType.FALLING_BLOCK,
                Rac1FallingBlockRenderer::new);
        event.registerEntityRenderer(RacMcEntities.FALLING_EXPLOSIVE.get(),
                Rac1FallingBlockRenderer::new);
    }

    /**
     * Gives the Blaster and the crates of the game their own renderer.
     *
     * @param event the event that registers the client side of the items
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private com.github.giannigr.racmc.client.render.BlasterAmmoItemRenderer ammo;

            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (ammo == null) ammo = new com.github.giannigr.racmc.client.render.BlasterAmmoItemRenderer();
                return ammo;
            }
        }, RacMcItems.BLASTER_AMMO.get());
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer shipRenderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (shipRenderer == null) {
                    shipRenderer = new com.github.giannigr.racmc.client.ship.GreenShipItemRenderer();
                }
                return shipRenderer;
            }
        }, com.github.giannigr.racmc.ship.Rac1Ships.PLACEMENT_ITEM.get());
        RacMcItems.BOLTS.values().forEach(item -> event.registerItem(new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (boltRenderer == null) boltRenderer =
                        new com.github.giannigr.racmc.client.render.Rac1BoltItemRenderer();
                return boltRenderer;
            }
        }, item.get()));
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (vendorRenderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    vendorRenderer = new Rac1VendorItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return vendorRenderer;
            }
        }, RacMcItems.VENDOR.get());
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return wrenchRenderer();
            }
        }, RacMcItems.OMNIWRENCH.get());
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
        // Every crate of the game is drawn by its own renderer, so that the tab of the
        // creative mode shows the crate of the game and not a picture of it.
        for (Map.Entry<String, DeferredItem<BlockItem>> entry : RacMcItems.crates().entrySet()) {
            event.registerItem(new IClientItemExtensions() {

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return crateRenderer(entry.getKey());
                }
            }, entry.getValue().get());
        }
    }

    /**
     * The renderer of a crate of the game.
     *
     * @param key the key of the crate
     * @return the renderer of the crate
     */
    private static Rac1CrateItemRenderer crateRenderer(String key) {
        Rac1CrateItemRenderer renderer = crateRenderers.get(key);
        if (renderer == null) {
            Minecraft minecraft = Minecraft.getInstance();
            BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
            EntityModelSet models = minecraft.getEntityModels();
            renderer = new Rac1CrateItemRenderer(dispatcher, models);
            crateRenderers.put(key, renderer);
        }
        return renderer;
    }

    @SubscribeEvent
    public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager ->
                Rac1VendorDrawing.clear());
        event.registerReloadListener((ResourceManagerReloadListener) manager ->
                com.github.giannigr.racmc.client.render.Rac1BlockDebris.clear());
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

    /** Reuse this client singleton for item and cosmetic detached rendering after consolidation. */
    public static Rac1WrenchItemRenderer wrenchRenderer() {
        if (wrenchRenderer == null) {
            Minecraft minecraft = Minecraft.getInstance();
            wrenchRenderer = new Rac1WrenchItemRenderer(
                    minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        }
        return wrenchRenderer;
    }
}
