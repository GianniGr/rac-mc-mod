package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.junit.jupiter.api.Test;

class WrenchNativeSocketTest {
    @Test void commonContactDisplayBasisMatchesActualNativeLeftAndRightItemTransforms() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(
                System.getProperty("racmc.projectDir"),
                "src/main/resources/assets/racmc/models/item/omniwrench.json")))
                .getAsJsonObject().getAsJsonObject("display");
        var gson = new GsonBuilder().registerTypeAdapter(ItemTransform.class,
                new ItemTransform.Deserializer()).create();
        for (boolean left : new boolean[] {false, true}) {
            var transform = gson.fromJson(json.get(left ? "thirdperson_lefthand"
                    : "thirdperson_righthand"), ItemTransform.class);
            PoseStack poses = new PoseStack();
            transform.apply(left, poses);
            assertTrue(WrenchSteveRig.itemDisplay().equals(poses.last().pose(), 1.0e-6F));
        }
    }
}
