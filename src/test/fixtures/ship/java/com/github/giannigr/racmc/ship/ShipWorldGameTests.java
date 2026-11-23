package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.ShipFlight.*;

import com.github.giannigr.racmc.RacMcMod;
import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.bundle.PacketAndPayloadAcceptor;

/** Actual server entities, packet handlers/state, and entity-region codec; no interactive or socket client. */
@GameTestHolder("racmc_ship")
@PrefixGameTestTemplate(false)
public final class ShipWorldGameTests {
    @GameTest(template = "empty", timeoutTicks = 155)
    public static void boostSweepShiftLandingAndObserver(GameTestHelper h) {
        var level = h.getLevel();
        var base = pad(h);
        var center = Vec3.atBottomCenterOf(base).add(0, 0, -4);
        for (int x = -5; x <= 5; x++) for (int z = -6; z <= 35; z++) {
            level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(x, 9, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 8; y++)
            level.setBlockAndUpdate(base.offset(x, y, 33), Blocks.GLASS_PANE.defaultBlockState());

        var ship = craft(level, center);
        var pilot = player(level, center.add(-2, 0, 0), "travel-pilot");
        var other = player(level, center.add(3, 0, 18), "travel-observer");
        var observer = (Sink) other.connection;
        var frames = new Frames(h, ship, pilot);
        board(h, ship, pilot);
        var access = ship.accessSnapshot();
        h.assertTrue(access.phase() == Rac1TravelShip.AccessPhase.BOARDING && access.serial() > 0
                        && ship.localToWorld(access.boardingLocal()).distanceToSqr(center.add(-2, 0, 0)) < .001
                        && ship.getControllingPassenger() == null, "Boarding seam or native ownership incorrect");
        h.assertFalse(other.startRiding(ship, true), "Forced mount bypassed seat");
        h.assertFalse(new ShipInputPayload(ship.getId(), ship.seatSession(), 1, 0, ACTIVE | UP).apply(other),
                "Observer input was admitted");
        var seen = new ArrayList<Packet<?>>();
        var tracker = new ServerEntity(level, ship, 1, true, seen::add);
        tracker.sendPairingData(other, new PacketAndPayloadAcceptor<ClientGamePacketListener>(seen::add));
        h.assertTrue(seen.stream().anyMatch(packet -> passenger(packet, ship, pilot.getId()))
                        && seen.stream().anyMatch(packet -> packet instanceof ClientboundSetEntityDataPacket data
                                && data.id() == ship.getId()), "Observer pairing lacked phase/seat");
        tracker.sendChanges();
        // GameTest's synthetic connections are not on the server connection tick list.
        // Drain and acknowledge chunks, then run native tracker admission for the observer.
        for (int tick = 1; tick < 128; tick++) h.runAtTickTime(tick, () -> {
            observer.chunkSender.sendNextChunks(other);
            level.getChunkSource().chunkMap.move(other);
        });

        h.runAtTickTime(10, () -> {
            h.assertTrue(observer.spawned.contains(ship.getId()), "Observer did not enter native ship tracking");
            h.assertTrue(ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING
                    && ship.accessSnapshot().serial() > access.serial() && ship.accessSnapshot().startTick() >= access.startTick()
                    && pilot.position().distanceToSqr(ship.pilotAnchor()) < .001
                    && ship.engineState() == ShipEngineState.SUPPORTED && observer.beats.isEmpty(),
                    "Pilot phase/seat drifted or boarding created an engine beat");
            pilot.connection.handlePlayerInput(new ServerboundPlayerInputPacket(1, 1, true, true));
            pilot.rideTick();
            h.assertTrue(pilot.getVehicle() == ship && ship.position().equals(center), "Vanilla sneak detached");
            pilot.connection.handlePlayerInput(new ServerboundPlayerInputPacket(0, 0, false, false));
            var sink = (Sink) pilot.connection;
            h.assertTrue(sink.lastTeleport > 0, "Missing native boarding teleport");
            sink.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(sink.lastTeleport));
            sink.tick();
            var forged = new Rac1TravelShip(Rac1Ships.SHIP.get(), level);
            forged.setPos(center.add(20, 8, 12));
            pilot.connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(forged));
            h.assertTrue(ship.position().equals(center) && sink.vehicleCorrections == 0,
                    "Native vehicle packet steered or corrected the server-owned craft");
            RacMcMod.LOGGER.info("[Turbo authority] forged MoveVehicle ship={} unchanged={} corrections={}",
                    ship.getId(), ship.position(), sink.vehicleCorrections);
            h.assertFalse(new ShipInputPayload(ship.getId(), ship.seatSession() - 1, 1, ship.groundSerial(), ACTIVE).apply(pilot),
                    "Stale seat admitted");
        });
        h.runAtTickTime(11, () -> frames.send(ACTIVE));
        h.runAtTickTime(12, () -> frames.send(ACTIVE | BOOST_HELD | BOOST_PRESS));
        h.runAtTickTime(13, () -> frames.send(ACTIVE | BOOST_HELD | BOOST_PRESS)); // repeat can't toggle again
        h.runAtTickTime(14, () -> frames.send(ACTIVE));
        h.runAtTickTime(15, () -> frames.send(ACTIVE | BOOST_HELD | BOOST_PRESS));
        h.runAtTickTime(16, () -> {
            h.assertFalse(ship.isBoosted(), "Fresh second tap failed to return to normal speed");
            frames.send(ACTIVE);
        });
        h.runAtTickTime(17, () -> frames.send(ACTIVE | BOOST_HELD | BOOST_PRESS));
        for (int tick = 18; tick <= 60; tick++) h.runAtTickTime(tick, () -> frames.send(ACTIVE | UP | FORWARD));
        double[] measuredMax = {0, 0, 0};
        boolean[] observedDiagonal = {false};
        boolean[] observedCeilingClip = {false};
        for (int tick = 18; tick <= 58; tick++) h.runAtTickTime(tick, () -> {
            double horizontal = ship.getDeltaMovement().horizontalDistance();
            double y = ship.getDeltaMovement().y;
            measuredMax[0] = Math.max(measuredMax[0], horizontal);
            measuredMax[1] = Math.max(measuredMax[1], y);
            observedDiagonal[0] |= ship.boostThrust().forward()
                    && ship.boostThrust().vertical() == Rac1TravelShip.VerticalBoost.CLIMB
                    && ship.engineThrust() == ShipThrust.BOOST;
            observedCeilingClip[0] |= horizontal > .5 && y == 0
                    && Math.abs(ship.getBoundingBox().maxY - (center.y + 9)) < .00001;
        });
        h.runAtTickTime(62, () -> {
            h.assertTrue(ship.isBoosted() && measuredMax[0] >= 1.499 && measuredMax[0] <= 1.500001,
                    "Turbo did not persist after release or reach the forward cap: " + measuredMax[0]);
            h.assertTrue(measuredMax[1] >= .598 && measuredMax[1] <= .600001
                            && observedDiagonal[0] && observedCeilingClip[0]
                            && ship.getBoundingBox().maxY <= center.y + 9.000001,
                    "Turbo climb cap, diagonal movement or diagonal ceiling sweep failed");
            double paneFront = base.getZ() + 33 + 7.0 / 16;
            h.assertTrue(Math.abs(ship.getBoundingBox().maxZ - paneFront) < .00001, "Boost crossed/missed thin pane");
            h.assertTrue(ship.getDeltaMovement().lengthSqr() < 1e-6, "Collision retained blocked velocity");
            h.assertTrue(observer.nearShipPackets > 2 && observer.farShipPackets > 0 && observer.vehicleCorrections == 0,
                    "Near/far observer did not use relative native tracking");
            RacMcMod.LOGGER.info("[Turbo observer] relative={} near={} far={} full={} vehicle_corrections={} "
                            + "wall_pos={} wall_velocity={}",
                    observer.relativeShipPackets, observer.nearShipPackets, observer.farShipPackets,
                    observer.fullShipPackets, observer.vehicleCorrections,
                    ship.position(), ship.getDeltaMovement());
            h.assertTrue(!ship.boostThrust().forward() && ship.boostThrust().vertical() == Rac1TravelShip.VerticalBoost.NONE,
                     "Blocked movement still advertised boost thrust");
            h.assertTrue(ship.engineThrust() == ShipThrust.OFF, "Blocked movement still advertised rear thrust");
            h.assertTrue(observer.beats.size() == 1 && observer.beats.getFirst().kind() == ShipEngineBeat.Kind.LIFTOFF
                            && observer.beats.getFirst().shipUuid().equals(ship.getUUID())
                            && ship.engineState() == ShipEngineState.AIRBORNE,
                    "Missing liftoff or spurious flight/ceiling event: " + observer.beats);
            assertNoPastEngineBeatInPairing(h, level, ship, other);
            assertUnloadedSweep(h, ship);
            assertRefitEnvelopeStopsAtUnloadedHalo(h, ship);
        });
        for (int tick = 65; tick <= 69; tick++) h.runAtTickTime(tick, () -> frames.send(ACTIVE | RIGHT));
        h.runAtTickTime(77, () -> h.assertTrue(ship.getYRot() >= 14 && ship.getYRot() <= 32
                && ship.getY() > center.y + 5.4, "Yaw freshness or neutral hover failed"));
        boolean[] sawApproach = {false};
        boolean[] sawBoostDescent = {false};
        for (int tick = 78; tick <= 114; tick++) {
            int mask = ACTIVE | SNEAK | BRAKE | (tick == 78 ? EXIT_PRESS : 0);
            h.runAtTickTime(tick, () -> {
                measuredMax[2] = Math.max(measuredMax[2], -ship.getDeltaMovement().y);
                if (ship.engineState() == ShipEngineState.APPROACH) {
                    h.assertTrue(observer.beats.size() == 1 && ship.getY() > center.y,
                            "Approach continued after contact or emitted a replayable beat");
                    if (!sawApproach[0]) assertNoPastEngineBeatInPairing(h, level, ship, other);
                    sawApproach[0] = true;
                    sawBoostDescent[0] |= ship.boostThrust().vertical() == Rac1TravelShip.VerticalBoost.DESCEND;
                }
                frames.send(mask);
            });
        }
        h.runAtTickTime(115, () -> {
            h.assertTrue(Math.abs(ship.getY() - center.y) < .002 && !ship.isBoosted()
                    && pilot.getVehicle() == ship && ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING,
                    "Holding Sneak ejected across landing or boost failed to reset");
            h.assertTrue(sawApproach[0] && sawBoostDescent[0] && measuredMax[2] >= .499 && measuredMax[2] <= .500001
                            && ship.boostThrust().vertical() == Rac1TravelShip.VerticalBoost.NONE
                            && ship.engineState() == ShipEngineState.SUPPORTED
                            && ship.engineThrust() == ShipThrust.OFF
                            && observer.beats.size() == 2
                            && observer.beats.getLast().kind() == ShipEngineBeat.Kind.TOUCHDOWN
                            && observer.beats.getLast().serial() > observer.beats.getFirst().serial()
                            && observer.beats.getLast().serverTick() > observer.beats.getFirst().serverTick(),
                    "No verified approach or touchdown: " + observer.beats);
            assertNoPastEngineBeatInPairing(h, level, ship, other);
            frames.send(ACTIVE);
        });
        h.runAtTickTime(116, () -> frames.send(ACTIVE | SNEAK | EXIT_PRESS));
        h.runAtTickTime(119, () -> h.assertTrue(ship.accessPhase() == Rac1TravelShip.AccessPhase.EXITING
                && ship.accessSnapshot().exitLocal() != null && pilot.getVehicle() == ship
                && ShipCockpitProfile.outsideHull(ship.getBoundingBox(),
                        ship.localToWorld(ship.accessSnapshot().exitLocal()), pilot.getDimensions(Pose.STANDING)),
                "Missing checked exit phase/destination outside the hull"));
        h.runAtTickTime(130, () -> {
            h.assertTrue(!pilot.isPassenger() && ship.accessPhase() == Rac1TravelShip.AccessPhase.PARKED
                    && pilot.position().distanceToSqr(ship.position()) >= 1.8, "Ground repress failed safe exit");
            h.assertTrue(ship.engineState() == ShipEngineState.UNVERIFIED && observer.beats.size() == 2,
                    "Exit did not clear engine state or invented a beat");
            seen.clear();
            tracker.sendChanges();
            h.assertTrue(seen.stream().anyMatch(packet -> passenger(packet, ship, -1)), "Observer did not clear seat");
            clean(level, ship, pilot, other);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void ratioRouteMovesRealPilotAboveHullAndBackToGround(GameTestHelper h) {
        var level = h.getLevel();
        var center = Vec3.atBottomCenterOf(pad(h));
        var ship = new Rac1TravelShip(Rac1Ships.SHIP.get(), level, ShipCockpitRefit.CANDIDATE);
        ship.setPos(center);
        level.addFreshEntity(ship);
        Vec3 outside = center.add(5.70, 0, 2.12);
        var pilot = player(level, center.add(3.20, 0, 2.12), "ratio-route");
        pilot.setYRot(-90);
        h.assertFalse(ship.interact(pilot, InteractionHand.MAIN_HAND).consumesAction(),
                "A player facing away from the visible side started boarding");
        pilot.setPos(outside);
        pilot.setOnGround(true);
        pilot.setYRot(90);
        var frames = new Frames(h, ship, pilot);
        board(h, ship, pilot);
        var route = ship.accessPath();
        h.assertTrue(route != null && route.durationTicks() == 24 && !route.rollback()
                        && pilot.position().distanceToSqr(outside) < 1e-9,
                "Boarding snapped to seat instead of beginning on the supported stand");
        h.runAtTickTime(4, () -> h.assertTrue(pilot.getY() > center.y && pilot.getY() < center.y + 4.15
                && Math.abs(pilot.getX() - outside.x) < 1e-6,
                "First leg did not rise outside the hull"));
        h.runAtTickTime(12, () -> {
            h.assertTrue(Math.abs(pilot.getY() - center.y - 4.15) < 1e-6
                            && pilot.getX() < outside.x && pilot.getX() > center.x
                            && pilot.position().distanceToSqr(ship.localToWorld(route.sample(ship.accessPathProgress()))) < 1e-9,
                    "Second leg did not cross above the roof at the checked path progress");
            h.assertTrue(ship.getEntityData().getNonDefaultValues().stream().anyMatch(value ->
                            value.value() instanceof CompoundTag tag && route.equals(ShipAccessPath.decode(tag))),
                    "A fresh tracker would not receive the server route, clock and serial");
        });
        h.runAtTickTime(20, () -> h.assertTrue(Math.abs(pilot.getX() - center.x) < 1e-6
                && Math.abs(pilot.getZ() - center.z - 2.12) < 1e-6
                && pilot.getY() > center.y + 1.30 && pilot.getY() < center.y + 4.15,
                "Third leg did not descend through the cockpit column"));
        h.runAtTickTime(28, () -> {
            h.assertTrue(ship.accessPath() == null && ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING
                    && pilot.position().distanceToSqr(ship.pilotAnchor()) < 1e-9
                    && Math.abs(pilot.getEyeY() - center.y - 2.92) < 1e-6,
                    "Mount completion did not join the shared seat and eye");
            frames.send(ACTIVE);
        });
        h.runAtTickTime(29, () -> frames.send(ACTIVE | SNEAK | EXIT_PRESS));
        Vec3[] exit = {null};
        h.runAtTickTime(33, () -> {
            var outgoing = ship.accessPath();
            h.assertTrue(outgoing != null && !outgoing.rollback() && outgoing.durationTicks() == 24
                    && outgoing.waypoints().getFirst().equals(ShipCockpitRefit.CANDIDATE.seatLocal())
                    && outgoing.waypoints().get(1).equals(ShipCockpitRefit.CANDIDATE.sillLocal()),
                    "Ordinary exit did not reverse the overhead legs");
            var standLocal = outgoing.waypoints().getLast();
            h.assertTrue(Math.abs(standLocal.x) < ship.getBbWidth() / 2
                    && Math.abs(standLocal.z - ShipCockpitRefit.CANDIDATE.seatLocal().z) <= .90,
                    "Exit still went to the oversized sweep boundary instead of beside the cockpit");
            exit[0] = ship.localToWorld(outgoing.waypoints().getLast());
        });
        h.runAtTickTime(35, () -> level.setBlockAndUpdate(BlockPos.containing(exit[0]).above(),
                Blocks.STONE.defaultBlockState()));
        h.runAtTickTime(38, () -> h.assertTrue(ship.accessPath() != null && ship.accessPath().rollback()
                && pilot.getVehicle() == ship, "Obstructed exit did not roll back to the seat"));
        h.runAtTickTime(48, () -> {
            h.assertTrue(ship.accessPath() == null && ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING
                    && pilot.position().distanceToSqr(ship.pilotAnchor()) < 1e-9,
                    "Blocked exit did not safely restore the seated pilot");
            level.setBlockAndUpdate(BlockPos.containing(exit[0]).above(), Blocks.AIR.defaultBlockState());
            frames.send(ACTIVE);
        });
        h.runAtTickTime(49, () -> frames.send(ACTIVE | SNEAK | EXIT_PRESS));
        h.runAtTickTime(78, () -> {
            h.assertTrue(!pilot.isPassenger() && ship.accessPath() == null
                    && pilot.position().distanceToSqr(exit[0]) < 1e-9
                    && ShipSpace.safeAccessStand(level, pilot, pilot.position()),
                    "Exit failed the checked stand or left a route reserved");
            clean(level, ship, pilot);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void ratioBoardingObstructionRollsBackAndLogoutClearsPath(GameTestHelper h) {
        var level = h.getLevel();
        var base = pad(h);
        var center = Vec3.atBottomCenterOf(base);
        var ship = new Rac1TravelShip(Rac1Ships.SHIP.get(), level, ShipCockpitRefit.CANDIDATE);
        ship.setPos(center);
        level.addFreshEntity(ship);
        Vec3 outside = center.add(5.70, 0, 0);
        var pilot = player(level, outside, "ratio-rollback");
        var footing = BlockPos.containing(outside).below();
        var remote = outside.add(100_000, 0, 0);
        var remoteChunk = new ChunkPos(BlockPos.containing(remote));
        h.assertTrue(level.getChunkSource().getChunkNow(remoteChunk.x, remoteChunk.z) == null
                        && !ShipSpace.clearAccessLegs(level, pilot, List.of(remote, remote.add(0, 4.15, 0)))
                        && level.getChunkSource().getChunkNow(remoteChunk.x, remoteChunk.z) == null,
                "Access attempted to load a missing route chunk");
        level.setBlockAndUpdate(footing, Blocks.AIR.defaultBlockState());
        h.assertFalse(ship.interact(pilot, InteractionHand.MAIN_HAND).consumesAction(), "Boarded from missing footing");
        level.setBlockAndUpdate(footing, Blocks.STONE.defaultBlockState());
        board(h, ship, pilot);
        int firstSerial = ship.accessPath().serial();
        var blocked = base.offset(0, 4, 2);
        h.runAtTickTime(4, () -> level.setBlockAndUpdate(blocked, Blocks.STONE.defaultBlockState()));
        h.runAtTickTime(7, () -> h.assertTrue(ship.accessPath() != null && ship.accessPath().rollback()
                && ship.accessPath().serial() > firstSerial && pilot.getVehicle() == ship,
                "Mid-route obstruction did not publish checked reverse legs"));
        h.runAtTickTime(16, () -> {
            h.assertTrue(!pilot.isPassenger() && ship.accessPath() == null
                    && pilot.position().distanceToSqr(outside) < 1e-9,
                    "Aborted ingress left the seat/route reserved or pilot off the supported stand");
            level.setBlockAndUpdate(blocked, Blocks.AIR.defaultBlockState());
            board(h, ship, pilot);
        });
        h.runAtTickTime(21, () -> {
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(pilot));
            h.assertTrue(!pilot.isPassenger() && ship.accessPath() == null
                            && pilot.position().distanceToSqr(outside) < 1e-9,
                    "Logout left a transit reservation or lost the checked berth");
            clean(level, ship, pilot);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void nativeRiderFeetAndEyeShareThePresentationOriginAtEveryHeading(GameTestHelper h) {
        var level = h.getLevel();
        var center = Vec3.atBottomCenterOf(pad(h));
        var ship = craft(level, center);
        var pilot = player(level, center.add(-2, 0, 0), "cockpit-pilot");
        try {
            for (float yaw : new float[]{0, 90, 180, 270}) {
                ship.setYRot(yaw);
                var from = center.add(-2, 0, 0);
                pilot.setPos(from);
                pilot.setYRot(29);
                pilot.setXRot(18);
                board(h, ship, pilot);
                var access = ship.accessSnapshot();
                Vec3 seat = ship.localToWorld(access.seatLocal());
                h.assertTrue(pilot.position().distanceToSqr(seat) < 1e-10
                                && pilot.getEyePosition().distanceToSqr(seat.add(0, pilot.getEyeHeight(), 0)) < 1e-10
                                && pilot.getYRot() == 29 && pilot.getXRot() == 18
                                && ship.localToWorld(access.boardingLocal()).distanceToSqr(from) < 1e-10,
                        "Native feet/eye, free look, or boarding origin diverged at yaw " + yaw);
                h.assertTrue(ShipCockpitProfile.containsPilot(ship.getBoundingBox(), seat,
                        pilot.getDimensions(Pose.STANDING)), "Pilot capsule escaped the swept hull");
                pilot.stopRiding();
                h.assertTrue(ShipCockpitProfile.outsideHull(ship.getBoundingBox(), pilot.position(),
                        pilot.getDimensions(Pose.STANDING)), "Grounded exit landed inside the hull");
            }
            h.succeed();
        } finally {
            clean(level, ship, pilot);
        }
    }

    @GameTest(template = "empty")
    public static void brakeAtFullTurboWithoutChangingTheToggle(GameTestHelper h) {
        var level = h.getLevel();
        var center = Vec3.atBottomCenterOf(pad(h)).add(0, 0, -4);
        var ship = craft(level, center);
        var pilot = player(level, center.add(-2, 0, 0), "turbo-brake");
        var frames = new Frames(h, ship, pilot);
        board(h, ship, pilot);
        h.runAtTickTime(11, () -> frames.send(ACTIVE));
        h.runAtTickTime(12, () -> frames.send(ACTIVE | BOOST_HELD | BOOST_PRESS));
        for (int tick = 13; tick <= 37; tick++) {
            int flags = ACTIVE | FORWARD | (tick < 28 ? UP : 0);
            h.runAtTickTime(tick, () -> frames.send(flags));
        }
        double[] zAtBrake = {0};
        h.runAtTickTime(38, () -> {
            h.assertTrue(ship.isBoosted() && ship.getDeltaMovement().horizontalDistance() >= 1.499
                            && ship.boostThrust().forward(),
                    "Brake case never reached unobstructed Turbo pace");
            zAtBrake[0] = ship.getZ();
            RacMcMod.LOGGER.info("[Turbo brake] tick={} pos={} velocity={}",
                    level.getGameTime(), ship.position(), ship.getDeltaMovement());
            frames.send(ACTIVE | FORWARD | BRAKE);
        });
        h.runAtTickTime(39, () -> {
            h.assertTrue(ship.isBoosted() && !ship.boostThrust().forward()
                            && Math.abs(ship.getZ() - zAtBrake[0] - .525) < .001,
                    "S failed to brake Turbo in the same tick, or toggled the mode");
            RacMcMod.LOGGER.info("[Turbo braked] tick={} pos={} velocity={}",
                    level.getGameTime(), ship.position(), ship.getDeltaMovement());
            frames.send(ACTIVE | BRAKE | BOOST_HELD | BOOST_PRESS);
        });
        h.runAtTickTime(41, () -> {
            h.assertTrue(!ship.isBoosted() && ship.getDeltaMovement().horizontalDistance() <= .40
                            && ship.boostThrust().vertical() == Rac1TravelShip.VerticalBoost.NONE,
                    "Second Ctrl failed to restore normal caps/presentation");
            clean(level, ship, pilot);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void proposedOverheadAccessRejectsTerrainInEveryLeg(GameTestHelper h) {
        var level = h.getLevel();
        var base = pad(h);
        var origin = Vec3.atBottomCenterOf(base);
        var pilot = player(level, origin.add(5.70, 0, 0), "cockpit-route");
        var points = ShipCockpitRefit.waypoints(new Vec3(5.70, 0, 0)).stream()
                .map(origin::add).toList();
        h.assertTrue(ShipSpace.clearAccessLegs(level, pilot, points), "Clear overhead approach rejected");
        for (var obstruction : List.of(base.offset(6, 2, 0), base.offset(3, 5, 1), base.offset(0, 3, 2))) {
            level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            h.assertFalse(ShipSpace.clearAccessLegs(level, pilot, points), "Accepted a block in an access leg");
            level.setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState());
        }
        level.removePlayerImmediately(pilot, Entity.RemovalReason.DISCARDED);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void engineProofRejectsNearnessSideSupportAndUnsafeFloor(GameTestHelper h) {
        var level = h.getLevel();
        var base = pad(h);
        var center = Vec3.atBottomCenterOf(base);
        var ship = craft(level, center);
        h.assertTrue(ShipEnginePhysics.contact(level, ship, ship.getBoundingBox()) == ShipEngineState.SUPPORTED,
                "Flat centre contact missing");
        ship.setPos(center.add(0, .01, 0));
        h.assertTrue(ShipEnginePhysics.contact(level, ship, ship.getBoundingBox()) == ShipEngineState.AIRBORNE,
                "Proximity was mistaken for actual contact");
        level.setBlockAndUpdate(base.below(), Blocks.AIR.defaultBlockState());
        ship.setPos(center.add(0, .4, 0));
        ship.setDeltaMovement(new Vec3(0, -.13, 0));
        Vec3 motion = new Vec3(0, -.13, 0);
        h.assertFalse(ShipEnginePhysics.verifiedApproach(level, ship, ACTIVE | SNEAK, motion, motion),
                "Side-only collision projected as a touchdown");
        level.setBlockAndUpdate(base.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        h.assertFalse(ShipEnginePhysics.verifiedApproach(level, ship, ACTIVE | SNEAK, motion, motion),
                "Hazardous floor projected as a touchdown");
        ship.setPos(center);
        h.assertTrue(ShipEnginePhysics.contact(level, ship, ship.getBoundingBox()) == ShipEngineState.UNVERIFIED,
                "Magma accepted as verified support");
        ship.discard();
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 95)
    public static void blockedExitLogoutDeathAndEntityRegionRoundTrip(GameTestHelper h) {
        var level = h.getLevel();
        var base = pad(h);
        var point = Vec3.atBottomCenterOf(base);
        var ship = craft(level, point);
        var pilot = player(level, point.add(0, 0, -2.5), "travel-first");
        var next = player(level, point.add(0, 0, 2.5), "travel-next");
        var frames = new Frames(h, ship, pilot);
        board(h, ship, pilot);
        int session = ship.seatSession();
        for (var direction : Direction.Plane.HORIZONTAL) exitWall(level, base, direction, true);
        h.runAtTickTime(11, () -> frames.send(ACTIVE));
        h.runAtTickTime(12, () -> frames.send(ACTIVE | SNEAK | EXIT_PRESS));
        h.runAtTickTime(15, () -> {
            h.assertTrue(ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING && pilot.getVehicle() == ship,
                    "Unsafe ground ejected");
            exitWall(level, base, Direction.WEST, false);
            frames.send(ACTIVE);
        });
        h.runAtTickTime(16, () -> frames.send(ACTIVE | SNEAK | EXIT_PRESS));
        h.runAtTickTime(19, () -> {
            h.assertTrue(ship.accessPhase() == Rac1TravelShip.AccessPhase.EXITING
                    && ship.localToWorld(ship.accessSnapshot().exitLocal()).x < point.x, "Wrong exit selection");
            exitWall(level, base, Direction.WEST, true);
        });
        h.runAtTickTime(28, () -> {
            h.assertTrue(ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING && pilot.getVehicle() == ship,
                    "Exit did not revalidate");
            exitWall(level, base, Direction.WEST, false);
            frames.send(ACTIVE);
        });
        h.runAtTickTime(29, () -> frames.send(ACTIVE | BOOST_HELD | BOOST_PRESS));
        for (int tick = 30; tick <= 43; tick++) h.runAtTickTime(tick, () -> frames.send(ACTIVE | UP));
        h.runAtTickTime(45, () -> {
            h.assertTrue(ship.isBoosted() && ship.getY() > point.y + 1, "Setup: not boosted airborne");
            var playerSave = pilot.saveWithoutId(new CompoundTag());
            h.assertFalse(playerSave.contains("RootVehicle"), "Player save duplicated world-owned vehicle");
            h.assertTrue(playerSave.getList("Pos", 6).getDouble(1) == point.y, "Save missed the valid ground berth");
            var restored = regionRoundTrip(h, ship);
            h.assertTrue(restored.getUUID().equals(ship.getUUID()) && restored.position().equals(ship.position())
                            && restored.getYRot() == ship.getYRot() && !restored.isVehicle()
                            && restored.seatSession() == 0 && restored.groundSerial() == 0 && !restored.isBoosted()
                            && restored.getDeltaMovement().equals(Vec3.ZERO)
                            && restored.accessPhase() == Rac1TravelShip.AccessPhase.PARKED
                            && restored.accessSnapshot().seatLocal().equals(ShipCockpitProfile.CURRENT.seatLocal())
                            && restored.engineState() == ShipEngineState.UNVERIFIED && restored.engineBeat() == null,
                    "Entity-region reload kept stale authority or lost identity/transform");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(pilot));
            h.assertTrue(!pilot.isPassenger() && !ship.isBoosted() && ship.accessPhase() == Rac1TravelShip.AccessPhase.PARKED
                    && pilot.getX() < point.x - 1 && Math.abs(pilot.getY() - point.y) < .001, "Logout did not recover safely");
            ship.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(level.addFreshEntity(restored) && !next.startRiding(restored, true),
                    "Reload did not publish a single empty craft");
            restored.setPos(point);
            exitWall(level, base, Direction.SOUTH, false);
            board(h, restored, next);
            h.runAfterDelay(12, () -> {
                h.assertFalse(new ShipInputPayload(ship.getId(), session, 60, 0, ACTIVE | UP).apply(next),
                        "Pre-unload craft ID authorized new pilot");
                next.kill();
            });
            h.runAfterDelay(17, () -> {
                h.assertTrue(!next.isAlive() && !next.isPassenger() && !restored.isVehicle() && !restored.isBoosted(),
                        "Death left restored craft occupied");
                clean(level, restored, pilot, next);
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void creativePlacementValidatesFaceSpaceReachAndDuplicates(GameTestHelper h) {
        var level = h.getLevel();
        var base = pad(h).below();
        var point = Vec3.atBottomCenterOf(base.above());
        var placer = player(level, point.add(0, 0, -2.6), "travel-builder");
        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Rac1Ships.PLACEMENT_ITEM.get()));
        try {
            h.assertFalse(place(placer, base, Direction.UP), "Survival item placed");
            placer.setGameMode(GameType.CREATIVE);
            h.assertFalse(place(placer, base, Direction.NORTH), "Side-face item placed");
            h.assertFalse(place(placer, base.offset(60, 0, 0), Direction.UP), "Remote item placed");
            level.setBlockAndUpdate(base.above(2), Blocks.STONE.defaultBlockState());
            h.assertFalse(place(placer, base, Direction.UP), "Obstructed item placed");
            level.setBlockAndUpdate(base.above(2), Blocks.AIR.defaultBlockState());
            h.assertTrue(place(placer, base, Direction.UP) && !place(placer, base, Direction.UP), "Missing place/dedup");
            var ships = level.getEntitiesOfClass(Rac1TravelShip.class, placer.getBoundingBox().inflate(6));
            h.assertTrue(ships.size() == 1 && ships.getFirst().position().equals(point)
                            && ships.getFirst().getBbWidth() == ShipCockpitProfile.CURRENT.width()
                            && ships.getFirst().getBbHeight() == ShipCockpitProfile.CURRENT.height(),
                    "Placement did not use the registered cockpit envelope");
            ships.getFirst().discard();
            h.succeed();
        } finally {
            level.removePlayerImmediately(placer, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(templateNamespace = "racmc_ship_ratio", template = "empty")
    public static void ratioSideVanillaPickAndRightClickAtMultipleHeadings(GameTestHelper h) {
        var level = h.getLevel();
        var center = Vec3.atBottomCenterOf(pad(h));
        for (float yaw : new float[]{0, 37, 90, 225}) {
            var ship = craft(level, center);
            ship.setYRot(yaw);
            var seat = ShipCockpitRefit.CANDIDATE.seatLocal();
            int side = yaw == 225 ? -1 : 1;
            double x = side * ShipSideAccess.exitSide(yaw, .6F);
            var pilot = player(level, ship.localToWorld(new Vec3(x, 0, seat.z)), "side-reach");
            boolean continueWithExit = false;
            try {
                h.assertTrue(!pilot.isSpectator() && !pilot.getAbilities().flying, "Native test player not on foot");
                var worldInward = ship.localToWorld(new Vec3(-side * .12, 0, 0)).subtract(center);
                for (int i = 0; i < 8; i++) pilot.move(MoverType.SELF, worldInward);
                Vec3 eye = pilot.getEyePosition();
                Vec3 stand = pilot.position();
                Vec3 aim = ship.localToWorld(new Vec3(side * ShipSideAccess.HALF_BEAM, eye.y - center.y, seat.z));
                Vec3 direction = aim.subtract(eye).normalize();
                pilot.setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
                pilot.setYHeadRot(pilot.getYRot());
                pilot.setXRot((float)-Math.toDegrees(Math.asin(direction.y)));
                double reach = pilot.entityInteractionRange();
                Vec3 end = eye.add(pilot.getViewVector(1).scale(reach));
                var hit = ProjectileUtil.getEntityHitResult(pilot, eye, end,
                        pilot.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                        e -> e.isPickable() && !e.isSpectator(), reach * reach);
                h.assertTrue(hit != null && hit.getEntity() == ship && pilot.canInteractWithEntity(ship, 0),
                        "Vanilla pick/reach did not target the hull at yaw " + yaw);
                h.assertFalse(ShipCockpitProfile.outsideHull(ship.getBoundingBox(), stand,
                        pilot.getDimensions(Pose.STANDING)), "Did not exercise the old side rejection");
                float across = pilot.getYRot();
                pilot.setYRot(across + 180);
                pilot.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(hit.getEntity(),
                        false, InteractionHand.MAIN_HAND));
                h.assertFalse(pilot.isPassenger(), "A broad-box pick while looking away started boarding");
                pilot.setYRot(across);
                pilot.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(hit.getEntity(),
                        false, InteractionHand.MAIN_HAND));
                h.assertTrue(pilot.getVehicle() == ship && ship.accessPath() != null
                                && pilot.position().distanceToSqr(stand) < 1e-9,
                        "Normal side interaction did not start the checked route at yaw " + yaw
                                + " stand=" + stand + " feedback=" + ((Sink)pilot.connection).lastFeedback
                                + " levelTarget=" + (level.getEntity(ship.getId()) == ship));
                if (yaw == 225) {
                    var frames = new Frames(h, ship, pilot);
                    var berth = ship.localToWorld(new Vec3(x, 0, seat.z));
                    h.runAtTickTime(26, () -> {
                        h.assertTrue(ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING
                                && pilot.position().distanceToSqr(ship.pilotAnchor()) < 1e-8,
                                "Side boarding did not settle at the shared seat");
                        frames.send(ACTIVE);
                    });
                    h.runAtTickTime(27, () -> frames.send(ACTIVE | SNEAK | EXIT_PRESS));
                    h.runAtTickTime(29, () -> {
                        var route = ship.accessPath();
                        h.assertTrue(route != null && ship.accessPhase() == Rac1TravelShip.AccessPhase.EXITING
                                && ship.localToWorld(route.waypoints().getLast()).distanceToSqr(berth) < 1e-8,
                                "The checked exit did not return to the boarding side");
                    });
                    h.runAtTickTime(54, () -> {
                        h.assertTrue(!pilot.isPassenger() && ship.accessPath() == null
                                && pilot.position().distanceToSqr(berth) < 1e-8,
                                "Side exit did not release the pilot on the supported berth");
                        clean(level, ship, pilot);
                        h.succeed();
                    });
                    continueWithExit = true;
                }
            } finally {
                if (!continueWithExit) clean(level, ship, pilot);
            }
        }
    }

    @GameTest(templateNamespace = "racmc_ship_ratio", template = "empty")
    public static void nonFlyingWalkersHitFrontAndSidesButNotEmptyTerrainSweepCorners(GameTestHelper h) {
        var level = h.getLevel();
        var center = Vec3.atBottomCenterOf(pad(h));
        for (float yaw : new float[]{0, 37, 90, 225}) {
            var ship = craft(level, center);
            ship.setYRot(yaw);
            var pilot = player(level, center.add(0, 0, 6), "hull-walker");
            try {
                h.assertTrue(!pilot.isSpectator() && !pilot.getAbilities().flying, "Walker must be on foot");
                for (Vec3 local : List.of(new Vec3(4.7, 0, 2.12), new Vec3(-4.7, 0, 2.12), new Vec3(0, 0, 5.9))) {
                    Vec3 start = ship.localToWorld(local);
                    pilot.setPos(start);
                    var toward = local.x == 0 ? new Vec3(0, 0, -.12) : new Vec3(-Math.signum(local.x) * .12, 0, 0);
                    var step = ship.localToWorld(toward).subtract(center);
                    for (int i = 0; i < 24; i++) pilot.move(MoverType.SELF, step);
                    var within = pilot.position().subtract(center).yRot(yaw * ((float)Math.PI / 180));
                    double offset = local.x == 0 ? within.z : Math.abs(within.x);
                    double edge = local.x == 0 ? ShipHullCollision.BOW : ShipSideAccess.HALF_BEAM;
                    h.assertTrue(offset >= edge && offset <= edge + .7,
                            "Walking was not blocked close to the visible hull at yaw " + yaw + ": " + within);
                }
                if (yaw == 0) {
                    var empty = ship.localToWorld(new Vec3(4, 0, 4));
                    pilot.setPos(empty);
                    pilot.move(MoverType.SELF, new Vec3(0, 0, -.12));
                    h.assertTrue(pilot.position().distanceToSqr(empty.add(0, 0, -.12)) < 1e-9,
                            "Empty yaw-square corner was incorrectly solid");
                    pilot.setPos(center.add(0, 0, ShipHullCollision.BOW + .4));
                    ship.setDeltaMovement(new Vec3(0, 0, 1));
                    ship.fly(level, ACTIVE);
                    h.assertTrue(ship.position().equals(center) && ship.getDeltaMovement().equals(Vec3.ZERO),
                            "Moving hull passed through an unrelated stationary walker");
                }
            } finally {
                clean(level, ship, pilot);
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "racmc_ship_ratio", template = "empty")
    public static void creativeEmptyHandAttackOnlyDiscardsTargetedUnoccupiedHull(GameTestHelper h) {
        var level = h.getLevel();
        var center = Vec3.atBottomCenterOf(pad(h));
        var ship = craft(level, center);
        var attacker = player(level, center.add(3.6, 0, 0), "ship-removal");
        var pilot = player(level, center.add(0, 0, -5.70), "ship-removal-pilot");
        attacker.setYRot(90);
        attacker.setYHeadRot(90);
        var packet = ServerboundInteractPacket.createAttackPacket(ship, false);
        attacker.setGameMode(GameType.SURVIVAL);
        attacker.connection.handleInteract(packet);
        h.assertFalse(ship.isRemoved(), "Survival punch removed the ship");
        attacker.setGameMode(GameType.CREATIVE);
        double beyond = ShipSideAccess.HALF_BEAM + attacker.entityInteractionRange() + .2;
        attacker.setPos(center.add(beyond, 0, 0));
        h.assertTrue(attacker.canInteractWithEntity(ship, 0), "Case did not exercise the coarse native range");
        attacker.connection.handleInteract(packet);
        h.assertFalse(ship.isRemoved(), "Attack reached the broad box but was outside hull reach");
        attacker.setPos(center.add(3.6, 0, -4.8));
        attacker.connection.handleInteract(packet);
        h.assertFalse(ship.isRemoved(), "Attack into an empty sweep-box corner removed the ship");
        attacker.setPos(center.add(4.6, 0, 0));
        BlockPos wall = BlockPos.containing(center.add(3.5, 1, 0));
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        attacker.connection.handleInteract(packet);
        h.assertFalse(ship.isRemoved(), "Punch through an intervening block removed the ship");
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        attacker.setPos(center.add(3.6, 0, 0));
        var eye = attacker.getEyePosition();
        double reach = attacker.entityInteractionRange();
        var end = eye.add(attacker.getViewVector(1).scale(reach));
        var hit = ProjectileUtil.getEntityHitResult(attacker, eye, end,
                attacker.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                e -> e.isPickable() && !e.isSpectator(), reach * reach);
        h.assertTrue(hit != null && hit.getEntity() == ship, "Native aim did not pick the visible hull");
        board(h, ship, pilot);
        attacker.connection.handleInteract(packet);
        h.assertTrue(!ship.isRemoved() && pilot.getVehicle() == ship && ship.accessPath() != null
                && ((Sink)attacker.connection).lastFeedback.contains("cannot be removed"),
                "Creative punch did not preserve the boarding pilot and explain rejection");
        h.runAtTickTime(26, () -> {
            h.assertTrue(ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING, "Pilot did not finish boarding");
            attacker.connection.handleInteract(packet);
            h.assertTrue(!ship.isRemoved() && pilot.getVehicle() == ship, "Creative punch removed an occupied ship");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(pilot));
            attacker.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(hit.getEntity(), false));
            h.assertTrue(ship.isRemoved() && !ship.isVehicle() && !pilot.isPassenger()
                    && ship.accessPath() == null, "Unoccupied Creative punch did not remove and detach the ship");
        });
        h.runAtTickTime(28, () -> {
            h.assertTrue(level.getEntity(ship.getId()) == null
                    && level.getEntitiesOfClass(ItemEntity.class, ship.getBoundingBox().inflate(2)).isEmpty(),
                    "Discard left a tracked hull or dropped items");
            clean(level, ship, pilot, attacker);
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "racmc_ship_ratio", template = "empty")
    public static void ratioPlacementFromReachablePadEdgeAndNativeSeat(GameTestHelper h) {
        var level = h.getLevel();
        var clicked = pad(h).below();
        var center = clicked.south(5);
        var point = Vec3.atBottomCenterOf(center.above());
        var placer = player(level, Vec3.atBottomCenterOf(clicked.above()).add(0, 0, -2.6), "ratio-builder");
        placer.setGameMode(GameType.CREATIVE);
        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Rac1Ships.PLACEMENT_ITEM.get()));
        h.assertTrue(ShipCockpitRefit.activeProfile() == ShipCockpitRefit.CANDIDATE, "Ratio runtime not enabled");
        level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
        h.assertFalse(place(placer, clicked, Direction.UP), "Parked above unsupported center");
        level.setBlockAndUpdate(center, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(center.offset(4, 1, 3), Blocks.STONE.defaultBlockState());
        h.assertFalse(place(placer, clicked, Direction.UP), "Accepted obstruction beyond old collider");
        level.setBlockAndUpdate(center.offset(4, 1, 3), Blocks.AIR.defaultBlockState());
        h.assertTrue(place(placer, clicked, Direction.UP) && !place(placer, clicked, Direction.UP),
                "Near edge did not place one craft");
        var ship = level.getEntitiesOfClass(Rac1TravelShip.class, placer.getBoundingBox().inflate(10)).getFirst();
        h.assertTrue(ship.position().equals(point)
                && ship.getBbWidth() == 10.40f && ship.getBbHeight() == 4.05f, "Incorrect native refit footprint");
        Vec3 outside = point.add(0, 0, -5.70);
        placer.setPos(outside);
        placer.setOnGround(true);
        board(h, ship, placer);
        var route = ship.accessPath();
        h.assertTrue(route != null && route.durationTicks() == 24, "Default entity had no 24-tick route");
        h.runAtTickTime(25, () -> {
            h.assertTrue(ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING
                    && placer.position().distanceToSqr(ship.pilotAnchor()) < .0001, "Native seat did not settle");
            clean(level, ship, placer);
            h.succeed();
        });
    }

    private static Rac1TravelShip regionRoundTrip(GameTestHelper h, Rac1TravelShip ship) {
        var level = h.getLevel();
        var chunk = ship.chunkPosition();
        Path dir = Path.of("ship-region-" + ship.getUUID());
        try {
            try (var storage = storage(level, dir)) {
                storage.storeEntities(new ChunkEntities<>(chunk, List.of(ship)));
                storage.flush(true);
            }
            try (var storage = storage(level, dir)) {
                var entities = storage.loadEntities(chunk).get(10, TimeUnit.SECONDS).getEntities().toList();
                h.assertTrue(entities.size() == 1 && entities.getFirst() instanceof Rac1TravelShip,
                        "Missing/duplicated world region entity");
                return (Rac1TravelShip) entities.getFirst();
            }
        } catch (Exception error) {
            throw new AssertionError("Native entity region save/reopen failed", error);
        }
    }

    private static EntityStorage storage(ServerLevel level, Path root) {
        return new EntityStorage(new SimpleRegionStorage(new RegionStorageInfo("ship-gametest", level.dimension(), "entities"),
                root, DataFixers.getDataFixer(), true, DataFixTypes.ENTITY_CHUNK), level, Util.backgroundExecutor());
    }

    private static void assertUnloadedSweep(GameTestHelper h, Rac1TravelShip ship) {
        var level = h.getLevel();
        var chunks = level.getChunkSource();
        var origin = ship.chunkPosition();
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
            var own = new ChunkPos(origin.x + dx, origin.z + dz);
            var missing = new ChunkPos(own.x + 1, own.z);
            if (chunks.getChunkNow(own.x, own.z) == null || chunks.getChunkNow(missing.x, missing.z) != null) continue;
            Vec3 original = ship.position();
            Vec3 originalVelocity = ship.getDeltaMovement();
            float yaw = ship.getYRot();
            Vec3 edge = new Vec3(missing.getMinBlockX() - 2.95, 180, own.getMiddleBlockZ() + .5);
            var atEdge = ship.getBoundingBox().move(edge.subtract(original));
            if (!ShipSpace.loaded(level, atEdge)
                    || !ShipSpace.loaded(level, atEdge.expandTowards(.70, 0, 0))
                    || ShipSpace.loaded(level, atEdge.expandTowards(BOOST_SPEED, 0, 0))) continue;
            String before = chunks.getChunkDebugData(missing);
            try {
                ship.setPos(edge);
                ship.setYRot(-90);
                Vec3 descending = new Vec3(BOOST_SPEED, -.27, 0);
                ship.setDeltaMovement(descending);
                h.assertFalse(ShipEnginePhysics.verifiedApproach(level, ship, ACTIVE | FORWARD | SNEAK,
                                descending, descending),
                        "Approach looked through an unavailable chunk halo");
                ship.setDeltaMovement(new Vec3(BOOST_SPEED, 0, 0));
                ship.fly(level, ACTIVE | FORWARD);
                h.assertTrue(ship.position().equals(edge) && ship.getDeltaMovement().equals(Vec3.ZERO)
                        && ship.engineState() == ShipEngineState.UNVERIFIED
                        && chunks.getChunkNow(missing.x, missing.z) == null && chunks.getChunkDebugData(missing).equals(before),
                        "Boost advanced into halo or altered missing chunk state");
                double previousX = ship.getX();
                for (int i = 0; i < 10; i++) {
                    ship.fly(level, ACTIVE | FORWARD);
                    h.assertTrue(ship.getX() >= previousX && ShipSpace.loaded(level, ship.getBoundingBox()),
                            "Repeated boundary stop reversed the ship or entered the missing halo");
                    previousX = ship.getX();
                }
                RacMcMod.LOGGER.info("[Turbo boundary] tick={} initial={} final={} velocity={} missing={}",
                        level.getGameTime(), edge, ship.position(), ship.getDeltaMovement(), chunks.getChunkDebugData(missing));
            } finally {
                ship.setPos(original);
                ship.setYRot(yaw);
                ship.setDeltaMovement(originalVelocity);
            }
            return;
        }
        throw new AssertionError("No live loaded/unloaded chunk boundary in GameTest search radius");
    }

    private static void assertRefitEnvelopeStopsAtUnloadedHalo(GameTestHelper h, Rac1TravelShip ship) {
        var level = h.getLevel();
        var chunks = level.getChunkSource();
        var origin = ship.chunkPosition();
        var profile = ShipCockpitRefit.CANDIDATE;
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
            var own = new ChunkPos(origin.x + dx, origin.z + dz);
            var missing = new ChunkPos(own.x + 1, own.z);
            if (chunks.getChunkNow(own.x, own.z) == null || chunks.getChunkNow(missing.x, missing.z) != null) continue;
            var edge = new Vec3(missing.getMinBlockX() - profile.width() / 2 - 2,
                    180, own.getMiddleBlockZ() + .5);
            var envelope = net.minecraft.world.entity.EntityDimensions.fixed(profile.width(), profile.height())
                    .makeBoundingBox(edge);
            if (!ShipSpace.loaded(level, envelope)
                    || ShipSpace.loaded(level, envelope.expandTowards(BOOST_SPEED, BOOST_CLIMB, 0))) continue;
            Vec3 before = ship.position(), velocity = ship.getDeltaMovement();
            var box = ship.getBoundingBox();
            float yaw = ship.getYRot();
            try {
                ship.setPos(edge);
                ship.setBoundingBox(envelope);
                ship.setYRot(-90);
                ship.setDeltaMovement(new Vec3(BOOST_SPEED, BOOST_CLIMB, 0));
                ship.fly(level, ACTIVE | FORWARD | UP);
                h.assertTrue(ship.position().equals(edge) && ship.getDeltaMovement().equals(Vec3.ZERO)
                                && ship.engineState() == ShipEngineState.UNVERIFIED
                                && chunks.getChunkNow(missing.x, missing.z) == null,
                        "Turbo movement used an unloaded halo with the refit envelope");
            } finally {
                ship.setPos(before);
                ship.setBoundingBox(box);
                ship.setYRot(yaw);
                ship.setDeltaMovement(velocity);
            }
            return;
        }
        throw new AssertionError("No loaded edge for proposed 10.40-block hull");
    }

    private static void assertNoPastEngineBeatInPairing(GameTestHelper h, ServerLevel level, Rac1TravelShip ship,
                                                         ServerPlayer other) {
        var packets = new ArrayList<Packet<?>>();
        var tracker = new ServerEntity(level, ship, 1, true, packets::add);
        tracker.sendPairingData(other, new PacketAndPayloadAcceptor<ClientGamePacketListener>(packets::add));
        var replica = new Rac1TravelShip(Rac1Ships.SHIP.get(), level);
        for (var packet : packets) {
            if (packet instanceof ClientboundSetEntityDataPacket metadata) {
                replica.getEntityData().assignValues(metadata.packedItems());
            }
        }
        h.assertTrue(replica.engineState() == ship.engineState()
                        && replica.isBoosted() == ship.isBoosted()
                        && replica.boostThrust().equals(ship.boostThrust()) && replica.engineBeat() == null
                        && packets.stream().noneMatch(packet -> packet instanceof ClientboundCustomPayloadPacket payload
                                && payload.payload() instanceof ShipEngineBeat),
                "Fresh tracking did not get current metadata, or replayed a past beat");
    }

    private static boolean place(ServerPlayer player, BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, .5), face, pos, false);
        return Rac1Ships.PLACEMENT_ITEM.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction();
    }

    private static boolean passenger(Packet<?> packet, Rac1TravelShip ship, int pilotId) {
        return packet instanceof ClientboundSetPassengersPacket list && list.getVehicle() == ship.getId()
                && (pilotId == -1 ? list.getPassengers().length == 0
                        : list.getPassengers().length == 1 && list.getPassengers()[0] == pilotId);
    }

    static final class Frames {
        final GameTestHelper h;
        final Rac1TravelShip ship;
        final ServerPlayer pilot;
        int sequence;
        Frames(GameTestHelper h, Rac1TravelShip ship, ServerPlayer pilot) {
            this.h = h;
            this.ship = ship;
            this.pilot = pilot;
        }
        void send(int flags) {
            var packet = new ShipInputPayload(ship.getId(), ship.seatSession(), ++sequence, ship.groundSerial(), flags);
            h.assertTrue(packet.apply(pilot), "Current seat packet dropped unexpectedly: " + packet);
            h.assertFalse(packet.apply(pilot), "Replay/duplicate server tick accepted");
        }
    }

    static BlockPos pad(GameTestHelper h) {
        var base = h.absolutePos(new BlockPos(7, 2, 7));
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++)
            h.getLevel().setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        return base;
    }

    private static void exitWall(ServerLevel level, BlockPos base, Direction direction, boolean blocked) {
        var block = blocked ? Blocks.STONE : Blocks.AIR;
        for (int y = 0; y < 3; y++) level.setBlockAndUpdate(base.relative(direction, 2).above(y), block.defaultBlockState());
    }

    static Rac1TravelShip craft(ServerLevel level, Vec3 point) {
        var ship = new Rac1TravelShip(Rac1Ships.SHIP.get(), level);
        ship.setPos(point);
        level.addFreshEntity(ship);
        return ship;
    }

    static void board(GameTestHelper h, Rac1TravelShip ship, ServerPlayer pilot) {
        h.assertTrue(ship.interact(pilot, InteractionHand.MAIN_HAND).consumesAction() && pilot.getVehicle() == ship,
                "Could not board " + ship.position() + " from " + pilot.position());
    }

    static ServerPlayer player(ServerLevel level, Vec3 at, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND) {
            private final io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel();
            @Override public io.netty.channel.Channel channel() { return channel; }
        };
        player.connection = new Sink(level, connection, player, profile);
        player.setPos(at);
        player.setOnGround(true);
        level.addNewPlayer(player);
        player.connection.resetPosition();
        return player;
    }

    private static final class Sink extends ServerGamePacketListenerImpl {
        private int lastTeleport;
        private String lastFeedback = "";
        private int vehicleCorrections;
        private int relativeShipPackets;
        private int fullShipPackets;
        private int nearShipPackets;
        private int farShipPackets;
        private final List<ShipEngineBeat> beats = new ArrayList<>();
        private final List<Integer> spawned = new ArrayList<>();
        Sink(ServerLevel level, Connection connection, ServerPlayer player, GameProfile profile) {
            super(level.getServer(), connection, player, CommonListenerCookie.createInitial(profile, false));
        }
        @Override public void send(Packet<?> packet) {
            if (packet instanceof ClientboundSystemChatPacket message) lastFeedback = message.content().getString();
            if (packet instanceof ClientboundPlayerPositionPacket position) lastTeleport = position.getId();
            if (packet instanceof ClientboundChunkBatchFinishedPacket) chunkSender.onChunkBatchReceivedByClient(64);
            if (packet instanceof ClientboundBundlePacket bundle) bundle.subPackets().forEach(this::send);
            if (packet instanceof ClientboundAddEntityPacket spawn) spawned.add(spawn.getId());
            if (packet instanceof ClientboundMoveVehiclePacket) vehicleCorrections++;
            if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()
                    && move.getEntity(player.level()) instanceof Rac1TravelShip ship) {
                relativeShipPackets++;
                if (ship.position().distanceTo(player.position()) <= 16) nearShipPackets++;
                else farShipPackets++;
                if (relativeShipPackets <= 4 || ship.getDeltaMovement().horizontalDistance() > 1.499
                        && relativeShipPackets % 5 == 0) {
                    RacMcMod.LOGGER.info("[Turbo native packet] tick={} server_pos={} server_v={} kind={} delta={},{},{}",
                            player.level().getGameTime(), ship.position(), ship.getDeltaMovement(),
                            packet.getClass().getSimpleName(), move.getXa(), move.getYa(), move.getZa());
                }
            }
            if (packet instanceof ClientboundTeleportEntityPacket absolute
                    && player.level().getEntity(absolute.getId()) instanceof Rac1TravelShip) fullShipPackets++;
            if (packet instanceof ClientboundCustomPayloadPacket event && event.payload() instanceof ShipEngineBeat beat) {
                beats.add(beat);
            }
        }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
        @Override public boolean isAcceptingMessages() { return true; }
    }

    static void clean(ServerLevel level, Rac1TravelShip ship, ServerPlayer... players) {
        ship.discard();
        for (var player : players) level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }
}
