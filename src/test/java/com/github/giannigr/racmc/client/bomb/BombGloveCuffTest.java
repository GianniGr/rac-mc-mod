package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.client.bomb.BombGloveCuff.ArmFit;
import com.github.giannigr.racmc.client.bomb.BombGloveCuff.Vertex;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BombGloveCuffTest {
    @Test
    void vanillaFitsHaveDistinctWidthsPivotsAndMirroredCenters() {
        assertEquals(4, ArmFit.STANDARD_RIGHT.width);
        assertEquals(3, ArmFit.SLIM_RIGHT.width);
        assertEquals(2f, ArmFit.STANDARD_LEFT.pivotY);
        assertEquals(2.5f, ArmFit.SLIM_LEFT.pivotY);
        assertEquals(-ArmFit.STANDARD_LEFT.centerX(), ArmFit.STANDARD_RIGHT.centerX());
        assertEquals(-ArmFit.SLIM_LEFT.centerX(), ArmFit.SLIM_RIGHT.centerX());
    }

    @Test
    void skinAndInflatedSleeveMeetAtSamePlaneWithoutStretchingUv() {
        // Synthetic cuff. The original mesh, not this fixture, determines the actual cutoff.
        for (ArmFit fit : ArmFit.values()) {
            for (float dilation : new float[]{0, .25f}) {
                float minX = fit.minX - dilation;
                float maxX = fit.minX + fit.width + dilation;
                float start = -2 - dilation;
                float end = 10 + dilation;
                var face = List.of(new Vertex(minX, start, -2, 0, 0),
                        new Vertex(minX, end, -2, 0, 1), new Vertex(maxX, end, -2, 1, 1),
                        new Vertex(maxX, start, -2, 1, 0));
                var clipped = BombGloveCuff.upperFace(face, 6);
                float v = (6 - start) / (end - start);
                assertEquals(List.of(face.get(0), new Vertex(minX, 6, -2, 0, v),
                        new Vertex(maxX, 6, -2, 1, v), face.get(3)), clipped);
                assertEquals(clipped.reversed(), BombGloveCuff.upperFace(face.reversed(), 6));
                assertEquals(end, face.get(1).y()); // Shared vanilla geometry is unchanged.
            }
        }
    }

    @Test
    void preservesShoulderFaceButRemovesCoveredHandFace() {
        var shoulder = horizontalFace(-2);
        assertEquals(shoulder, BombGloveCuff.upperFace(shoulder, 6));
        assertTrue(BombGloveCuff.upperFace(horizontalFace(10.25f), 6).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BombGloveCuff.upperFace(shoulder, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> BombGloveCuff.upperFace(shoulder, 10));
    }

    private static List<Vertex> horizontalFace(float y) {
        return List.of(new Vertex(-3, y, -2, 0, 0), new Vertex(1, y, -2, 1, 0),
                new Vertex(1, y, 2, 1, 1), new Vertex(-3, y, 2, 0, 1));
    }
}
