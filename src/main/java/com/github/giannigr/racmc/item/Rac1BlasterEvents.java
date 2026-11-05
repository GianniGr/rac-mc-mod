package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.RacMcConstants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The events of the game that the Blaster needs.
 *
 * <p>The arrows of the Blaster are removed when their flight is over. The removal happens in the
 * tick of the entity, so that only the arrows of the Blaster are looked at and the arrows of the
 * game are not touched.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1BlasterEvents {

    private Rac1BlasterEvents() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Removes the arrows of the Blaster whose flight is over.
     *
     * @param event the event that is fired after an entity was ticked
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Rac1BlasterServer.removeExpiredArrow(event.getEntity());
    }
}
