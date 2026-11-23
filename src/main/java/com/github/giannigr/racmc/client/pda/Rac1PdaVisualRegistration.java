package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.RacMcItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.*;

/** Visual registration is independent of portable screen/commerce readiness. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class Rac1PdaVisualRegistration {
    private Rac1PdaVisualRegistration() {}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private Rac1PdaItemRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer==null) renderer=new Rac1PdaItemRenderer();
                return renderer;
            }
        },RacMcItems.PDA.get());
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> Rac1PdaVisuals.clear());
    }
}
