package com.github.giannigr.racmc.client.effect;

import com.github.giannigr.racmc.asset.effect.*;
import com.github.giannigr.racmc.client.render.Rac1ClientTextures;
import com.github.giannigr.racmc.effect.EffectMaterial;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** One render-thread resource owner per generation, no static texture/RenderType caches.
 * CPU decoding is performed by the importer/caller before construction. */
public final class EffectGpuCatalog implements AutoCloseable {
    public record CpuTexture(EffectAssetRef ref, com.github.giannigr.racmc.asset.Rac1Texture texture) {}
    private record MaterialKey(EffectAssetRef ref, int slot, EffectMaterial material) {}
    private final Map<EffectAssetRef, ResourceLocation> textures = new HashMap<>();
    private final Map<EffectAssetRef, EffectMeshAsset> meshes = new HashMap<>();
    private final Map<MaterialKey, RenderType> types = new HashMap<>();
    private final List<ResourceLocation> owned = new ArrayList<>();
    private boolean closed;

    public static List<CpuTexture> decode(EffectBanks banks) throws com.github.giannigr.racmc.asset.AssetFormatException {
        List<CpuTexture> result = new ArrayList<>();
        for (EffectTextureBank bank : List.of(banks.particles(), banks.fx()))
            for (int id = 0; id < bank.entries().size(); id++)
                result.add(new CpuTexture(new EffectAssetRef(bank.source().discSha1(),
                        bank.source().sourceLevel(), bank.source().bank(), id), bank.decode(id)));
        return List.copyOf(result);
    }

    public EffectGpuCatalog(long generation, List<CpuTexture> decoded, List<EffectMeshAsset> meshes) {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        if (decoded.size() > 512 || meshes.size() > 64) throw new IllegalArgumentException("GPU catalog too large");
        long pixels = 0;
        for (CpuTexture entry : decoded) pixels += texturePixels(entry.texture());
        for (EffectMeshAsset mesh : meshes)
            for (var texture : mesh.textures()) pixels += texturePixels(texture);
        if (pixels > 32 * 1024 * 1024) throw new IllegalArgumentException("Effect GPU texture budget exceeded");
        try {
            for (CpuTexture entry : decoded) {
                if (textures.containsKey(entry.ref())) throw new IllegalArgumentException("Duplicate effect texture");
                ResourceLocation location = Rac1ClientTextures.registerClamped(
                        prefix(generation, entry.ref()) + entry.ref().bank().name().toLowerCase(Locale.ROOT)
                                + "_" + entry.ref().sourceId(), entry.texture());
                textures.put(entry.ref(), location);
                owned.add(location);
            }
            for (EffectMeshAsset mesh : meshes) {
                if (this.meshes.putIfAbsent(mesh.source(), mesh) != null)
                    throw new IllegalArgumentException("Duplicate effect mesh");
                for (int slot = 0; slot < mesh.textures().size(); slot++) {
                    ResourceLocation location = Rac1ClientTextures.register(prefix(generation, mesh.source()) + "moby_"
                            + mesh.source().sourceId() + "_" + slot, mesh.textures().get(slot));
                    owned.add(location);
                    meshTextures.computeIfAbsent(mesh.source(), ignored -> new ArrayList<>()).add(location);
                }
            }
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }
    private final Map<EffectAssetRef, List<ResourceLocation>> meshTextures = new HashMap<>();
    private static String prefix(long generation, EffectAssetRef ref) {
        return "effect/" + generation + "/" + ref.discSha1() + "/level_" + ref.sourceLevel() + "/";
    }
    private static long texturePixels(com.github.giannigr.racmc.asset.Rac1Texture texture) {
        if (texture.width() > 1024 || texture.height() > 1024)
            throw new IllegalArgumentException("Effect GPU texture dimensions exceeded");
        return (long) texture.width() * texture.height();
    }
    public EffectMeshAsset mesh(EffectAssetRef ref) { return closed ? null : meshes.get(ref); }
    public List<ResourceLocation> meshTextures(EffectAssetRef ref) {
        return meshTextures.getOrDefault(ref, List.of());
    }
    public RenderType type(EffectAssetRef ref, EffectMaterial material) {
        if (closed) return null;
        ResourceLocation location = textures.get(ref);
        if (location == null) return null; // Missing original assets never become placeholder smoke.
        if (material.address() != EffectMaterial.Address.CLAMP)
            throw new IllegalArgumentException("Bank sprite requires clamp in first slice");
        return types.computeIfAbsent(new MaterialKey(ref, -1, material),
                ignored -> EffectRenderTypes.create(location, material));
    }
    public RenderType meshType(EffectAssetRef ref, int slot, EffectMaterial material) {
        List<ResourceLocation> locations = meshTextures(ref);
        if (closed || slot < 0 || slot >= locations.size()) return null;
        if (material.address() != EffectMaterial.Address.REPEAT)
            throw new IllegalArgumentException("Static moby effect requires repeat sampling");
        // Mesh material state is owned by this generation too.
        return types.computeIfAbsent(new MaterialKey(ref, slot, material),
                ignored -> EffectRenderTypes.create(locations.get(slot), material));
    }
    @Override public void close() {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        for (ResourceLocation location : owned) Rac1ClientTextures.release(location);
        owned.clear();
        textures.clear();
        meshes.clear();
        meshTextures.clear();
        types.clear();
    }
}
