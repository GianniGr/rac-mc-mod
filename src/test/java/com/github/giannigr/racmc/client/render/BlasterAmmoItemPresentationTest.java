package com.github.giannigr.racmc.client.render;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlasterAmmoItemPresentationTest {
    @Test void mediaBackedSlotsHaveNoSpriteAndFitAnOffCenterMesh() throws Exception {
        var model = JsonParser.parseString(Files.readString(Path.of(System.getProperty("racmc.projectDir"),
                "src/main/resources/assets/racmc/models/item/blaster_ammo.json"))).getAsJsonObject();
        assertEquals("builtin/entity", model.get("parent").getAsString());
        assertFalse(model.has("textures"), "Do not fall back to a placeholder sprite");
        var gson = new GsonBuilder().registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer())
                .registerTypeAdapter(ItemTransforms.class, new ItemTransforms.Deserializer()).create();
        var transforms = gson.fromJson(model.get("display"), ItemTransforms.class);
        // Deliberately offset synthetic mesh; no proprietary test geometry.
        float[] corners = {4, 12, -7, 14, 15, 1};
        for (var context : new ItemDisplayContext[]{ItemDisplayContext.GUI, ItemDisplayContext.GROUND}) {
            var poses = new PoseStack();
            var transform = transforms.getTransform(context);
            transform.apply(false, poses);
            poses.translate(-.5, -.5, -.5);
            Rac1ImportedItemDrawing.restoreSocketOrigin(poses);
            Rac1ImportedItemDrawing.fitGui(poses, corners);
            for (int i = 0; i < 8; i++) {
                var vertex = new Vector3f(corners[(i & 1) == 0 ? 0 : 3],
                        corners[(i & 2) == 0 ? 1 : 4], corners[(i & 4) == 0 ? 2 : 5]);
                poses.last().pose().transformPosition(vertex);
                assertTrue(vertex.distance(transform.translation) <= .400001f);
            }
        }
    }
}
