package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.client.bomb.BombGloveCuff.ArmFit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BombGloveArmMaskTest {
    @Test void clipsRealVanillaCuboidsButDoesNotMutateModelParts() {
        for (var fit : ArmFit.values()) {
            for (boolean sleeve : new boolean[]{false, true}) {
                var part = part(fit, sleeve);
                part.setPos(5, fit.pivotY, 0);
                part.xRot = 1;
                var prepared = BombGloveArmMask.prepare(part, fit, sleeve, 6).orElseThrow();
                assertEquals(5, prepared.faces().size());
                var vertices = prepared.faces().stream().flatMap(f -> f.vertices().stream()).toList();
                assertEquals(20, vertices.size());
                assertTrue(vertices.stream().allMatch(v -> v.y() <= 6));
                assertEquals(8, vertices.stream().filter(v -> v.y() == 6).count());
                assertEquals(5, part.x);
                assertEquals(1, part.xRot);
                assertTrue(part.visible);
                assertFalse(part.skipDraw);
            }
        }
    }

    @Test void hiddenSleeveStaysHiddenAndCustomOrWrongProfileIsNotMasked() {
        var part = part(ArmFit.SLIM_LEFT, true);
        assertTrue(BombGloveArmMask.prepare(part, ArmFit.STANDARD_LEFT, true, 6).isEmpty());
        assertTrue(BombGloveArmMask.prepare(part, ArmFit.SLIM_LEFT, false, 6).isEmpty());
        part.visible = false;
        assertTrue(BombGloveArmMask.prepare(part, ArmFit.SLIM_LEFT, true, 6).orElseThrow().faces().isEmpty());
        assertFalse(part.visible);
    }

    private static ModelPart part(ArmFit fit, boolean sleeve) {
        float grow = sleeve ? .25f : 0;
        var cube = new ModelPart.Cube(40, 16, fit.minX, -2, -2, fit.width, 12, 4,
                grow, grow, grow, false, 64, 64, Set.of(Direction.values()));
        return new ModelPart(List.of(cube), Map.of());
    }
}
