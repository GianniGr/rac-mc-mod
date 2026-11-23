package com.github.giannigr.racmc.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipCockpitOpening;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Measured exterior ratio and shared access geometry for the proportional retest. */
public final class ShipCockpitRefit {
    /** Isolated regression/test escape hatch. Both peers must use the same profile. */
    static final boolean ENABLED = !Boolean.getBoolean("racmc.shipLegacyProfile");
    public static final ShipCockpitProfile CANDIDATE =
            new ShipCockpitProfile(1.331490788, 10.40F, 4.05F,
                    new Vec3(0, 1.30, 2.12), new Vec3(0, 4.15, 2.12));
    public static final GreenShipCockpitOpening.Bounds OPENING =
            // Preserve the original central floor and components below the checked body column.
            new GreenShipCockpitOpening.Bounds(-.68F, 1.27F, 1.25F, .68F, 4.10F, 3.19F);

    private ShipCockpitRefit() {}

    public static ShipCockpitProfile activeProfile() {
        return ENABLED ? CANDIDATE : ShipCockpitProfile.CURRENT;
    }
    /**
     * Candidate three-leg path in ship space: up outside the hull, across above it,
     * then down through the opening. Reverse for exit; never use a direct diagonal shortcut.
     */
    public static List<Vec3> waypoints(Vec3 outside) {
        if (!Double.isFinite(outside.x) || !Double.isFinite(outside.y) || !Double.isFinite(outside.z)
                || outside.y > CANDIDATE.sillLocal().y) {
            throw new IllegalArgumentException("Invalid cockpit approach");
        }
        return List.of(outside, new Vec3(outside.x, CANDIDATE.sillLocal().y, outside.z),
                CANDIDATE.sillLocal(), CANDIDATE.seatLocal());
    }

    /** Provisional padded swept body volumes used to test the retained local hull, not world collision. */
    public static AABB portalColumn() {
        var seat = CANDIDATE.seatLocal();
        return new AABB(-.59, seat.y, seat.z - .63, .59, CANDIDATE.sillLocal().y, seat.z + .92);
    }
}
