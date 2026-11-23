package com.github.giannigr.racmc.asset.rescue;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.MobySequenceReader;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.asset.Rac1CoordinateTransform;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Single-purpose Veldin S01 adapter. No scene playback, world track, audio or camera. */
final class Rac1RescueScenePose {
    static final int CLASS = 1365;
    static final int JOINTS = 34;
    static final int SECTOR_BYTES = 2048;
    private static final int TABLE_ENTRIES = 71;
    private static final int SCENE_TABLE = 0x184;
    private static final int SCENE_RECORD = 0x250;
    private static final int FRAME_HEADER = MobySequenceReader.FRAME_HEADER_SIZE;
    private static final int SEQUENCE_HEADER = MobySequenceReader.SEQUENCE_HEADER_SIZE;
    private static final int MAX_DECODED_BYTES = 96 * 1024; // R16 maximum across every S01 chunk: 79,712
    static final int MAX_WAD_BYTES = 48 * 1024; // R16 maximum across every S01 chunk: 42,992
    private static final int MAX_SECTORS = MAX_WAD_BYTES / SECTOR_BYTES;

    record Span(int first, int count) {}

    private Rac1RescueScenePose() {}

    static Span firstChunk(byte[] levelHeader, Rac1DiscRevision revision, long imageBytes)
            throws AssetFormatException {
        boolean pal = revision == Rac1DiscRevision.PAL_V2_00;
        require(revision == Rac1DiscRevision.NTSC_U_V1_00 || pal, "unsupported revision");
        // Only these retail S01 spans were verified by R16. Read only the first chunk.
        int expectedFirst = pal ? 1618986 : 1618767;
        int expectedEnd = pal ? 1619079 : 1618875;
        int offset = SCENE_TABLE + SCENE_RECORD + (pal ? 0x134 : 0x18);
        var header = little(levelHeader);
        require(range(offset, TABLE_ENTRIES * Integer.BYTES, header.limit()), "S01 table outside header");
        int previous = 0;
        int second = 0;
        // Six chunks and one sentinel in each supported release.
        for (int i = 0; i < 7; i++) {
            int next = header.getInt(offset + i * Integer.BYTES);
            require(next > previous && (long) next * SECTOR_BYTES < imageBytes, "invalid S01 sector");
            if (i == 0) require(next == expectedFirst, "wrong S01 start");
            if (i == 1) second = next;
            previous = next;
        }
        require(previous == expectedEnd && header.getInt(offset + 7 * Integer.BYTES) == 0,
                "wrong S01 sentinel");
        require(second - expectedFirst <= MAX_SECTORS, "first S01 chunk too large");
        return new Span(expectedFirst, second - expectedFirst);
    }

    static Rac1Animation frameZero(byte[] decoded, Rac1DiscRevision revision, float classScale)
            throws AssetFormatException {
        boolean pal = revision == Rac1DiscRevision.PAL_V2_00;
        require(revision == Rac1DiscRevision.NTSC_U_V1_00 || pal, "unsupported revision");
        require(Float.isFinite(classScale) && classScale > 0, "invalid class scale");
        var chunk = little(decoded);
        require(chunk.limit() >= 0x1c && chunk.limit() <= MAX_DECODED_BYTES
                && u16(chunk, 0) == (pal ? 448 : 538) && u16(chunk, 0x0c) == 2, "wrong S01 chunk");
        int otherActor = chunk.getInt(0x14);
        int actor = chunk.getInt(0x18);
        require(otherActor >= 0x1c && actor >= 0x1c && otherActor != actor
                && range(otherActor, 0x10, chunk.limit()) && chunk.getInt(otherActor) == 0
                && range(actor, 0x10 + SEQUENCE_HEADER, chunk.limit()) && chunk.getInt(actor) == CLASS,
                "wrong S01 actors");
        int sequence = actor + 0x10;
        int track = chunk.getInt(actor + 0x0c);
        int frameCount = Byte.toUnsignedInt(chunk.get(sequence + 0x10));
        require(frameCount > 0 && frameCount <= (pal ? 42 : 50)
                && Byte.toUnsignedInt(chunk.get(sequence + 0x12)) == 255
                && range(sequence + SEQUENCE_HEADER, frameCount * 4, track)
                && range(track, frameCount * 16, chunk.limit()), "wrong 1365 sequence bounds");
        // The position is only checked for format. It never enters the pod-local pose.
        require(Float.isFinite(chunk.getFloat(track)) && Float.isFinite(chunk.getFloat(track + 4))
                && Float.isFinite(chunk.getFloat(track + 8)) && chunk.getFloat(track + 12) == 0,
                "wrong 1365 position track");
        int offset = chunk.getInt(sequence + SEQUENCE_HEADER);
        require(offset >= SEQUENCE_HEADER + frameCount * 4 && range(sequence, offset, track)
                && range(sequence + offset, FRAME_HEADER, track), "frame zero outside sequence");
        int frame = sequence + offset;
        int bytes = u16(chunk, frame + 6) * 16;
        require(u16(chunk, frame + 8) == JOINTS * 8 && u16(chunk, frame + 10) <= JOINTS
                && u16(chunk, frame + 14) <= JOINTS && range(frame + FRAME_HEADER, bytes, track),
                "invalid frame zero payload");

        // Isolate the raw first key. Do not map near-0xff00 scene header clocks to a gameplay clip.
        byte[] isolated = new byte[SEQUENCE_HEADER + 4 + FRAME_HEADER + bytes];
        isolated[0x10] = 1;
        little(isolated).putInt(SEQUENCE_HEADER, SEQUENCE_HEADER + 4);
        System.arraycopy(decoded, frame, isolated, SEQUENCE_HEADER + 4, FRAME_HEADER + bytes);
        var keys = MobySequenceReader.read(isolated, 0, JOINTS);
        float[] rotations = keys.rotations();
        for (int i = 0; i < rotations.length; i += 4) {
            float x = rotations[i], y = rotations[i + 1], z = rotations[i + 2], w = rotations[i + 3];
            float lengthSquared = x * x + y * y + z * z + w * w;
            require(lengthSquared > 0.95f && lengthSquared < 1.05f, "invalid scene joint quaternion");
            System.arraycopy(Rac1AssetExtractor.convertQuaternion(x, y, z, w), 0, rotations, i, 4);
        }
        if (Arrays.stream(keys.translationJoints()).distinct().count() != keys.translationJoints().length
                || Arrays.stream(keys.scaleJoints()).distinct().count() != keys.scaleJoints().length) {
            throw new AssetFormatException("S01 pose has duplicate joint channels.");
        }
        float[] translations = keys.translationValues();
        var axes = new Rac1CoordinateTransform(classScale * Rac1AssetExtractor.UNIT_SCALE);
        for (int i = 0; i < translations.length; i += 3) {
            System.arraycopy(axes.convert(translations[i], translations[i + 1], translations[i + 2]),
                    0, translations, i, 3);
        }
        return new Rac1Animation("l00_s01_actor1_frame0", keys.times(), false, rotations,
                keys.translationKeys(), keys.translationJoints(), translations,
                keys.scaleKeys(), keys.scaleJoints(), keys.scaleValues(), keys.scaleInherited());
    }

    private static ByteBuffer little(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static boolean range(int offset, int count, int limit) {
        return offset >= 0 && count >= 0 && (long) offset + count <= limit;
    }

    private static int u16(ByteBuffer buffer, int offset) {
        return Short.toUnsignedInt(buffer.getShort(offset));
    }

    private static void require(boolean condition, String problem) throws AssetFormatException {
        if (!condition) throw new AssetFormatException("Rescue scene: " + problem);
    }
}
