package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.util.Arrays;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.range;

/** Stored reference spans only. Neither a frame count nor FPS is present on disc. */
public final class ParticleFrameLists {
    private final byte[] bytes;
    private final int count, textures, blob, size;

    public ParticleFrameLists(byte[] bytes) throws AssetFormatException {
        range(bytes, 0, 16);
        count = word(bytes, 0);
        textures = word(bytes, 4);
        blob = word(bytes, 8);
        size = word(bytes, 12);
        if (count < 1 || count > 1024 || textures < 1 || textures > 256
                || blob < 16 + count * 4 || blob > 16 + count * 4 + 15 || size < 1 || size > 65536
                || bytes.length != blob + size)
            throw new AssetFormatException("Invalid particle definition layout");
        this.bytes = bytes.clone();
        for (int type = 0; type < count; type++) {
            int offset = word(bytes, 16 + type * 4);
            if (offset != 0 && (offset < blob || offset >= blob + size))
                throw new AssetFormatException("Particle definition offset outside blob");
        }
        for (int at = blob; at < bytes.length; at++)
            if ((bytes[at] & 255) >= textures)
                throw new AssetFormatException("Particle definition names unknown texture");
    }

    public int count() { return count; }
    public int textureCount() { return textures; }
    public byte[] bytes() { return bytes.clone(); }

    public byte[] storedSpan(int type) {
        if (type < 0 || type >= count) throw new IllegalArgumentException("Unknown particle type");
        int start = offset(type), end = blob + size;
        for (int other = 0; other < count; other++) {
            int next = offset(other);
            if (next > start && next < end) end = next;
        }
        return Arrays.copyOfRange(bytes, start, end);
    }

    private int offset(int type) {
        int at = 16 + type * 4;
        int raw = (bytes[at] & 255) | ((bytes[at + 1] & 255) << 8)
                | ((bytes[at + 2] & 255) << 16) | (bytes[at + 3] << 24);
        return raw == 0 ? blob : raw;
    }
}
