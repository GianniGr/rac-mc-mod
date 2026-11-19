package com.github.giannigr.racmc.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.github.giannigr.racmc.item.weapon.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent MC source pin6d1662dd: actual JSON slot/fallback, ModelPart and ItemTransform.
 * Source-up/forward assertions are separate from muzzle-coordinate agreement.
 */
class BlasterWorldHandContractTest {
    static ItemTransforms display() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(System.getProperty("racmc.projectDir"),
                "src/main/resources/assets/racmc/models/item/blaster.json"))).getAsJsonObject();
        return new GsonBuilder().registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer())
                .registerTypeAdapter(ItemTransforms.class, new ItemTransforms.Deserializer())
                .create().fromJson(json.get("display"), ItemTransforms.class);
    }

    static Matrix4f world(ItemTransforms display, boolean left, boolean slim, boolean crouch) {
        var stack = new PoseStack();
        if (crouch) stack.translate(0, -.125, 0); // PlayerRenderer render offset, entity scale1
        stack.mulPose(Axis.YP.rotationDegrees(180));
        stack.scale(-1, -1, 1);
        stack.scale(.9375f, .9375f, .9375f);
        stack.translate(0, -1.501, 0);
        var arm = new ModelPart(List.of(), Map.of());
        arm.setPos(left ? 5 : -5, crouch ? 5.2f : 2, 0);
        arm.xRot = -(float) (Math.PI / 2) + (crouch ? .4f : 0);
        // PlayerModel.translateToHand applies the half-unit only during hand transformation.
        if (slim) arm.x += left ? -.5f : .5f;
        arm.translateAndRotate(stack);
        stack.mulPose(Axis.XP.rotationDegrees(-90));
        stack.mulPose(Axis.YP.rotationDegrees(180));
        stack.translate((left ? -1.0 : 1.0) / 16, .125, -.625);
        display.getTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(left, stack);
        stack.translate(-.5, -.5, -.5);
        stack.translate(.5, .5, .5);
        return new Matrix4f(stack.last().pose());
    }

    static HandPoseSnapshot snapshot(boolean left, boolean slim, boolean crouch) {
        return new HandPoseSnapshot(new WeaponDefinition.Vector(0, crouch ? -.125 : 0, 0),
                0, 1, left,
                new WeaponDefinition.Vector((left ? 5 : -5) + (slim ? (left ? -.5 : .5) : 0),
                        crouch ? 5.2f : 2, 0),
                new WeaponDefinition.Vector(Math.toDegrees(-(float) (Math.PI / 2) + (crouch ? .4f : 0)), 0, 0),
                new WeaponDefinition.Vector(1, 1, 1), .9375, 1.501);
    }

    @Test void actualVanillaWorldChainKeepsImportedUpForwardAndBackendAgreesInBothPhysicalHands() throws Exception {
        var display = display();
        for (boolean slim : new boolean[]{false, true})
            for (boolean crouch : new boolean[]{false, true})
                for (boolean left : new boolean[]{false, true}) {
                    var matrix = world(display, left, slim, crouch);
                    var up = matrix.transformDirection(new Vector3f(1, 0, 0)).normalize();
                    var forward = matrix.transformDirection(new Vector3f(0, 0, 1)).normalize();
                    System.out.println("Actual JSON/vanilla left=" + left + " slim=" + slim
                            + " crouch=" + crouch + " up=" + up + " forward=" + forward);
                    assertTrue(up.y > (crouch ? .9 : .99), "Source +X is inverted in physical world hand");
                    assertTrue(forward.z > (crouch ? .9 : .99), "Source barrel +Z is not forward");
                    var pose = snapshot(left, slim, crouch);
                    for (var source : new WeaponDefinition.Vector[]{new WeaponDefinition.Vector(0, 0, 0),
                            new WeaponDefinition.Vector(.2305, -.03, .6335),
                            new WeaponDefinition.Vector(1, 0, 0), new WeaponDefinition.Vector(0, 0, 1)}) {
                        var drawn = matrix.transformPosition(new Vector3f((float) source.x(), (float) source.y(), (float) source.z()));
                        var physical = HandTransform.point(Rac1BlasterDefinition.INSTANCE, pose, source);
                        assertEquals(drawn.x, physical.x(), 4e-7);
                        assertEquals(drawn.y, physical.y(), 4e-7);
                        assertEquals(drawn.z, physical.z(), 4e-7);
                    }
                }
    }
}
