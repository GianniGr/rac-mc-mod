package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.Rac1BlasterClient;
import com.github.giannigr.racmc.client.Rac1BlasterPose;
import com.github.giannigr.racmc.client.RacMcClient;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The drawing of the Blaster as an item of the game.
 *
 * <p>The weapon of the game is not a model of the game of blocks: it is an arbitrary mesh with
 * its own textures and its own animations. The item is therefore drawn by this renderer, which
 * is given to the game of blocks for the item of the Blaster.
 *
 * <p>The weapon is drawn as it was imported: it keeps its own size and its own proportions, and
 * the point of the game that holds it (the origin of the mesh) stays where it is, so that the
 * weapon hangs in the hand the way it hangs in the hand of the hero in the game.
 *
 * <p>The game draws an item in the cube of an item of the game and moves that cube by half a
 * block in every direction before it hands the item to the renderer of the item. The weapon of
 * the game does not use that cube, so the renderer takes that move back, exactly, before it
 * draws anything. The transforms of the model of the item
 * ({@code assets/racmc/models/item/blaster.json}) place the weapon in the hand and turn it, and
 * they are the only place where the direction of the weapon is decided.
 *
 * <p>The sizes of the weapon, as it was imported, are these: the weapon is 0.71 blocks wide, 0.31
 * blocks high and 0.77 blocks long, and the mouth of the weapon is at the front of its long axis.
 * The transforms of the model of the item scale the weapon; a scale of one keeps the size of the
 * game.
 *
 * <p>The textures of the weapon are put into the texture manager of the game once for every
 * content of the cache of the assets, and not once for every frame. The renderer draws nothing
 * when the assets of the game are not in the cache.
 */
public final class BlasterItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** The pose of the weapon. */
    private final Rac1BlasterPose pose = new Rac1BlasterPose();

    /** The textures of the weapon, when they were registered. */
    private ResourceLocation[] textures;

    /** The content of the cache the textures belong to. */
    private Rac1CacheContent textureSource;

    /** The failure of the drawing, when there was one. */
    private String failure;

    /**
     * Creates the renderer.
     *
     * @param dispatcher the dispatcher of the blocks of the world
     * @param models the models of the entities
     */
    public BlasterItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        if (this.failure != null) {
            return;
        }
        try {
            Optional<Rac1CacheContent> content = RacMcClient.assets().content();
            if (content.isEmpty()) {
                // The assets of the game are not in the cache: there is nothing to draw.
                return;
            }
            Rac1CacheContent cache = content.get();
            Rac1AnimatedAsset blaster = cache.blaster();
            if (blaster == null || blaster.mesh().triangleCount() == 0) {
                return;
            }
            ResourceLocation[] locations = texturesOf(cache, blaster);

            Minecraft minecraft = Minecraft.getInstance();
            float tick = tickOf(minecraft);
            LivingEntity entity = context.firstPerson() ? minecraft.player
                    : Rac1BlasterClient.current();
            float weight = Rac1BlasterClient.weightOf(entity, tick);
            float fireTime = Rac1BlasterClient.fireTimeOf(entity, tick);
            this.pose.update(blaster, weight, fireTime);
            float[] positions = this.pose.positions();
            if (positions.length == 0) {
                return;
            }
            // The picture of the item in the lists of the game is always drawn with the full
            // light, so that the weapon can be seen in the lists.
            int usedLight = context == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light;
            if (!(buffers instanceof MultiBufferSource.BufferSource source)) {
                // The buffers of the game are needed to close the run of the triangles of a
                // material. Without them the weapon cannot be drawn.
                return;
            }
            poseStack.pushPose();
            try {
                // The game moves the cube of the item by half a block in every direction before
                // it hands the item to this renderer, because the cube of an item of the game
                // stands in the middle of the place of the item. The weapon of the game does not
                // use that cube: its own origin is the point of the game that holds it, and the
                // weapon keeps the size it was imported with. The move of the game is therefore
                // taken back, exactly, before anything else is done to the weapon.
                poseStack.translate(0.5f, 0.5f, 0.5f);
                Rac1MeshRenderer.render(poseStack, source, blaster.mesh(), positions,
                        this.pose.normals(), locations, usedLight);
            } finally {
                poseStack.popPose();
            }
        } catch (RuntimeException | LinkageError exception) {
            this.failure = exception.toString();
            RacMcMod.LOGGER.error("The Blaster could not be drawn. The weapon is not drawn again.",
                    exception);
        }
    }

    /**
     * The tick of the client, with its partial tick.
     *
     * @param minecraft the game
     * @return the tick
     */
    private static float tickOf(Minecraft minecraft) {
        if (minecraft.level == null) {
            return 0.0f;
        }
        return minecraft.level.getGameTime()
                + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
    }

    /**
     * The places of the textures of the weapon.
     *
     * <p>The textures are put into the texture manager of the game once for every content of the
     * cache of the assets. The textures of a content that is not the one of the moment are
     * released, so that the images of the textures do not stay in the texture manager.
     *
     * @param content the content of the cache of the assets
     * @param blaster the weapon of the game
     * @return the places of the textures
     */
    private ResourceLocation[] texturesOf(Rac1CacheContent content, Rac1AnimatedAsset blaster) {
        if (this.textureSource != content) {
            release(this.textures);
            this.textureSource = content;
            this.textures = register(blaster);
        }
        return this.textures;
    }

    /**
     * Puts the textures of the weapon into the texture manager of the game.
     *
     * @param blaster the weapon of the game
     * @return the places of the textures
     */
    private static ResourceLocation[] register(Rac1AnimatedAsset blaster) {
        ResourceLocation[] locations = new ResourceLocation[blaster.textures().length];
        for (int index = 0; index < locations.length; index++) {
            locations[index] = Rac1ClientTextures.register("blaster_item_" + index,
                    blaster.textures()[index]);
        }
        return locations;
    }

    /**
     * Releases a list of places of textures.
     *
     * @param locations the places of the textures
     */
    private static void release(ResourceLocation[] locations) {
        if (locations == null) {
            return;
        }
        for (ResourceLocation location : locations) {
            Rac1ClientTextures.release(location);
        }
    }
}
