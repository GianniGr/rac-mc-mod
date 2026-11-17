package com.github.giannigr.racmc.asset.hud;

import com.github.giannigr.racmc.asset.Rac1Texture;

/** A decoded atlas and original four-byte glyph records: u, v, signed top, signed advance. */
public record Rac1HudFont(Rac1Texture atlas, byte[] metrics) {
    public Rac1HudFont {
        if (metrics.length < 4 * 128 || metrics.length > 4 * 256 || metrics.length % 4 != 0)
            throw new IllegalArgumentException("Invalid font metrics length");
        metrics = metrics.clone();
        for (int c = 33; c < 127; c++) {
            int u = metrics[c * 4] & 255, v = metrics[c * 4 + 1] & 255;
            if (metrics[c * 4 + 3] != 0 && (u + 16 > atlas.width() || v + 16 > atlas.height()))
                throw new IllegalArgumentException("Font glyph outside atlas");
        }
    }
    @Override public byte[] metrics() { return metrics.clone(); }
    public int u(char c) { return metrics[c * 4] & 255; }
    public int v(char c) { return metrics[c * 4 + 1] & 255; }
    public int top(char c) { return metrics[c * 4 + 2]; }
    public int advance(char c) { return metrics[c * 4 + 3]; }
}
