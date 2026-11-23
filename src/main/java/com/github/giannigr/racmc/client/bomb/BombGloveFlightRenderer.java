package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.client.render.Rac1ImportedItemDrawing;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveClientTracking;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking;
import com.github.giannigr.racmc.item.weapon.bomb.Rac1Bomb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** Draws only admitted native flights. The dispatcher origin is the shared palm at release. */
public final class BombGloveFlightRenderer extends EntityRenderer<Rac1Bomb> {
    private static final com.github.giannigr.racmc.client.render.Rac1ItemTextureLease<ResourceLocation> TEXTURES =
            Rac1ImportedItemDrawing.textures("bomb_glove_flight");

    public BombGloveFlightRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static void clearTextures() { TEXTURES.clear(); }

    @Override
    public void render(Rac1Bomb bomb, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        var commit = BombGloveClientTracking.flight(bomb);
        if (!draws(commit, bomb.phase())) return;
        var model = Rac1EffectClient.bomb();
        if (model == null) return;
        var textures = TEXTURES.get(model, Rac1ImportedItemDrawing.epoch(),
                model.textures().toArray(Rac1Texture[]::new)).toArray(ResourceLocation[]::new);
        double seconds = elapsed(bomb.level().getGameTime(), commit.releaseTick(), partial);
        pose.pushPose();
        try {
            // Imported basis is source (X,Y,Z) -> (X,Z,-Y). Spin about the same point
            // in flight and on release; no item centering, floor offset or camera translation.
            pose.mulPose(Axis.XP.rotation(angle(seconds, BombGloveAppearance.SPIN_X_RADIANS_PER_SECOND)));
            pose.mulPose(Axis.ZN.rotation(angle(seconds, BombGloveAppearance.SPIN_Y_RADIANS_PER_SECOND)));
            Rac1MeshRenderer.render(pose, buffers, model.mesh(), textures, light, false);
        } finally {
            pose.popPose();
        }
    }

    static boolean draws(BombGloveTracking.Commit commit, Rac1Bomb.Phase phase) {
        return commit != null && (phase == Rac1Bomb.Phase.FLIGHT || phase == Rac1Bomb.Phase.SINKING);
    }

    static double elapsed(long gameTick, long releaseTick, float partial) {
        return Math.max(0, (double) gameTick - releaseTick + Math.clamp(partial, 0, 1)) / 20;
    }

    private static float angle(double seconds, double rate) {
        return (float) Math.IEEEremainder(seconds * rate, Math.TAU);
    }

    @Override
    public ResourceLocation getTextureLocation(Rac1Bomb bomb) {
        // EntityRenderer's required default. render() never submits this as a fallback.
        return MissingTextureAtlasSprite.getLocation();
    }
}
