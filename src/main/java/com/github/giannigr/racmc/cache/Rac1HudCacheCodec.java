package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.hud.*;
import java.io.*;
import java.util.*;

/** Versioned reusable 2D asset section. All lengths are checked before allocation. */
public final class Rac1HudCacheCodec {
    private Rac1HudCacheCodec() {}
    public static byte[] write(Rac1HudAssets assets) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeInt(1);
        out.writeInt(assets.sprites().size());
        for (var entry : assets.sprites().entrySet()) {
            out.writeUTF(entry.getKey()); out.writeInt(entry.getValue().size());
            for (var texture : entry.getValue()) texture(out, texture);
        }
        out.writeInt(assets.fonts().size());
        for (var entry : assets.fonts().entrySet()) {
            out.writeUTF(entry.getKey()); texture(out, entry.getValue().atlas());
            byte[] metrics = entry.getValue().metrics();
            out.writeInt(metrics.length); out.write(metrics);
        }
        out.writeInt(assets.properties().size());
        for (var entry : assets.properties().entrySet()) {
            out.writeUTF(entry.getKey()); out.writeInt(entry.getValue());
        }
        return bytes.toByteArray();
    }

    public static Rac1HudAssets read(byte[] bytes) throws IOException {
        if (bytes.length > 32 * 1024 * 1024) throw new IOException("Oversized HUD cache section");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 1) throw new IOException("Unsupported 2D HUD section version");
        Map<String, List<Rac1Texture>> sprites = new LinkedHashMap<>();
        for (int i = bounded(in.readInt(), 64); i > 0; i--) {
            String key = key(in);
            int count = bounded(in.readInt(), 512);
            List<Rac1Texture> frames = new ArrayList<>();
            for (int frame = 0; frame < count; frame++) frames.add(texture(in));
            if (sprites.put(key, frames) != null) throw new IOException("Duplicate HUD sprite key");
        }
        Map<String, Rac1HudFont> fonts = new LinkedHashMap<>();
        for (int i = bounded(in.readInt(), 8); i > 0; i--) {
            String key = key(in);
            var atlas = texture(in);
            int count = bounded(in.readInt(), 1024);
            byte[] metrics = exact(in, count);
            try {
                if (fonts.put(key, new Rac1HudFont(atlas, metrics)) != null)
                    throw new IOException("Duplicate HUD font key");
            } catch (IllegalArgumentException failure) {
                throw new IOException("Invalid HUD font metrics", failure);
            }
        }
        Map<String, Integer> properties = new LinkedHashMap<>();
        for (int i = bounded(in.readInt(), 128); i > 0; i--) {
            String key = key(in);
            if (properties.put(key, in.readInt()) != null) throw new IOException("Duplicate HUD property");
        }
        if (in.available() != 0) throw new IOException("Trailing HUD cache bytes");
        return new Rac1HudAssets(sprites, fonts, properties);
    }

    private static String key(DataInputStream in) throws IOException {
        String key = in.readUTF();
        if (key.isBlank() || key.length() > 128) throw new IOException("Invalid HUD asset key");
        return key;
    }
    private static void texture(DataOutputStream out, Rac1Texture texture) throws IOException {
        out.writeInt(texture.width()); out.writeInt(texture.height()); out.write(texture.pixels());
    }
    private static Rac1Texture texture(DataInputStream in) throws IOException {
        int width = bounded(in.readInt(), 1024), height = bounded(in.readInt(), 1024);
        if (width == 0 || height == 0) throw new IOException("Empty HUD texture");
        return new Rac1Texture(width, height, exact(in, width * height * 4));
    }
    private static byte[] exact(DataInputStream in, int size) throws IOException {
        if (size > in.available()) throw new EOFException("Truncated HUD cache");
        byte[] result = new byte[size]; in.readFully(result); return result;
    }
    private static int bounded(int value, int maximum) throws IOException {
        if (value < 0 || value > maximum) throw new IOException("Invalid HUD cache count/size");
        return value;
    }
}
