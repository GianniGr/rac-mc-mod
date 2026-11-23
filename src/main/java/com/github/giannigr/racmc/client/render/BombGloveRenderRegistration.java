package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.bomb.BombGloveFlightRenderer;
import com.github.giannigr.racmc.item.RacMcEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The sole native registration for the original client-local class-121 model. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class BombGloveRenderRegistration {
    private BombGloveRenderRegistration() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RacMcEntities.BOMB.get(), BombGloveFlightRenderer::new);
    }
}
