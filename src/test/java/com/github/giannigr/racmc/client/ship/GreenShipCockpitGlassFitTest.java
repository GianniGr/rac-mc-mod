package com.github.giannigr.racmc.client.ship;

import static com.github.giannigr.racmc.client.ship.GreenShipCockpitGlassFit.Fit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.giannigr.racmc.client.ship.GreenShipCockpitGlassFit.Triangle;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GreenShipCockpitGlassFitTest {
    private static final Triangle FRONT = triangle(-1, 2, 3.775, 1, 2, 3.775, 0, 4, 3.775);

    @Test
    void distinguishesMissingGlassAndMissingForwardWindowFromValidWindshield() {
        assertEquals(NO_GLASS, GreenShipCockpitGlassFit.check(null, null));
        assertEquals(BLOCKED, GreenShipCockpitGlassFit.check(List.of()));
        assertEquals(CLEAR, GreenShipCockpitGlassFit.check(List.of(FRONT)));
    }

    @Test
    void rejectsAHeadIntersectionBetweenTriangleVerticesBeyondTheOlderAuditWidth() {
        var side = triangle(.61, 1.5, 1, .61, 4, 1, .61, 2.6, 4);
        assertEquals(BLOCKED, GreenShipCockpitGlassFit.check(List.of(FRONT, side)));
        var clearSide = triangle(.70, 1.5, 1, .70, 4, 1, .70, 2.6, 4);
        assertEquals(CLEAR, GreenShipCockpitGlassFit.check(List.of(FRONT, clearSide)));
    }

    private static Triangle triangle(double ax, double ay, double az, double bx, double by, double bz,
                                     double cx, double cy, double cz) {
        return new Triangle(new Vec3(ax, ay, az), new Vec3(bx, by, bz), new Vec3(cx, cy, cz));
    }
}
