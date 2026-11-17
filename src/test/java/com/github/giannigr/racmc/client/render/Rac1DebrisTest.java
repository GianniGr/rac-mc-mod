package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class Rac1DebrisTest {
    @Test void faceMaterialAndWrappedTriangleUvAreNotWhiteModelFallback() {
        var mesh = mesh();
        var front = mesh.sample(Direction.SOUTH, .5);
        var back = mesh.sample(Direction.NORTH, .5);
        assertEquals(0, front.material());
        assertEquals(1, back.material());
        assertEquals(0xffb43219, mesh.texture(front.material()).pixel(0, 0));
        assertEquals(0xff1964c8, mesh.texture(back.material()).pixel(0, 0));
        assertTrue(front.u0() >= 0 && front.u1() <= 1);
        assertEquals(1f / 16, front.u1() - front.u0(), 1e-6);
        assertNotEquals(front.u0(), front.v0(), "Imported UVs, not a constant white icon");
    }

    @Test void destructionUsesBothOriginalMaterialsAndHandlesBoundaries() {
        var mesh = mesh();
        assertEquals(0, mesh.sample(null, 0).material());
        assertEquals(1, mesh.sample(null, 1).material());
        assertNotNull(mesh.sample(Direction.UP, .2), "Unrepresented face falls back to surface sample");
        assertNotNull(mesh.sample(null, Double.NaN));
    }

    @Test void transparentAndDegeneratePacketsFailGracefully() {
        var texture = new Rac1Texture(1, 1, new byte[4]);
        var mesh = new Rac1DebrisMesh(new float[]{0,0,0, 1,0,0, 0,1,0},
                new float[6], new int[]{0,1,2}, new int[]{0}, new Rac1Texture[]{texture});
        assertNull(mesh.sample(null, .5));
        var empty = new Rac1DebrisMesh(new float[9], new float[6], new int[]{0,1,2},
                new int[]{0}, new Rac1Texture[]{opaque(1,2,3)});
        assertNull(empty.sample(null, .5));
    }

    @Test void everyCrateAndVendorSharesLazyCacheLifecycleWithoutPerParticleUpload() {
        var registrations = new ArrayList<String>();
        var releases = new ArrayList<String>();
        var materials = new Rac1DebrisMaterials<String>(new Rac1DebrisMaterials.Upload<>() {
            public String register(String name, Rac1Texture texture) {
                registrations.add(name);
                return name + registrations.size();
            }
            public void release(String texture) { releases.add(texture); }
        });
        assertNull(materials.of("ammo_crate", null, 0));
        var content = content();
        materials.source(content);
        var old = materials.of("ammo_crate", null, 0);
        assertNotNull(old);
        for (int i = 0; i < 64; i++) {
            assertEquals(old.texture, materials.of("ammo_crate", null, 0).texture);
        }
        assertEquals(1, registrations.size());
        for (var definition : Rac1Crates.definitions()) {
            assertNotNull(materials.of(definition.key(), Direction.SOUTH, 0));
        }
        assertNotNull(materials.of("gadgetron_vendor", null, .5));
        assertNull(materials.of("missing", null, 0));
        materials.source(content);
        assertTrue(materials.current(old));
        materials.clear(); // resource reload: invalidates live particles as well as uploads
        assertFalse(materials.current(old));
        assertEquals(registrations.size(), releases.size());
        materials.source(content); // unchanged cache after reload must reupload, not stale GPU names
        var reloaded = materials.of("ammo_crate", null, 0);
        assertNotEquals(old.texture, reloaded.texture);
        materials.source(content()); // replacement cache invalidates again
        assertFalse(materials.current(reloaded));
        materials.source(null);
        assertNull(materials.of("ammo_crate", null, 0));
    }

    @Test void hooksUseVanillaTerrainPhysicsLightingAndOverrideInvisibleMiningAndDestroy() throws Exception {
        var root = Path.of(System.getProperty("racmc.projectDir"));
        String source = Files.readString(root.resolve(
                "src/main/java/com/github/giannigr/racmc/client/render/Rac1BlockDebris.java"));
        assertTrue(source.contains("extends TerrainParticle"));
        assertTrue(source.contains("addHitEffects") && source.contains("addDestroyEffects"));
        assertTrue(source.contains("RacMcBlocks.crates()") && source.contains("RacMcBlocks.VENDOR"));
        assertTrue(source.contains("DefaultVertexFormat.PARTICLE"));
        assertTrue(source.contains("RenderSystem.setShaderTexture(0, binding.texture)"));
        assertTrue(source.contains("binding.patch.u0()") && source.contains("binding.patch.v1()"));
        assertFalse(source.contains("FULL_LIGHT"));
        assertFalse(source.contains("Iso") || source.contains("Files.") || source.contains("ImageIO"));
        String registration = Files.readString(root.resolve(
                "src/main/java/com/github/giannigr/racmc/client/RacMcClientRegistration.java"));
        assertTrue(registration.contains("Rac1BlockDebris.clear()"));
    }

    private static Rac1DebrisMesh mesh() {
        return new Rac1DebrisMesh(
                new float[]{0,0,0, 1,0,0, 0,1,0, 0,0,1, 0,1,1, 1,0,1},
                new float[]{1.1f,.2f, 1.7f,.2f, 1.4f,.5f, 0,0, 0,1, 1,0},
                new int[]{0,1,2, 3,4,5}, new int[]{0,1},
                new Rac1Texture[]{opaque(180,50,25), opaque(25,100,200)});
    }

    private static Rac1Texture opaque(int r, int g, int b) {
        return new Rac1Texture(1, 1, new byte[]{(byte)255,(byte) r,(byte) g,(byte) b});
    }

    private static Rac1CacheContent content() {
        var vendor = SyntheticVendor.content();
        var crates = new LinkedHashMap<String, Rac1CrateAsset>();
        var mesh = new Rac1Mesh(new float[]{0,0,0,1,0,0,0,1,0}, new float[9],
                new float[]{0,0,1,0,0,1}, new int[]{0,1,2}, new int[]{0});
        for (var definition : Rac1Crates.definitions()) crates.put(definition.key(),
                new Rac1CrateAsset(definition.key(), definition.classNumber(), mesh,
                        new Rac1Texture[]{opaque(25,100,200)}));
        return new Rac1CacheContent("debris", vendor.staticAsset(), vendor.animatedAsset(),
                vendor.blaster(), vendor.handJoint(), crates, vendor.vendor());
    }
}
