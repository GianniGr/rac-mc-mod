package com.github.giannigr.racmc.client.rescue;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Rac1DecorativePodItemModelTest {
    @Test
    void itemModelSelectsCustomRenderingAndFitsEachDisplayContext() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/racmc/models/item/decorative_crashed_pod.json")) {
            assertNotNull(stream);
            var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("builtin/entity", model.get("parent").getAsString());
            assertFalse(model.has("elements"));
            assertFalse(model.has("textures"));
            var display = model.getAsJsonObject("display");
            float guiScale = display.getAsJsonObject("gui").getAsJsonArray("scale").get(0).getAsFloat();
            for (String mode : new String[]{"ground", "fixed", "firstperson_righthand", "firstperson_lefthand",
                    "thirdperson_righthand", "thirdperson_lefthand"}) {
                float scale = display.getAsJsonObject(mode).getAsJsonArray("scale").get(0).getAsFloat();
                assertTrue(scale > 0 && scale < guiScale, mode);
            }
        }
    }
}
