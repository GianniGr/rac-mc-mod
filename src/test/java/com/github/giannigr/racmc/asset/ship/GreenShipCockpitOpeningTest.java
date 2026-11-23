package com.github.giannigr.racmc.asset.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import org.junit.jupiter.api.Test;

class GreenShipCockpitOpeningTest {
    private static final GreenShipCockpitOpening.Bounds BOX =
            new GreenShipCockpitOpening.Bounds(-1, -1, -1, 1, 1, 1);

    @Test
    void removesContainedFaceAndPreservesUntouchedExterior() {
        assertEquals(0, GreenShipCockpitOpening.subtract(mesh(
                new float[]{0, 0, 0, .5f, 0, 0, 0, .5f, 0}), BOX).triangleCount());
        var untouched = mesh(new float[]{-2, 0, 0, -2, 1, 0, -2, 1, 1});
        assertEquals(untouched, GreenShipCockpitOpening.subtract(untouched, BOX));
    }

    @Test
    void crossingFaceCutsAtBoundaryAndInterpolatesUvAndMaterial() {
        var sliced = GreenShipCockpitOpening.subtract(mesh(
                new float[]{0, 0, 0, 2, 0, 0, 0, .5f, 0}), BOX);
        assertEquals(1, sliced.triangleCount());
        assertEquals(7, sliced.materials()[0]);
        int boundary = 0;
        for (int i = 0; i < sliced.vertexCount(); i++) {
            if (sliced.positions()[i * 3] == 1) {
                assertEquals(.5f, sliced.uvs()[i * 2], 1e-6);
                boundary++;
            }
        }
        assertEquals(2, boundary);
    }

    private static Rac1Mesh mesh(float[] positions) {
        return new Rac1Mesh(positions, new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1},
                new float[]{0, 0, 1, 0, 0, 1}, new int[]{0, 1, 2}, new int[]{7});
    }
}
