package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.util.Arrays;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Original 9x9 LED cells use packed unsigned 12.4 UV coordinates, not ordinary font metrics. */
public final class Rac1ShopLedFont {
    public static final int FIRST = 0x20, LAST = 0x5a, TABLE_ENTRIES = 64;
    private final int[] cells, advances;
    public Rac1ShopLedFont(int[] cells, int[] advances) {
        if (cells.length != TABLE_ENTRIES || advances.length != TABLE_ENTRIES)
            throw new IllegalArgumentException("Invalid LED table counts");
        this.cells = cells.clone(); this.advances = advances.clone();
        for (int i = 0; i <= LAST - FIRST; i++) {
            int cell = cells[i];
            if (advances[i] < 0 || advances[i] > 32)
                throw new IllegalArgumentException("Invalid LED advance");
            if (cell != -1) {
                int u = cell & 65535, v = cell >>> 16;
                if ((u & 15) != 0 || (v & 15) != 0 || u / 16 + 9 > 64 || v / 16 + 9 > 64)
                    throw new IllegalArgumentException("LED cell outside original 64x64 atlas");
            }
        }
    }
    public int[] cells() { return cells.clone(); }
    public int[] advances() { return advances.clone(); }
    public int cell(int sourceByte) { return cells[index(sourceByte)]; }
    public int advance(int sourceByte) { return advances[index(sourceByte)]; }
    private int index(int c) {
        if (c < FIRST || c > LAST) throw new IllegalArgumentException("Unsupported LED byte " + c);
        return c - FIRST;
    }
    public static Rac1ShopLedFont read(byte[] cells, byte[] advances) throws AssetFormatException {
        if (cells.length != 256 || advances.length != 256)
            throw new AssetFormatException("Invalid source LED table lengths");
        int[] c = new int[64], a = new int[64];
        for (int i = 0; i < 64; i++) { c[i] = word(cells, i * 4); a[i] = word(advances, i * 4); }
        try { return new Rac1ShopLedFont(c, a); }
        catch (IllegalArgumentException failure) { throw new AssetFormatException(failure.getMessage(), failure); }
    }
    @Override public boolean equals(Object other) {
        return other instanceof Rac1ShopLedFont f && Arrays.equals(cells, f.cells) && Arrays.equals(advances, f.advances);
    }
    @Override public int hashCode() { return 31 * Arrays.hashCode(cells) + Arrays.hashCode(advances); }
}
