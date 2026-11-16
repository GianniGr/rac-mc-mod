package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Temporary plain-text counter, NOT an imitation of the unimplemented original HUD bank/font. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class Rac1BoltHud {
    private Rac1BoltHud() {}
    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "bolts"),
                (graphics, delta) -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player == null || mc.options.hideGui) return;
                    var data = mc.player.getPersistentData();
                    if (!Rac1BoltHudTime.visible(data.getLong("racmcBoltPickupNanos"), System.nanoTime())) return;
                    String text = "Bolts: " + data.getLong("racmcBolts");
                    graphics.drawString(mc.font, text, graphics.guiWidth() - mc.font.width(text) - 12,
                            12, 0xFFFFFF);
                });
    }
}
