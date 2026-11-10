package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The kinds of entity of the mod.
 *
 * <p>The arrow that the Blaster fired is a kind of entity of its own, so that every client knows
 * which arrows the Blaster fired. The kinds of entity of the game of blocks and of the other mods
 * are not touched.
 */
public final class RacMcEntities {

    /** The kinds of entity of the mod, registered under the identifier of the mod. */
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RacMcConstants.MOD_ID);

    /**
     * The arrow that the Blaster fired.
     *
     * <p>The arrow is the arrow of the game of blocks, with the size, the eye and the reach of the
     * arrows of the game.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Rac1BlasterArrow>> BLASTER_ARROW =
            ENTITIES.register("blaster_arrow", () -> EntityType.Builder
                    .<Rac1BlasterArrow>of(Rac1BlasterArrow::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .eyeHeight(0.13F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("blaster_arrow"));

    private RacMcEntities() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers the kinds of entity of the mod.
     *
     * @param modEventBus the event bus of the mod
     */
    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
