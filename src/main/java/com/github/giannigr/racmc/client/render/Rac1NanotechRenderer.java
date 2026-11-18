package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.loot.Rac1NanotechEntity;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Geometry is drawn by the shared local FX catalog after particles, not a replacement item mesh. */
public final class Rac1NanotechRenderer extends EntityRenderer<Rac1NanotechEntity> {
    public Rac1NanotechRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(Rac1NanotechEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
