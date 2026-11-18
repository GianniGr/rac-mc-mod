package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PsAdpcmTest {
    @Test void nibbleOrderSignShiftAndGoldenPcm() throws Exception {
        byte[] frame = new byte[16];
        frame[1] = 1;
        frame[2] = 0x10; frame[3] = 0x32; frame[4] = 0x76; frame[5] = (byte) 0x98;
        var decoded = PsAdpcm.decode(frame, 0, frame.length);
        assertEquals(28, decoded.samples());
        var pcm = ByteBuffer.wrap(decoded.pcm()).order(ByteOrder.LITTLE_ENDIAN);
        assertArrayEquals(new short[]{0, 4096, 8192, 12288, 24576, 28672, -32768, -28672},
                new short[]{pcm.getShort(), pcm.getShort(), pcm.getShort(), pcm.getShort(),
                        pcm.getShort(), pcm.getShort(), pcm.getShort(), pcm.getShort()});
        frame[0] = 12;
        pcm = ByteBuffer.wrap(PsAdpcm.decode(frame, 0, 16).pcm()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, pcm.getShort()); assertEquals(1, pcm.getShort()); assertEquals(2, pcm.getShort());
    }
    @Test void predictorHistoryRoundingAndSaturation() throws Exception {
        byte[] frames = new byte[32];
        frames[0] = 12;
        java.util.Arrays.fill(frames, 2, 16, (byte) 0x11);
        frames[16] = 0x1c; frames[17] = 1;
        var pcm = ByteBuffer.wrap(PsAdpcm.decode(frames, 0, 32).pcm()).order(ByteOrder.LITTLE_ENDIAN);
        // Filter 1 with previous sample1: round(60/64) =1, so zero residual retains1.
        assertEquals(1, pcm.getShort(56)); assertEquals(1, pcm.getShort(58));
        frames[0] = 0;
        java.util.Arrays.fill(frames, 2, 16, (byte) 0x77);
        frames[16] = 0x20;
        java.util.Arrays.fill(frames, 18, 32, (byte) 0x77);
        pcm = ByteBuffer.wrap(PsAdpcm.decode(frames, 0, 32).pcm()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(32767, pcm.getShort(56));
        assertEquals(32767, pcm.getShort(110));
    }
    @Test void retainsNonzeroLoopAndStopsAtFirstEnd() throws Exception {
        byte[] frames = new byte[64];
        frames[17] = 4; frames[49] = 3;
        var decoded = PsAdpcm.decode(frames, 0, 64);
        assertEquals(28, decoded.loopStart()); assertEquals(112, decoded.loopEnd()); assertTrue(decoded.repeat());
        frames[33] = 1;
        assertEquals(3, PsAdpcm.decode(frames, 0, 64).frames());
    }
    @Test void allFivePredictorsMatchHandCalculatedHistoryVector() throws Exception {
        int[][] expected = {{0, 0, 0, 0}, {2, 2, 2, 2}, {3, 4, 5, 6},
                {2, 1, 0, -1}, {3, 4, 5, 6}};
        for (int filter = 0; filter < 5; filter++) {
            byte[] frames = new byte[32]; frames[0] = 12;
            java.util.Arrays.fill(frames, 2, 16, (byte) 0x11);
            frames[15] = 0x21; // final history is h1=2, h2=1
            frames[16] = (byte) ((filter << 4) | 12); frames[17] = 1;
            var pcm = ByteBuffer.wrap(PsAdpcm.decode(frames, 0, 32).pcm()).order(ByteOrder.LITTLE_ENDIAN);
            for (int n = 0; n < 4; n++) assertEquals(expected[filter][n], pcm.getShort(56 + n * 2));
        }
    }
    @Test void refusesMalformedBoundariesPredictorsFlagsAndUnterminatedFrames() {
        byte[] data = new byte[32];
        assertThrows(IOException.class, () -> PsAdpcm.decode(data, -1, 32));
        assertThrows(IOException.class, () -> PsAdpcm.decode(data, 1, 32));
        assertThrows(IOException.class, () -> PsAdpcm.decode(data, 0, 33));
        assertThrows(IOException.class, () -> PsAdpcm.decode(data, 0, 15));
        assertThrows(IOException.class, () -> PsAdpcm.decode(data, 0, 32));
        data[0] = 0x50;
        assertThrows(IOException.class, () -> PsAdpcm.decode(data, 0, 32));
        data[0] = 13;
        assertThrows(IOException.class, () -> PsAdpcm.decode(data, 0, 32));
        data[0] = 0; data[1] = 8;
        assertThrows(IOException.class, () -> PsAdpcm.decode(data, 0, 32));
    }
}
