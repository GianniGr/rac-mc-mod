package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.disc.Rac1DiscTables;
import com.github.giannigr.racmc.disc.Rac1LevelData;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.disc.Rac1LevelHeader;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import java.io.IOException;
import java.util.Arrays;

/** The proven Novalis dry class-121 waveform only. Callers hold a fully validated image. */
final class Rac1DryBombWave {
    static final int WAVE_START = 0x407e0;
    private static final int SAMPLE_SECTION_SIZE = 0x19d3b0;
    private static final int CUE = 89;
    private static final int GRAIN = 326;
    private static final int GRAIN_COUNT = 601;
    private static final int MAX_WAVE_BYTES = 64 * 1024;
    private static final int VERIFIED_NEXT_START = 0x44b60;
    private static final int VERIFIED_FRAMES = 1079;
    private static final String VERIFIED_ADPCM = "d4d552f62c0ac61ed79123dfe3964ddc3cc4f6bfb2b51080045f9988d0d0ddeb";
    private static final String VERIFIED_SPAN = "c323137b11df10b9a8e459906c95c0001d07b40f7dfc6c9db8219e17b198ced4";

    private Rac1DryBombWave() {}

    record Wave(PsAdpcm.Decoded decoded, String adpcmHash, String spanHash, int nextStart) {}
    record Bank(long metadataBase, long sampleBase, int cueTable, int grainTable,
                Rac1LevelData container, long levelSize) {}

    static Bank locate(Rac1DiscImage image) throws Exception {
        var tables = Rac1DiscTables.read(image);
        var level = Rac1LevelHeader.read(image, tables.levelSector(1));
        if (level.id() != 1) throw new IOException("Dry Bomb Glove cue is Novalis only");
        long levelBase = level.dataSector() * Rac1DiscTables.SECTOR_SIZE;
        long levelSize = (long) level.dataSectors() * Rac1DiscTables.SECTOR_SIZE;
        byte[] container = image.readBytes(levelBase, Rac1LevelData.HEADER_SIZE);
        int bank = word(container, 8), bankSize = word(container, 12);
        if (bank < Rac1LevelData.HEADER_SIZE || bankSize < 64 || bankSize > 32 * 1024 * 1024
                || bank + (long) bankSize > levelSize) throw new IOException("Invalid Novalis bank extent");
        long bankBase = levelBase + bank;
        var outer = image.readBytes(bankBase, 24);
        int metadata = word(outer, 8), metadataSize = word(outer, 12);
        int samples = word(outer, 16), sampleSize = word(outer, 20);
        if (word(outer, 0) != 3 || word(outer, 4) != 2 || metadata < 24 || metadataSize < 40
                || samples < metadata + (long) metadataSize || sampleSize != SAMPLE_SECTION_SIZE) {
            throw new IOException("Unsupported Novalis dry cue container");
        }
        SoundBytes.range(metadata, metadataSize, bankSize);
        SoundBytes.range(samples, sampleSize, bankSize);
        long metadataBase = bankBase + metadata;
        var sblk = image.readBytes(metadataBase, 40);
        var b = SoundBytes.view(sblk);
        int cueCount = Short.toUnsignedInt(b.getShort(0x16)), grainCount = Short.toUnsignedInt(b.getShort(0x18));
        int cueTable = b.getInt(0x1c), grainTable = b.getInt(0x20);
        if (b.getInt(0) != 0x6b6c4253 || b.getInt(4) != 1 || cueCount != 290 || grainCount != GRAIN_COUNT
                || cueTable < 40 || grainTable < cueTable + cueCount * 12L) {
            throw new IOException("Unsupported Novalis dry cue SBlk layout");
        }
        SoundBytes.range(cueTable, cueCount * 12L, metadataSize);
        SoundBytes.range(grainTable, grainCount * 40L, metadataSize);
        return new Bank(metadataBase, bankBase + samples, cueTable, grainTable,
                Rac1LevelData.of(levelBase, container), levelSize);
    }

    static Wave readWave(Rac1DiscImage image, Bank b) throws IOException {
        int next = nextWaveStart((grain, field) ->
                word(image.readBytes(b.metadataBase + b.grainTable + grain * 40L + field, 4), 0));
        if (next != VERIFIED_NEXT_START) throw new IOException("Changed Novalis dry bomb sample boundary");
        byte[] span = image.readBytes(b.sampleBase + WAVE_START, next - WAVE_START);
        return decodeSpan(span, next);
    }

