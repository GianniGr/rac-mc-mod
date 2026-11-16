package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Lazy migration for already-falling explosives saved by the stateless baseline. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1CrateFallEvents {
    private Rac1CrateFallEvents() {
    }

    @SubscribeEvent
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (event.isCanceled() || !(event.getEntity() instanceof FallingBlockEntity falling)
                || falling instanceof Rac1FallingExplosive
                || !(falling.level() instanceof ServerLevel level)
                || !(falling.getBlockState().getBlock() instanceof Rac1CrateBlock block)
                || !block.explosive()) {
            return;
        }
        if (Rac1FallingExplosive.migrate(level, falling) != null) {
            event.setCanceled(true); // Do not run the replaced carrier's landing physics.
        }
    }
}
