package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Bounded 989SND outer-v3 / SBlk-v1 reader. Cue programs other than one tone are refused. */
public final class Rac1SoundBank {
    private final byte[] bytes;
    private final ByteBuffer b;
    private final int metadata, metadataEnd, samples, sampleSize, cues, grains, cueTable, grainTable;
    public Rac1SoundBank(byte[] bytes) throws IOException {
        if (bytes.length < 64 || bytes.length > 32 * 1024 * 1024) throw new IOException("Invalid bank length");
        this.bytes = bytes.clone();
        b = SoundBytes.view(this.bytes);
        if (b.getInt(0) != 3 || b.getInt(4) != 2) throw new IOException("Unsupported bank container");
        metadata = b.getInt(8);
        int metadataSize = b.getInt(12);
        samples = b.getInt(16); sampleSize = b.getInt(20);
        SoundBytes.range(metadata, metadataSize, bytes.length);
        SoundBytes.range(samples, sampleSize, bytes.length);
        if (metadata < 24 || metadataSize < 40 || samples < metadata + (long) metadataSize) {
            throw new IOException("Overlapping bank sections");
        }
        metadataEnd = metadata + metadataSize;
        if (b.getInt(metadata) != 0x6b6c4253 || b.getInt(metadata + 4) != 1) {
            throw new IOException("Unsupported SBlk");
        }
        cues = Short.toUnsignedInt(b.getShort(metadata + 0x16));
        grains = Short.toUnsignedInt(b.getShort(metadata + 0x18));
        cueTable = relative(b.getInt(metadata + 0x1c), cues * 12L);
        grainTable = relative(b.getInt(metadata + 0x20), grains * 40L);
    }
    private int relative(int offset, long length) throws IOException {
        if (offset < 0) throw new IOException("Negative bank offset");
        long absolute = metadata + (long) offset;
        if (absolute > Integer.MAX_VALUE) throw new IOException("Bank offset overflow");
        SoundBytes.range((int) absolute, length, metadataEnd);
        return (int) absolute;
    }
    public record Tone(int cue, int grain, int cueVolume, int toneVolume, int centerNote,
                       int fine, int toneFlags, byte[] rawCue, byte[] rawGrain,
                       String adpcmHash, PsAdpcm.Decoded decoded) {
        public Tone { rawCue = rawCue.clone(); rawGrain = rawGrain.clone(); }
        @Override public byte[] rawCue() { return rawCue.clone(); }
        @Override public byte[] rawGrain() { return rawGrain.clone(); }
        /** Preserve the v1 positive-note path; negative notes use the 989snd SPU2 pitch lookup. */
        public float sampleRate() throws IOException {
            if (centerNote < -128 || centerNote > 127 || fine < 0 || fine > 127) {
                throw new IOException("Unverified note encoding");
            }
            if (centerNote < 0) {
                // Independent fixed note=60/fine=0 specialization of the public libsd facts:
                // ReRAC ae1e343, rc-formats/src/vag.rs; see docs/clank.md for provenance.
                // The sign selects 48 kHz, NOT an unsigned MIDI note or a negative frequency.
                int difference = 60 + centerNote;
                int octave = Math.floorDiv(difference, 12);
                int semitone = Math.floorMod(difference, 12);
                long coarse = (long) (32768 * StrictMath.pow(2, semitone / 12.0));
                long fractional = (long) (32768 * StrictMath.pow(2, fine / 1536.0));
                long pitch = coarse * fractional / 65536;
                int shift = 2 - octave;
                if (shift > 0) pitch = (pitch + (1L << (shift - 1))) >> shift;
                return 48000f * pitch / 4096;
            }
            return (float) (44100.0 * Math.pow(2.0, (60 - centerNote - fine / 128.0) / 12.0));
        }
    }
    public String digest() { return SoundBytes.hash(bytes); }
    public Tone tone(int cue) throws IOException {
        if (cue < 0 || cue >= cues) throw new IOException("Missing bank cue");
        int c = cueTable + cue * 12, grainOffset = b.getInt(c + 8);
        if ((bytes[c + 4] & 255) != 1 || grainOffset < 0 || grainOffset % 40 != 0
                || grainOffset / 40 >= grains) throw new IOException("Unsupported cue program");
        int g = grainTable + grainOffset;
        if (b.getInt(g) != 1) throw new IOException("Unsupported cue opcode");
        int start = b.getInt(g + 8 + 0x10);
        SoundBytes.range(start, 16, sampleSize);
        // Bound by the nearest following tone start, not just the whole sample section.
        int end = sampleSize;
        for (int n = 0; n < grains; n++) {
            int p = grainTable + n * 40;
            if (b.getInt(p) == 1) {
                int other = b.getInt(p + 24);
                if (other > start && other < end) end = other;
            }
        }
        var decoded = PsAdpcm.decode(Arrays.copyOfRange(bytes, samples, samples + sampleSize), start, end);
        String hash = SoundBytes.hash(Arrays.copyOfRange(bytes, samples + start,
                samples + start + decoded.frames() * 16));
        return new Tone(cue, grainOffset / 40, bytes[c] & 255, bytes[g + 9] & 255,
                bytes[g + 10], bytes[g + 11] & 255, Short.toUnsignedInt(b.getShort(g + 22)),
                Arrays.copyOfRange(bytes, c, c + 12), Arrays.copyOfRange(bytes, g, g + 40), hash, decoded);
    }
}
