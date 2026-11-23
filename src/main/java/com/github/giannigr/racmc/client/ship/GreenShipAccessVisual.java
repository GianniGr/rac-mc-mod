package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.ship.Rac1TravelShip;
import com.github.giannigr.racmc.ship.ShipAccessPath;
import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Client adapter for P45 f0a1d79's access snapshot. It never maps access phases to flight FX. */
public final class GreenShipAccessVisual {
    // P45's documented eight-tick access clock, not the retail animation speed.
    private static final double ACCESS_TICKS = 8;

    private GreenShipAccessVisual() {}

    /** Route only controls pose. Native rider interpolation is the sole body translation. */
    static GreenShipPilotPose.Angles routed(Rac1TravelShip.AccessSnapshot snapshot, ShipAccessPath path,
                                           int checkedTicks) {
        var stand = new GreenShipPilotPose.Angles(0, 0, 0, 0, 0);
        var seated = GreenShipCockpitInterior.POSE;
        if (snapshot.phase() == Rac1TravelShip.AccessPhase.PILOTING) return seated;
        if (snapshot.phase() == Rac1TravelShip.AccessPhase.PARKED || path == null
                || path.serial() != snapshot.serial() || path.startTick() != snapshot.startTick()
                || checkedTicks < 0 || checkedTicks > path.durationTicks()) return null;
        var sill = ShipCockpitRefit.CANDIDATE.sillLocal();
        var p = path.sample(checkedTicks);
        var seat = snapshot.seatLocal();
        // Use only server-confirmed legs, including reversed prefixes and their new serials.
        if (Math.abs(p.x - seat.x) < .001 && Math.abs(p.z - seat.z) < .001) return seated;
        if (p.y < sill.y - .001) return stand;
        // Blend on the overhead crossing, not during descent onto the supports.
        double closeness = Math.clamp(1 - p.distanceTo(sill) / 1.5, 0, 1);
        if (closeness == 0) return stand;
        return new GreenShipPilotPose.Angles(seated.torso() * closeness, seated.rightArm() * closeness,
                seated.leftArm() * closeness, seated.rightLeg() * closeness, seated.leftLeg() * closeness);
    }

    public static GreenShipPilotPose.Sample sample(Rac1TravelShip.AccessSnapshot snapshot,
                                                   long levelTime, float partialTick, Vec3 sillLocal) {
        var seat = snapshot.seatLocal();
        double progress = progress(snapshot, levelTime, partialTick);
        return switch (snapshot.phase()) {
            case PARKED -> null;
            case PILOTING -> GreenShipPilotPose.seated(seat);
            case BOARDING -> snapshot.boardingLocal() == null ? null
                    : GreenShipPilotPose.board(snapshot.boardingLocal(), sillLocal, seat, progress);
            case EXITING -> snapshot.exitLocal() == null ? null
                    : GreenShipPilotPose.exit(seat, sillLocal, snapshot.exitLocal(), progress);
        };
    }

    static float facing(Rac1TravelShip.AccessSnapshot snapshot, long clock, float partialTick,
                        Vec3 sill, float shipYaw) {
        float t = (float) progress(snapshot, clock, partialTick);
        if (snapshot.boardingLocal() != null) {
            float entryYaw = heading(sill.subtract(snapshot.boardingLocal()));
            return shipYaw + Mth.rotLerp(Mth.clamp((t - .35f) / .55f, 0f, 1f), entryYaw, 0);
        }
        if (snapshot.exitLocal() != null) {
            float exitYaw = heading(snapshot.exitLocal().subtract(sill));
            return shipYaw + Mth.rotLerp(Mth.clamp((t - .15f) / .43f, 0f, 1f), 0, exitYaw);
        }
        return shipYaw;
    }

    private static double progress(Rac1TravelShip.AccessSnapshot snapshot, long clock, float partialTick) {
        return ((double) clock - snapshot.startTick() + partialTick) / ACCESS_TICKS;
    }

    private static float heading(Vec3 delta) {
        return delta.horizontalDistanceSqr() < .0001 ? 0
                : (float) (Math.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
    }
}
