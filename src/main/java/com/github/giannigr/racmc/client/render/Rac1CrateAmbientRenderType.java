package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcConstants;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Crate-only ambient replacement, not a texture tint or fullbright effect. Off-phases use
 * the original RenderType, so no mutable ambient/light state can leak into another mesh.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class Rac1CrateAmbientRenderType extends RenderType {
    private static final Map<ResourceLocation, RenderType> TYPES = new ConcurrentHashMap<>();
    private static ShaderInstance shader;

    private Rac1CrateAmbientRenderType() {
        super("rac1_crate_ambient", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                1536, true, false, () -> { }, () -> { });
    }

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "rac1_crate_ambient"),
                DefaultVertexFormat.NEW_ENTITY), loaded -> shader = loaded);
    }

    public static RenderType of(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, location -> create("rac1_crate_ambient",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(location, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true)));
    }
}
