package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GreenShipPlayerRenderTest {
    private static final GreenShipPilotPose.Angles PILOT =
            GreenShipPilotPose.seated(Vec3.ZERO).angles();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothSteveModelsAndSkinLayersArePosedAndRestored(boolean slim) {
        var model = player(slim);
        var pose = new PoseStack();
        pose.translate(9, -3, 2);
        var before = new Matrix4f(pose.last().pose());
        var pilot = new Object();
        var observer = new Object();
        model.rightArm.xRot = .25f;
        model.rightSleeve.xRot = .45f;
        GreenShipPlayerRender.renderScoped(pilot, model, PILOT, pose, new Vec3(1, .5, -1), 40, local -> {
            assertNotSame(pose, local);
            assertNotEquals(before, local.last().pose());
            GreenShipPlayerRender.afterSetup(observer, model);
            assertEquals(.25f, model.rightArm.xRot);
            GreenShipPlayerRender.afterSetup(pilot, model);
            assertEquals(Math.toRadians(PILOT.rightArm()), model.rightArm.xRot, .0001);
            assertEquals(model.rightArm.xRot, model.rightSleeve.xRot);
            assertEquals(model.rightLeg.xRot, model.rightPants.xRot);
            assertEquals(model.body.xRot, model.jacket.xRot);
        });
        assertEquals(.25f, model.rightArm.xRot);
        assertEquals(.45f, model.rightSleeve.xRot);
        assertEquals(before, pose.last().pose());
        GreenShipPlayerRender.afterSetup(pilot, model);
        assertEquals(.25f, model.rightArm.xRot);
    }

    @Test
    void cancelledNestedAndExceptionScopesCannotLeakStateOrPoseStack() {
        var model = player(false);
        var parent = new PoseStack();
        var pilot = new Object();
        model.body.xRot = .75f;
        GreenShipPlayerRender.renderScoped(pilot, model, PILOT, parent, Vec3.ZERO, 0, ignored -> {
            // Cancellation before native setupAnim.
        });
        assertEquals(.75f, model.body.xRot);
        assertThrows(IllegalStateException.class, () ->
                GreenShipPlayerRender.renderScoped(pilot, model, PILOT, parent, Vec3.ZERO, 0, isolated -> {
                    GreenShipPlayerRender.afterSetup(pilot, model);
                    float outer = model.body.xRot;
                    GreenShipPlayerRender.renderScoped(pilot, model,
                            GreenShipPilotPose.board(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, .45).angles(),
                            isolated, Vec3.ZERO, 10, nested -> {
                                GreenShipPlayerRender.afterSetup(pilot, model);
                                assertNotEquals(outer, model.body.xRot);
                            });
                    assertEquals(outer, model.body.xRot);
                    // Simulate failure inside a third-party layer after an unmatched push.
                    isolated.pushPose();
                    isolated.translate(200, 200, 200);
                    throw new IllegalStateException("synthetic failure");
                }));
        assertTrue(parent.clear());
        assertEquals(new Matrix4f(), parent.last().pose());
        assertEquals(.75f, model.body.xRot);
        GreenShipPlayerRender.afterSetup(pilot, model);
        assertEquals(.75f, model.body.xRot);
    }

    private static PlayerModel<?> player(boolean slim) {
        return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),
                64, 64).bakeRoot(), slim);
    }
}
