package com.github.giannigr.racmc.client.bomb;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BombGloveRenderScopeTest {
    @Test void mixinBytecodeReferencesNoOtherTypeFromItsOwnedPackage() throws Exception {
        String owned = "com/github/giannigr/racmc/client/bomb/mixin/";
        String mixin = owned + "BombGlovePlayerRenderMixin";
        // Read class bytes without asking Mixin to load a class from its owned package.
        try (var bytes = getClass().getClassLoader().getResourceAsStream(mixin + ".class")) {
            assertNotNull(bytes);
            var constants = new String(bytes.readAllBytes(), StandardCharsets.ISO_8859_1);
            var references = Pattern.compile(Pattern.quote(owned) + "[\\w$/]+").matcher(constants)
                    .results().map(m -> m.group()).collect(Collectors.toSet());
            assertEquals(Set.of(mixin), references);
            assertFalse(constants.contains("LambdaMetafactory"));
            assertFalse(constants.contains("lambda$"));
            assertFalse(constants.contains("NestMembers"));
        }
        for (var helper : List.of(BombGloveRenderScope.class, BombGloveRenderScope.SkinMask.class,
                BombGloveRenderScope.PoseFrame.class)) {
            assertTrue(Modifier.isPublic(helper.getModifiers()));
            assertTrue(Modifier.isPublic(helper.getDeclaredConstructors()[0].getModifiers()));
            assertTrue(Modifier.isPublic(helper.getMethod("close").getModifiers()));
            assertFalse(helper.getPackageName().endsWith(".mixin"));
        }
    }

    @Test void nestedMasksRestorePriorFlagsEvenWhenDrawingFails() {
        var arm = new ModelPart(List.of(), Map.of());
        var sleeve = new ModelPart(List.of(), Map.of());
        sleeve.skipDraw = true;
        assertThrows(IllegalStateException.class, () -> {
            try (var outer = new BombGloveRenderScope.SkinMask(arm, sleeve)) {
                try (var inner = new BombGloveRenderScope.SkinMask(arm, sleeve)) {
                    assertTrue(arm.skipDraw && sleeve.skipDraw);
                }
                assertTrue(arm.skipDraw && sleeve.skipDraw);
                throw new IllegalStateException("render failed");
            }
        });
        assertFalse(arm.skipDraw);
        assertTrue(sleeve.skipDraw);
    }

    @Test void restoresPoseStackAndActiveArmWithoutResettingScaleAfterFailure() {
        var poses = new PoseStack();
        poses.translate(1, 2, 3);
        var start = new Matrix4f(poses.last().pose());
        var arm = new ModelPart(List.of(), Map.of());
        arm.setPos(4, 5, 6);
        arm.setRotation(1, 2, 3);
        arm.xScale = 2;
        assertThrows(IllegalStateException.class, () -> {
            try (var scope = new BombGloveRenderScope(null, 0, start, null)) {
                scope.rememberArmorArm(arm);
                arm.setPos(7, 8, 9);
                arm.setRotation(4, 5, 6);
                try (var frame = new BombGloveRenderScope.PoseFrame(poses)) {
                    poses.translate(1, 0, 0);
                    throw new IllegalStateException("draw failed");
                }
            }
        });
        assertTrue(poses.clear());
        assertEquals(start, poses.last().pose());
        assertEquals(4, arm.x);
        assertEquals(5, arm.y);
        assertEquals(6, arm.z);
        assertEquals(1, arm.xRot);
        assertEquals(2, arm.yRot);
        assertEquals(3, arm.zRot);
        assertEquals(2, arm.xScale);
    }
}
