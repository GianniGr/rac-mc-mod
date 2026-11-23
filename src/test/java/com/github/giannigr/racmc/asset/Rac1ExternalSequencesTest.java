package com.github.giannigr.racmc.asset;

import java.nio.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ExternalSequencesTest {
    @Test void explicitStreamPreservesSlotsAndUsesSequenceRelativeKeys() throws Exception {
        byte[] sequence = SyntheticMobySequence.sequence(new float[]{.125f, .125f});
        byte[] stream = new byte[64 + sequence.length * 2];
        var b = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, 64); b.putInt(9 * 4, 64 + sequence.length);
        System.arraycopy(sequence, 0, stream, 64, sequence.length);
        System.arraycopy(sequence, 0, stream, 64 + sequence.length, sequence.length);
        var result = Rac1AssetExtractor.externalAnimations(stream, SyntheticMobySequence.JOINTS, 1);
        assertEquals(2, result.size());
        assertEquals("sequence_9", result.get(1).name());
        assertArrayEquals(result.get(0).times(), result.get(1).times());
        assertArrayEquals(result.get(0).rotations(), result.get(1).rotations());
    }
    @Test void rejectsMissingOversizedAliasedAndTruncatedStreams() {
        for (byte[] bytes : new byte[][]{new byte[0], new byte[63], new byte[64], new byte[8 * 1024 * 1024 + 1]})
            assertThrows(AssetFormatException.class, () -> Rac1AssetExtractor.externalAnimations(bytes, 2, 1));
        byte[] stream = new byte[128];
        var b = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset : new int[]{-1, 1, 63, 128, Integer.MAX_VALUE}) {
            b.putInt(0, offset);
            assertThrows(AssetFormatException.class, () -> Rac1AssetExtractor.externalAnimations(stream, 2, 1));
        }
        b.putInt(0, 64); b.putInt(4, 64);
        assertThrows(AssetFormatException.class, () -> Rac1AssetExtractor.externalAnimations(stream, 2, 1));
        b.putInt(4, 0);
        assertThrows(AssetFormatException.class, () -> Rac1AssetExtractor.externalAnimations(Arrays.copyOf(stream, 65), 2, 1));
    }
}
