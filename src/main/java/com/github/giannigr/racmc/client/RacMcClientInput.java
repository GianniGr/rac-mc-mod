package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.screen.IsoSelectionScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Opens the disc selection screen when the player presses the key mapping of the mod.
 *
 * <p>The event this class listens to only fires on the physical client, so a dedicated server
 * never opens the screen.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class RacMcClientInput {

    /**
     * Opens the disc selection screen after the client tick.
     *
     * @param event the event that marks the end of a client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (RacMcClient.OPEN_ISO_SELECTION.consumeClick()) {
            Minecraft.getInstance().setScreen(new IsoSelectionScreen());
        }
    }

    private RacMcClientInput() {
        throw new UnsupportedOperationException("Utility class");
    }
}
