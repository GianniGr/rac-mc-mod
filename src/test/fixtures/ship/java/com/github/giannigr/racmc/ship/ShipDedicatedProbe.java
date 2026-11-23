package com.github.giannigr.racmc.ship;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Disposable, loopback-only dedicated restart probe. Not compiled into the production artifact. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class ShipDedicatedProbe {
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) throws Exception {
        String stage = System.getProperty("racmc.shipDedicatedStage");
        if (stage == null || !(event.getServer() instanceof DedicatedServer server) || server.getTickCount() < 10) return;
        var level = server.overworld();
        var marker = Path.of("ship-restart-checkpoint.txt");
        if (stage.equals("write")) {
            if (Files.exists(marker)) throw new AssertionError("Use a fresh dedicated case for the write phase");
            var spawn = level.getSharedSpawnPos();
            var point = new Vec3(spawn.getX() + .5, 120, spawn.getZ() + .5);
            var ship = new Rac1TravelShip(Rac1Ships.SHIP.get(), level);
            ship.moveTo(point.x, point.y, point.z, 43, 0);
            ship.setDeltaMovement(new Vec3(.5, .15, .3));
            if (!level.addFreshEntity(ship)) throw new AssertionError("Dedicated spawn failed");
            Files.writeString(marker, ship.getUUID() + "\n" + point.x + "\n" + point.y + "\n" + point.z + "\n");
        } else {
            var saved = Files.readAllLines(marker);
            var ship = level.getEntity(UUID.fromString(saved.getFirst()));
            if (ship == null && server.getTickCount() < 200) return;
            var point = new Vec3(Double.parseDouble(saved.get(1)), Double.parseDouble(saved.get(2)),
                    Double.parseDouble(saved.get(3)));
            if (!(ship instanceof Rac1TravelShip craft) || !craft.position().equals(point) || craft.getYRot() != 43
                    || craft.isVehicle() || craft.isBoosted() || craft.seatSession() != 0
                    || !craft.accessSnapshot().seatLocal().equals(ShipCockpitProfile.CURRENT.seatLocal())
                    || craft.getBbWidth() != ShipCockpitProfile.CURRENT.width()
                    || craft.getBbHeight() != ShipCockpitProfile.CURRENT.height()
                    || craft.accessPhase() != Rac1TravelShip.AccessPhase.PARKED
                    || !craft.getDeltaMovement().equals(Vec3.ZERO)) {
                throw new AssertionError("Ship failed dedicated identity/transform/clean-state restart: " + ship);
            }
        }
        Files.writeString(Path.of("ship-restart-" + stage + "-passed.txt"), "PASS\n");
        RacMcMod.LOGGER.info("Travel ship dedicated restart {} PASS", stage);
        server.halt(false);
    }
}
