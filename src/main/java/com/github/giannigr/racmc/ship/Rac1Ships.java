package com.github.giannigr.racmc.ship;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1Ships {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RacMcConstants.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RacMcConstants.MOD_ID);
    public static final Supplier<EntityType<Rac1TravelShip>> SHIP = ENTITIES.register(
            "travel_ship", () -> EntityType.Builder.<Rac1TravelShip>of(Rac1TravelShip::new, MobCategory.MISC)
                    .sized(ShipCockpitRefit.activeProfile().width(), ShipCockpitRefit.activeProfile().height())
                    .clientTrackingRange(10).updateInterval(1)
                    .noSummon().build("racmc:travel_ship"));
    public static final Supplier<Item> PLACEMENT_ITEM =
            ITEMS.register("travel_ship_fixture", () -> new ShipPlacementItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
    }

    @SubscribeEvent
    public static void mount(EntityMountEvent event) {
        if (!event.isMounting() || event.getLevel().isClientSide) return;
        // Native force-mount (e.g. /ride or legacy player saves) bypasses canAddPassenger.
        if (event.getEntityMounting() instanceof Rac1TravelShip
                || event.getEntityBeingMounted() instanceof Rac1TravelShip ship
                    && !ship.canAddPassenger(event.getEntityMounting())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Before PlayerList saves the player or removes native RootVehicles with a departing pilot.
        if (event.getEntity() instanceof ServerPlayer pilot && pilot.getVehicle() instanceof Rac1TravelShip ship) {
            ship.releasePilot(pilot);
        }
    }

    private Rac1Ships() {}
}
