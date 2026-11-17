package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** One texture pair shared by item/world renderers, invalidated on content loss/change or reload. */
public final class Rac1VendorTextures<T> {
    public static final Rac1VendorTextures<ResourceLocation> SHARED =
            new Rac1VendorTextures<>(Rac1ClientTextures::register, Rac1ClientTextures::release);

    public record Pair<T>(List<T> body, List<T> hologram) {
    }

    private final BiFunction<String, Rac1Texture, T> register;
    private final Consumer<T> release;
    private Rac1CacheContent source;
    private Pair<T> pair;

    // Backend injection keeps ownership/lifecycle regression checks independent of OpenGL.
    Rac1VendorTextures(BiFunction<String, Rac1Texture, T> register, Consumer<T> release) {
        this.register = register;
        this.release = release;
    }

    public void synchronize(Rac1CacheContent content) {
        if (source != content) {
            clear();
            source = content;
        }
    }

    public Pair<T> of(Rac1CacheContent content) {
        synchronize(content);
        if (content == null || content.vendor() == null) {
            return null;
        }
        if (pair == null) {
            List<T> body = new ArrayList<>();
            List<T> hologram = new ArrayList<>();
            try {
                register("vendor_body_", content.vendor().body().textures(), body);
                register("vendor_hologram_", content.vendor().hologram().textures(), hologram);
                pair = new Pair<>(List.copyOf(body), List.copyOf(hologram));
            } catch (RuntimeException | Error exception) {
                body.forEach(release);
                hologram.forEach(release);
                throw exception;
            }
        }
        return pair;
    }

    private void register(String prefix, Rac1Texture[] textures, List<T> target) {
        for (int index = 0; index < textures.length; index++) {
            target.add(register.apply(prefix + index, textures[index]));
        }
    }

    public void clear() {
        if (pair != null) {
            pair.body().forEach(release);
            pair.hologram().forEach(release);
        }
        pair = null;
        source = null;
    }
}
