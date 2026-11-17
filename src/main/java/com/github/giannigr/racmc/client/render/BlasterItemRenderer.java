package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.Rac1BlasterAim;
import com.github.giannigr.racmc.client.Rac1BlasterClient;
import com.github.giannigr.racmc.client.Rac1BlasterFrame;
import com.github.giannigr.racmc.client.Rac1BlasterPose;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.item.Rac1BlasterGrip;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
    private final Rac1ItemTextureLease<ResourceLocation> textures =
            Rac1ImportedItemDrawing.textures("blaster_item");

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
                textures.clear();
                // The assets of the game are not in the cache: there is nothing to draw.
                return;
            }
            Rac1CacheContent cache = content.get();
            Rac1AnimatedAsset blaster = cache.blaster();
            if (blaster == null || blaster.mesh().triangleCount() == 0) {
                return;
            }
            ResourceLocation[] locations = textures.get(cache, Rac1ImportedItemDrawing.epoch(),
                    blaster.textures()).toArray(ResourceLocation[]::new);

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
            if (!(buffers instanceof MultiBufferSource.BufferSource source)) {
                // The buffers of the game are needed to close the run of the triangles of a
                // material. Without them the weapon cannot be drawn.
                return;
            }
            Rac1ImportedItemDrawing.draw(poseStack, source, blaster, positions,
                    this.pose.normals(), locations, context, light, false, adjusted -> {
                        if (context.firstPerson()) {
                            alignWithWorldMuzzle(adjusted, context);
                        }
                    });
        } catch (RuntimeException | LinkageError exception) {
            this.failure = exception.toString();
            RacMcMod.LOGGER.error("The Blaster could not be drawn. The weapon is not drawn again.",
                    exception);
        }
    }

    /**
     * Moves the mouth of the drawn weapon to the place of the screen where the mouth of the
     * weapon in the world is drawn.
     *
     * <p>The mouth of the weapon in the world is the place the shot of the weapon leaves from,
     * and the shot is drawn with the projection of the world. The mouth of the drawn weapon is
     * drawn with the projection of the hand of the player, which is another projection. The move
     * is made with the matrices of the game of blocks of the frame that is drawn, so that the
     * mouth of the drawn weapon stands in the column of the mouth of the weapon in the world for
     * every field of view of the game. The move in the height is limited, so that the crosshair
     * of the screen stays clear and the weapon of the game stays in the lower right of the
     * screen.
     *
     * @param poseStack the stack of the poses of the game
     * @param context the place the weapon is drawn at
     */
    private static void alignWithWorldMuzzle(PoseStack poseStack, ItemDisplayContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !Rac1BlasterFrame.ready()) {
            // The world was not drawn: there is nothing to aim at.
            return;
        }
        ItemDisplayContext mainHand = player.getMainArm() == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (context != mainHand) {
            // The other hand keeps the place the game of blocks gives it.
            return;
        }
        Matrix4f modelView = Rac1BlasterFrame.modelView();
        Vector3f camera = Rac1BlasterFrame.camera();
        Rac1BlasterGrip.Point muzzle = Rac1BlasterClient.muzzleOf(player);
        // The place of the mouth of the weapon in the world, in the space of the camera.
        Vector4f world = new Vector4f((float) (muzzle.x() - camera.x),
                (float) (muzzle.y() - camera.y), (float) (muzzle.z() - camera.z), 1.0f)
                .mul(modelView);
        Vector3f move = Rac1BlasterAim.align(poseStack.last().pose(), modelView,
                Rac1BlasterFrame.projection(), RenderSystem.getProjectionMatrix(),
                new Vector3f(world.x, world.y, world.z));
        poseStack.translate(move.x, move.y, move.z);
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

}
