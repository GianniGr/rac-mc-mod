package com.github.giannigr.racmc.client.render;

import java.util.*;
import java.util.function.*;

/** Testable cache-identity/reload lifecycle, independent of the graphics driver. */
public final class Rac1HudTextureRegistry<T> {
    private Object source;
    private final Map<String, T> textures = new LinkedHashMap<>();
    private final Consumer<T> release;
    public Rac1HudTextureRegistry(Consumer<T> release) { this.release = release; }
    public T get(Object content, String key, Supplier<T> register) {
        if (source != content) { clear(); source = content; }
        return textures.computeIfAbsent(key, ignored -> register.get());
    }
    public void clear() {
        textures.values().forEach(release);
        textures.clear();
        source = null;
    }
}
