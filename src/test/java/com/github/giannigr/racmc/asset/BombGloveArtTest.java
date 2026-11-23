package com.github.giannigr.racmc.asset;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BombGloveArtTest {
    @Test
    void acceptsOneFiniteOpaqueMaterialOnly() {
        float[] p = new float[9], n = new float[9], uv = new float[6];
        byte[] opaque = new byte[4 * 64 * 64];
        Arrays.fill(opaque, (byte) 255);
        var texture = new Rac1Texture(64, 64, opaque);
        assertDoesNotThrow(() -> BombGloveArt.validate(p, n, uv, new int[]{0, 1, 2},
                new int[]{0}, List.of(texture), 3, 1));
        assertThrows(AssetFormatException.class, () -> BombGloveArt.validate(p, n, uv,
                new int[]{0, 1, 3}, new int[]{0}, List.of(texture), 3, 1));
        assertThrows(AssetFormatException.class, () -> BombGloveArt.validate(p, n, uv,
                new int[]{0, 1, 2}, new int[]{1}, List.of(texture), 3, 1));
        opaque[0] = 0;
        assertThrows(AssetFormatException.class, () -> BombGloveArt.validate(p, n, uv,
                new int[]{0, 1, 2}, new int[]{0}, List.of(texture), 3, 1));
        opaque[0] = (byte) 255;
        n[0] = Float.NaN;
        assertThrows(AssetFormatException.class, () -> BombGloveArt.validate(p, n, uv,
                new int[]{0, 1, 2}, new int[]{0}, List.of(texture), 3, 1));
    }
}
