package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.util.Arrays;

/** Independent implementation of the public PlayStation 16-byte / 28-sample ADPCM format. */
public final class PsAdpcm {
    public static final int MAX_FRAMES = 262144;
    private static final int[][] FILTER = {{0, 0}, {60, 0}, {115, -52}, {98, -55}, {122, -60}};
    private PsAdpcm() {}

    public record Decoded(byte[] pcm, int frames, int loopStart, int loopEnd, boolean repeat) {
        public Decoded { pcm = pcm.clone(); }
        @Override public byte[] pcm() { return pcm.clone(); }
        public int samples() { return frames * 28; }
    }

    /** Decode up to the first end flag, never past the supplied waveform boundary. */
    public static Decoded decode(byte[] data, int offset, int limit) throws IOException {
        if (offset < 0 || limit > data.length || offset >= limit || (offset & 15) != 0) {
            throw new IOException("Invalid ADPCM range");
        }
        int capacity = Math.min((limit - offset) / 16, MAX_FRAMES);
        byte[] pcm = new byte[capacity * 56];
        int h1 = 0, h2 = 0, out = 0, loop = -1;
        for (int frame = 0; frame < capacity; frame++) {
            int p = offset + frame * 16, header = data[p] & 255, flags = data[p + 1] & 255;
            int filter = header >>> 4, shift = header & 15;
            if (filter >= FILTER.length || shift > 12 || (flags & ~7) != 0) {
                throw new IOException("Unsupported ADPCM frame");
            }
            if ((flags & 4) != 0) loop = frame * 28;
            for (int n = 0; n < 28; n++) {
                int nibble = (data[p + 2 + n / 2] >>> ((n & 1) * 4)) & 15;
                int signed = nibble < 8 ? nibble : nibble - 16;
                int value = ((signed << 12) >> shift)
                        + ((h1 * FILTER[filter][0] + h2 * FILTER[filter][1] + 32) >> 6);
                value = Math.max(-32768, Math.min(32767, value));
                pcm[out++] = (byte) value;
                pcm[out++] = (byte) (value >> 8);
                h2 = h1;
                h1 = value;
            }
            if ((flags & 1) != 0) {
                return new Decoded(Arrays.copyOf(pcm, out), frame + 1, loop,
                        (frame + 1) * 28, (flags & 2) != 0);
            }
        }
        throw new IOException("ADPCM waveform has no bounded end marker");
    }
}
