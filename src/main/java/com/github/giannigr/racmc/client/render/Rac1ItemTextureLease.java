package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Texture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Generation-scoped imported textures; injectable without a GPU for lifecycle tests. */
public final class Rac1ItemTextureLease<T> {
    private final BiFunction<Integer, Rac1Texture, T> register;
    private final Consumer<T> release;
    private Object source;
    private long epoch = -1;
    private List<T> locations = List.of();

    public Rac1ItemTextureLease(BiFunction<Integer, Rac1Texture, T> register,
                                Consumer<T> release) {
        this.register = register;
        this.release = release;
    }

    public List<T> get(Object source, long epoch, Rac1Texture[] textures) {
        if (source == null) {
            clear();
        } else if (this.source != source || this.epoch != epoch) {
            clear();
            List<T> created = new ArrayList<>();
            try {
                for (int i = 0; i < textures.length; i++) {
                    created.add(register.apply(i, textures[i]));
                }
            } catch (RuntimeException | LinkageError failure) {
                created.forEach(release);
                throw failure;
            }
            this.locations = List.copyOf(created);
            this.source = source;
            this.epoch = epoch;
        }
        return locations;
    }

    public void clear() {
        locations.forEach(release);
        locations = List.of();
        source = null;
        epoch = -1;
    }
}
