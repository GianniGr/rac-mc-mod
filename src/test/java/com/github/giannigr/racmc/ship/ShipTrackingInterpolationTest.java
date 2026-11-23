package com.github.giannigr.racmc.ship;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShipTrackingInterpolationTest {
    @Test
    void exactOldNearTrackerAndCameraOrderReproduceTheTurboSawtooth() {
        var packets = new RelativeTracker();
        var smoothing = new ShipTrackingInterpolation();
        Vec3 oldPosition = Vec3.ZERO;
        Vec3 fixedPosition = Vec3.ZERO;
        Vec3 fixedRider = new Vec3(0, .54, 0);
        var legacySteps = new ArrayList<Double>();
        int oldSnaps = 0;
        for (int tick = 1; tick <= 12; tick++) {
            var packet = packets.pos(new Vec3(0, 0, tick * 1.5));
            Vec3 oldRiderStart = oldPosition;
            Vec3 oldHullStart = oldPosition;
            // 4686d4a lerpTo runs before ClientLevel takes the hull/rider tick snapshots.
            if (oldPosition.distanceToSqr(packet.target) > 4) {
                oldPosition = packet.target;
                oldHullStart = packet.target;
                oldSnaps++;
            } else {
                oldPosition = oldPosition.lerp(packet.target, .5);
            }
            double oldFrameGap = oldHullStart.lerp(oldPosition, .5).z - oldRiderStart.lerp(oldPosition, .5).z;
            legacySteps.add(oldPosition.z - oldRiderStart.z);
            if (tick % 2 == 0) assertEquals(1.125, oldFrameGap, 1e-9);

            assertFalse(smoothing.accept(fixedPosition, packet.target, 0), "A legal packet was classified as a warp");
            Vec3 fixedStart = fixedPosition;
            Vec3 fixedRiderStart = fixedRider;
            fixedPosition = smoothing.advance(fixedPosition, 0).position();
            fixedRider = fixedPosition.add(0, .54, 0);
            double fixedFrameGap = fixedStart.lerp(fixedPosition, .5).z - fixedRiderStart.lerp(fixedRider, .5).z;
            assertEquals(0, fixedFrameGap, 1e-9);
            if (tick > 7) assertEquals(1.5, fixedPosition.z - fixedStart.z, .01);
            System.out.printf(Locale.ROOT,
                    "near tick=%d server_z=%.3f server_vz=1.500 packet=Pos dz=%d tracker_z=%.3f "
                    + "old_z=%.3f old_vz=%.3f old_hull_minus_camera_z=%.3f new_z=%.3f new_vz=%.3f new_gap=%.3f%n",
                    tick, tick * 1.5, packet.dz, packet.target.z, oldPosition.z, legacySteps.getLast(),
                    oldFrameGap, fixedPosition.z, fixedPosition.z - fixedStart.z, fixedFrameGap);
        }
        assertEquals(List.of(.75, 2.25, .75, 2.25), legacySteps.subList(0, 4));
        assertEquals(6, oldSnaps);
    }

    @Test
    void farObserverOrderedBurstDoesNotRebaseAndStopsAtLastSample() {
        var packets = new RelativeTracker();
        var smoothing = new ShipTrackingInterpolation();
        Vec3 pos = Vec3.ZERO;
        for (int tick = 1; tick <= 4; tick++) {
            long first = tick * 3L - 2;
            for (long serverTick = first; serverTick < first + 3; serverTick++) {
                var packet = packets.pos(new Vec3(0, 0, serverTick * 1.5));
                assertFalse(smoothing.accept(pos, packet.target, 0), "A burst changes delivery time, not motion bounds");
                System.out.printf(Locale.ROOT,
                        "far client_tick=%d server_tick=%d server_vz=1.500 packet=Pos dz=%d target_z=%.3f client_z=%.3f%n",
                        tick, serverTick, packet.dz, packet.target.z, pos.z);
            }
            var frame = smoothing.advance(pos, 0);
            assertTrue(frame.position().z > pos.z && frame.position().z <= tick * 4.5);
            System.out.printf(Locale.ROOT, "far client_tick=%d rendered_z=%.3f visual_vz=%.3f rebase=%b%n",
                    tick, frame.position().z, frame.position().z - pos.z, frame.rebase());
            pos = frame.position();
        }
        pos = smoothing.advance(pos, 0).position();
        assertEquals(new Vec3(0, 0, 18), pos);
        assertEquals(pos, smoothing.advance(pos, 0).position(), "No extrapolation through a missing update");
        assertTrue(smoothing.accept(pos, new Vec3(0, 0, 90), 70));
        var warp = smoothing.advance(pos, 0);
        assertTrue(warp.rebase());
        assertEquals(90, warp.position().z);
        assertEquals(70, warp.yaw());
    }

    private record PacketTarget(Vec3 target, long dz) {}

    private static final class RelativeTracker {
        final VecDeltaCodec server = new VecDeltaCodec();
        final VecDeltaCodec client = new VecDeltaCodec();

        PacketTarget pos(Vec3 position) {
            long dz = server.encodeZ(position);
            var decoded = client.decode(server.encodeX(position), server.encodeY(position), dz);
            server.setBase(position);
            client.setBase(decoded);
            return new PacketTarget(decoded, dz);
        }
    }
}