    static Rac1AudioCatalog.Clip clip(Rac1DiscImage image) throws Exception {
        var bank = locate(image);
        var definition = classDefinition(image, bank);
        byte[] cue = image.readBytes(bank.metadataBase + bank.cueTable + CUE * 12L, 12);
        byte[] grain = image.readBytes(bank.metadataBase + bank.grainTable + GRAIN * 40L, 40);
        var g = SoundBytes.view(grain);
        if (definition.cue() != CUE || (cue[4] & 255) != 1 || word(cue, 8) != GRAIN * 40
                || g.getInt(0) != 1 || g.getInt(24) != WAVE_START || g.getShort(22) != 1
                || grain[10] != 72 || grain[11] != 0) {
            throw new IOException("Changed Novalis class-121 dry cue");
        }
        var wave = readWave(image, bank);
        if (wave.decoded.frames() != VERIFIED_FRAMES || !VERIFIED_ADPCM.equals(wave.adpcmHash)
                || !VERIFIED_SPAN.equals(wave.spanHash)) {
            throw new IOException("Unverified Novalis class-121 waveform");
        }
        // The SHA-256 identifies the verified local cue span, not a digest of unrelated bank data.
        return new Rac1AudioCatalog.Clip(wave.decoded.pcm(), 22050, definition.metadata(), cue, grain,
                wave.spanHash, wave.adpcmHash, CUE, -1, wave.decoded.samples(), false);
    }

    private static Rac1SoundMap.Definition classDefinition(Rac1DiscImage image, Bank bank) throws Exception {
        var data = bank.container;
        checkedCoreRange(data.coreIndexOffset(), data.coreIndexSize(), bank.levelSize, 4 * 1024 * 1024);
        checkedCoreRange(data.coreDataOffset(), data.coreDataSize(), bank.levelSize, 32 * 1024 * 1024);
        byte[] index = image.readBytes(data.base() + data.coreIndexOffset(), data.coreIndexSize());
        SoundBytes.range(0x8c, 4, index.length);
        int decompressed = word(index, 0x8c);
        if (decompressed <= 0 || decompressed > 64 * 1024 * 1024) throw new IOException("Invalid Novalis core size");
        var assets = Rac1LevelAssets.loadCore(image, 1, data, index);
        var row = assets.coreIndex().mobyClass(121);
        if (row == null || row.offset() <= 0 || assets.coreIndex().blockSize(row.offset()) != 5184) {
            throw new IOException("Missing verified Novalis class 121");
        }
        return Rac1SoundMap.resolve(index, assets.classBlob(row.offset()), 121, 0);
    }

    private static void checkedCoreRange(int offset, int length, long levelSize, int max) throws IOException {
        if (offset < Rac1LevelData.HEADER_SIZE || length <= 0 || length > max
                || offset + (long) length > levelSize) throw new IOException("Invalid Novalis core range");
    }

    @FunctionalInterface
    interface GrainWord {
        int read(int grain, int field) throws IOException;
    }

    /** Only reads the opcode and, for tone records, the start word. No cue or unrelated sample bytes. */
    static int nextWaveStart(GrainWord words) throws IOException {
        int next = Integer.MAX_VALUE;
        boolean selected = false;
        for (int n = 0; n < GRAIN_COUNT; n++) {
            if (words.read(n, 0) != 1) continue;
            int start = words.read(n, 0x18);
            if (n == GRAIN) selected = start == WAVE_START;
            if (start < 0 || start >= SAMPLE_SECTION_SIZE || (start & 15) != 0) {
                throw new IOException("Invalid Novalis tone wave start");
            }
            if (start > WAVE_START && start < next) next = start;
        }
        if (!selected || next == Integer.MAX_VALUE || next - WAVE_START > MAX_WAVE_BYTES) {
            throw new IOException("No safe upper bound for Novalis dry bomb waveform");
        }
        return next;
    }

    static Wave decodeSpan(byte[] span, int next) throws IOException {
        if (span.length < 16 || span.length > MAX_WAVE_BYTES || (span.length & 15) != 0
                || next - (long) WAVE_START != span.length) {
            throw new IOException("Invalid bounded dry bomb ADPCM span");
        }
        var decoded = PsAdpcm.decode(span, 0, span.length);
        int end = decoded.frames() * 16;
        // Reject repeat and loop-start flags anywhere in the playable waveform.
        for (int at = 1; at < end; at += 16) {
            int expected = at == end - 15 ? 1 : 0;
            if ((span[at] & 255) != expected) throw new IOException("Unexpected dry bomb ADPCM flags");
        }
        return new Wave(decoded, SoundBytes.hash(Arrays.copyOf(span, end)), SoundBytes.hash(span), next);
    }

    private static int word(byte[] bytes, int offset) { return SoundBytes.view(bytes).getInt(offset); }
}
