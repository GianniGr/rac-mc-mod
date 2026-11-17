package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.ModSource;
import com.github.giannigr.racmc.block.Rac1ExplosiveFuse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Rac1CrateFlashTest {
    @Test
    void everyOriginalFrameUsesOriginalPredicateInsteadOfMinecraftTickModulo() {
        for (int tick = 0; tick < 60; tick++) {
            for (int subframe = 0; subframe < 3; subframe++) {
                int counter = Math.max(1, 179 - (3 * tick + subframe));
                boolean expected = counter % 60 > 40 || counter < 15;
                assertEquals(expected, Rac1ExplosiveFuse.red(60 - tick, subframe / 3.0F),
                        "MC tick " + tick + ", original subframe " + subframe);
            }
        }
    }

    @Test
    void flashBoundaryOccursWithinTickNotOnlyAtTwentyHertz() {
        // Elapsed 6 ticks: original counter161 (red), then160 (normal) after one frame.
        assertTrue(Rac1ExplosiveFuse.red(54, Math.nextDown(1.0F / 3)));
        assertFalse(Rac1ExplosiveFuse.red(54, 1.0F / 3));
        assertFalse(Rac1ExplosiveFuse.red(54, 2.0F / 3));
        // Elapsed 19 ticks:122,121 normal, then120 normal, next tick119 red.
        assertFalse(Rac1ExplosiveFuse.red(41, 2.0F / 3));
        assertTrue(Rac1ExplosiveFuse.red(41, 1.0F));
    }

    @Test
    void finalQuarterSecondIsSolidRedNotInventedRapidAlternation() {
        assertFalse(Rac1ExplosiveFuse.red(6, 2.0F / 3)); // counter15
        for (int remaining = 5; remaining > 0; remaining--) {
            for (int subframe = 0; subframe < 3; subframe++) {
                assertTrue(Rac1ExplosiveFuse.red(remaining, subframe / 3.0F));
            }
        }
        assertFalse(Rac1ExplosiveFuse.red(0, 0));
        assertFalse(Rac1ExplosiveFuse.red(Rac1ExplosiveFuse.TERMINAL, 0.5F));
    }

    @Test
    void invalidPartialTicksAreBoundedAndIdleNeverFlashes() {
        assertEquals(Rac1ExplosiveFuse.red(54, 0), Rac1ExplosiveFuse.red(54, -5));
        assertEquals(Rac1ExplosiveFuse.red(54, 1), Rac1ExplosiveFuse.red(54, 5));
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(Rac1ExplosiveFuse.red(54, 0), Rac1ExplosiveFuse.red(54, invalid));
        }
        assertFalse(Rac1ExplosiveFuse.red(-1, 0.5F));
        assertFalse(Rac1ExplosiveFuse.red(61, 0.5F));
    }

    @Test
    void partialSamplingDoesNotAdvanceOrResetServerFuseIncludingChain() {
        Rac1ExplosiveFuse fuse = new Rac1ExplosiveFuse();
        fuse.arm(true, 100);
        for (int frame = 0; frame < 100; frame++) {
            assertTrue(Rac1ExplosiveFuse.red(fuse.remaining(), frame / 100.0F));
        }
        assertEquals(1, fuse.remaining());
        assertEquals(100, fuse.lastTick());
        assertFalse(fuse.tick(100));
        assertTrue(fuse.tick(101));
    }

    @Test
    void ambientShaderAddsOriginalRgbToDirectionalLightAndKeepsVanillaFragment() throws IOException {
        String shader = resource("rac1_crate_ambient.vsh");
        assertTrue(shader.contains("vec3(200.0, 64.0, 64.0) / 255.0"));
        assertTrue(shader.contains("ambient + vec3(diffuse)"));
        assertTrue(shader.contains("diffuse = min(diffuse, 1.0 - MINECRAFT_AMBIENT_LIGHT)"));
        assertTrue(shader.contains("length(horizontal0 + horizontal1)"));
        assertTrue(shader.contains("diffuse -= 0.75 * whiteExcess"));
        assertTrue(shader.contains("MINECRAFT_LIGHT_POWER"));
        assertTrue(shader.contains("dot(Light0_Direction, Normal)"));
        assertTrue(shader.contains("dot(Light1_Direction, Normal)"));
        assertTrue(shader.contains("texelFetch(Sampler2, UV2 / 16, 0)"));
        assertTrue(shader.contains("texelFetch(Sampler1, UV1, 0)"));
        assertTrue(shader.contains("texCoord0 = UV0"));
        JsonObject json = JsonParser.parseString(resource("rac1_crate_ambient.json")).getAsJsonObject();
        assertEquals("racmc:rac1_crate_ambient", json.get("vertex").getAsString());
        assertEquals("minecraft:rendertype_entity_cutout_no_cull", json.get("fragment").getAsString());
        assertEquals(3, json.getAsJsonArray("samplers").size());
        // Material restoration is a RenderType switch, not mutation of global shader color/light.
        String source = ModSource.of("com/github/giannigr/racmc/client/render/Rac1MeshRenderer.java");
        assertTrue(source.contains("red ? Rac1CrateAmbientRenderType.of("));
        assertTrue(source.contains(": typeOf(texture)"));
        assertFalse(source.contains("red ? 48"));
        assertFalse(source.contains("setShaderColor"));
    }

    @Test
    void bothWorldRenderersSupplyPartialTickAndAmbientStateRetainsLightmap() {
        String ground = ModSource.of("com/github/giannigr/racmc/client/render/Rac1CrateRenderer.java");
        String air = ModSource.of("com/github/giannigr/racmc/client/render/Rac1FallingBlockRenderer.java");
        assertTrue(ground.contains("blockEntity.visualRed(partialTick)"));
        assertTrue(air.contains("explosive.visualRed(partialTick)"));
        assertFalse(ground.contains("Rac1ExplosiveFuse.red("));
        assertFalse(air.contains("Rac1ExplosiveFuse.red("));
        String state = ModSource.of(
                "com/github/giannigr/racmc/client/render/Rac1CrateAmbientRenderType.java");
        assertTrue(state.contains(".setLightmapState(LIGHTMAP)"));
        assertTrue(state.contains(".setOverlayState(OVERLAY)"));
        assertTrue(state.contains("loaded -> shader = loaded")); // Resource-reload-safe supplier.
        assertTrue(state.contains("new ShaderStateShard(() -> shader)"));
    }

    private static String resource(String name) throws IOException {
        try (InputStream stream = Rac1CrateFlashTest.class.getResourceAsStream(
                "/assets/racmc/shaders/core/" + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
