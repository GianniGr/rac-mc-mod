package com.github.giannigr.racmc.client.effect;

import com.github.giannigr.racmc.effect.EffectMaterial;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Material-local states restored by RenderType.clearRenderState; never entityCutout.
 * GS display-byte blending is approximated in Minecraft's colour space, pending GPU acceptance. */
final class EffectRenderTypes extends RenderType {
    private EffectRenderTypes() {
        super("rac1_effect", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                1536, false, false, () -> {}, () -> {});
    }
    private static final TransparencyStateShard ADDITIVE_FX = new TransparencyStateShard("rac1_effect_add", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    static RenderType create(ResourceLocation texture, EffectMaterial material) {
        if (!material.depthTest() || material.depthWrite() || !material.bilinear())
            throw new IllegalArgumentException("Unsupported first-slice effect material");
        return create("rac1_effect", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                1536, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(material.blend() == EffectMaterial.Blend.ADDITIVE
                                ? ADDITIVE_FX : TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
}
