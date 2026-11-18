package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.util.Arrays;

/** Class-table-order remapping, independent of global/class IDs. Runtime bank handles are omitted. */
public final class Rac1SoundMap {
    private Rac1SoundMap() {}
    public record Definition(int cue, byte[] metadata) {
        public Definition { metadata = metadata.clone(); }
        @Override public byte[] metadata() { return metadata.clone(); }
    }
    public static Definition resolve(byte[] index, byte[] blob, int classId, int slot) throws IOException {
        SoundBytes.range(0, 0x74, index.length);
        SoundBytes.range(0, 0x2c, blob.length);
        var b = SoundBytes.view(index);
        int count = b.getInt(0x18), table = b.getInt(0x1c), map = b.getInt(0x70);
        if (count < 0 || count > 16384 || map <= 0) throw new IOException("Invalid sound map");
        SoundBytes.range(table, count * 32L, index.length);
        SoundBytes.range(map, 8L + count * 4L, index.length);
        if (slot < 0 || slot >= (blob[13] & 255)) throw new IOException("Missing class sound slot");
        var cb = SoundBytes.view(blob);
        int definitions = cb.getInt(0x28);
        if (definitions <= 0) throw new IOException("Missing sound definitions");
        SoundBytes.range(definitions, (blob[13] & 255) * 32L, blob.length);
        int d = definitions + slot * 32, local = cb.getShort(d + 26);
        for (int n = 0; n < count; n++) {
            if (b.getInt(table + n * 32 + 4) != classId) continue;
            int span = map + 8 + n * 4;
            int relative = b.getShort(span), entries = b.getShort(span + 2);
            if (entries < 0 || local < 0 || local >= entries) throw new IOException("Unresolved class sound remap");
            int start = map + relative;
            SoundBytes.range(start, entries * 4L, index.length);
            byte[] metadata = Arrays.copyOfRange(blob, d, d + 32);
            Arrays.fill(metadata, 28, 32, (byte) 0);
            return new Definition(Short.toUnsignedInt(b.getShort(start + local * 4)), metadata);
        }
        throw new IOException("Class absent from sound map");
    }
}
