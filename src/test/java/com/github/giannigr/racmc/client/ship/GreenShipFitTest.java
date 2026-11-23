package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GreenShipFitTest {
    @Test
    void roofProbesUseShipLocalSideAndForwardCoordinatesAndDoNotInventCoverage() {
        var roof = List.of(new GreenShipGlassPass.Quad(0, 1, 2, 3));
        var points = List.of(new Vec3(-.5, 1, -.2), new Vec3(.5, 1, -.2),
                new Vec3(-.5, 2, .8), new Vec3(.5, 2, .8));
        assertEquals(1.2, GreenShipFit.roof(roof, points, 0, 0), 1e-8);
        assertEquals(1.4, GreenShipFit.roof(roof, points, .2, .2), 1e-8);
        assertEquals(Double.NEGATIVE_INFINITY, GreenShipFit.roof(roof, points, .51, .2));
    }
}
