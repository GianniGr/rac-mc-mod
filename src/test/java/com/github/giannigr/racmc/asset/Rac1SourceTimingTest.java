package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class Rac1SourceTimingTest {
    private static byte[] sequence(int firstRateBits, int lastRateBits, int overrideBits) {
        var bytes = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(0x10, (byte) 2);
        bytes.putInt(0x18, overrideBits);
        bytes.putInt(0x1c, 48);
        bytes.putInt(0x20, 80);
        for (int i = 0; i < 2; i++) {
            int frame = 48 + 32 * i;
            bytes.putInt(frame, i == 0 ? firstRateBits : lastRateBits);
            bytes.putShort(frame + 4, (short) ((i == 0 ? 7 : 33) * 16));
            bytes.putShort(frame + 6, (short) 1);
            bytes.putShort(frame + 8, (short) 8);
            bytes.putShort(frame + 12, (short) 8);
            bytes.putShort(frame + 22, (short) 32767);
        }
        return bytes.array();
    }

    @Test
    void rawHeaderTimeIsNotKeyIndexOrSecondsTimesSixty() throws Exception {
        var keys = MobySequenceReader.read(sequence(Float.floatToRawIntBits(0.5f), 0, 0), 0, 1);
        var timing = keys.sourceTiming();
        assertArrayEquals(new int[] { 112, 528 }, timing.headerTimes16());
        assertArrayEquals(new int[] { 0, 1 }, timing.sampledSourceKeys());
        assertArrayEquals(new float[] { 0, 2f / 60 }, timing.sourceSeconds());
        assertNotEquals(33, keys.times()[1] * 60);
        int[] copy = timing.headerTimes16();
        copy[0] = 0;
        assertEquals(112, timing.headerTimes16()[0]);
    }

    @Test
    void droppedZeroDurationPoseDoesNotDiscardItsOriginalTimingMetadata() throws Exception {
        var keys = MobySequenceReader.read(
                sequence(Float.floatToRawIntBits(Float.POSITIVE_INFINITY), 0, 0), 0, 1);
        assertEquals(1, keys.times().length);
        assertArrayEquals(new int[] { 112, 528 }, keys.sourceTiming().headerTimes16());
        assertArrayEquals(new int[] { 1 }, keys.sourceTiming().sampledSourceKeys());
        assertArrayEquals(new float[] { 0, 0 }, keys.sourceTiming().sourceSeconds());
    }

    @Test
    void syntheticLoopEndpointIsNotMisreportedAsAnotherOriginalKey() throws Exception {
        var keys = MobySequenceReader.read(sequence(Float.floatToRawIntBits(0.5f),
                Float.floatToRawIntBits(0.5f), 0), 0, 1);
        assertTrue(keys.loop());
        assertArrayEquals(new int[] { 0, 1, -1 }, keys.sourceTiming().sampledSourceKeys());
        assertEquals(2, keys.sourceTiming().headerTimes16().length);
    }

    @Test
    void rawRateBitsArePreservedIncludingTerminalNanPayload() throws Exception {
        var keys = MobySequenceReader.read(sequence(Float.floatToRawIntBits(0.5f),
                0x7fa12345, 0), 0, 1);
        assertEquals(0x7fa12345, keys.sourceTiming().keyRateBits()[1]);
    }

    @Test
    void malformedMappingsAndUnstatedSyntheticTimingFailClosed() {
        assertFalse(Rac1SourceTiming.unavailable().available());
        assertThrows(IllegalArgumentException.class, () -> new Rac1SourceTiming(0,
                new int[] { 0 }, new int[] { 0 }, new float[] { 0 }, new int[] { 9 }));
        assertThrows(IllegalArgumentException.class, () -> new Rac1SourceTiming(0,
                new int[] { 0 }, new int[] { 0 }, new float[] { Float.NaN }, new int[] { 0 }));
    }
}
