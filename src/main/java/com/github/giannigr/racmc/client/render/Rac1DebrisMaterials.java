package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.Rac1VendorPose;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;

/** One cache-generation owner for CPU meshes and uploaded debris textures. Testable without GL. */
public final class Rac1DebrisMaterials<T> {
    public interface Upload<T> {
        T register(String name, Rac1Texture texture);
        void release(T texture);
    }
    public static final class Binding<T> {
        public final String key;
        public final T texture;
        public final Rac1DebrisMesh.Patch patch;
        private final long generation;
        private Binding(String key, T texture, Rac1DebrisMesh.Patch patch, long generation) {
            this.key = key; this.texture = texture; this.patch = patch; this.generation = generation;
        }
    }
    private final Upload<T> upload;
    private final Map<String, Rac1DebrisMesh> meshes = new HashMap<>();
    private final Map<String, T> textures = new HashMap<>();
    private Rac1CacheContent source;
    private long generation;

    public Rac1DebrisMaterials(Upload<T> upload) { this.upload = upload; }

    public void source(Rac1CacheContent content) {
        if (content != source) { clear(); source = content; }
    }

    public Binding<T> of(String key, Direction side, double fraction) {
        if (source == null) return null;
        var mesh = meshes.computeIfAbsent(key, this::mesh);
        if (mesh == null) return null;
        var patch = mesh.sample(side, fraction);
        if (patch == null) return null;
        String materialKey = key + "_" + patch.material();
        T texture = textures.computeIfAbsent(materialKey,
                name -> upload.register("debris_" + name, mesh.texture(patch.material())));
        return new Binding<>(materialKey, texture, patch, generation);
    }

    public boolean current(Binding<T> binding) {
        return binding != null && binding.generation == generation && source != null;
    }

    public void clear() {
        generation++;
        textures.values().forEach(upload::release);
        textures.clear(); meshes.clear(); source = null;
    }

    private Rac1DebrisMesh mesh(String key) {
        if (key.equals("gadgetron_vendor")) {
            if (source.vendor() == null) return null;
            var body = source.vendor().body();
            return new Rac1DebrisMesh(Rac1VendorPose.closed(source.vendor()).positions(),
                    body.mesh().uvs(), body.mesh().indices(), body.mesh().materials(), body.textures());
        }
        var crate = source.crate(key);
        return crate == null ? null : new Rac1DebrisMesh(crate.mesh().positions(),
                crate.mesh().uvs(), crate.mesh().indices(), crate.mesh().materials(), crate.textures());
    }
}
