package com.github.giannigr.racmc.client.sound;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1PcmStreamTest {
    @Test void readsAlignedPcmAndCloseIsIdempotent() {
        var stream = new Rac1PcmStream(new byte[]{1, 2, 3, 4}, 22050);
        assertEquals(22050, stream.getFormat().getSampleRate());
        var first = stream.read(3);
        assertTrue(first.isDirect()); assertEquals(2, first.remaining()); assertEquals(1, first.get());
        assertEquals(2, stream.read(100).remaining()); assertEquals(0, stream.read(100).remaining());
        stream.close(); stream.close(); assertEquals(0, stream.read(100).remaining());
    }
    @Test void sustainedLoopPlaysIntroOnceThenUsesVerifiedNonzeroLoopStart() {
        var stream = new Rac1PcmStream(new byte[]{1, 2, 3, 4, 5, 6}, 22050, 1, 3);
        var first = stream.read(14);
        byte[] bytes = new byte[first.remaining()]; first.get(bytes);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 3, 4, 5, 6, 3, 4, 5, 6}, bytes);
        assertEquals(3, stream.read(2).get());
        stream.close(); stream.close();
        assertEquals(0, stream.read(14).remaining());
    }
    @Test void loopRejectsMalformedMarkersAndBoundsEachReadAllocation() {
        assertThrows(IllegalArgumentException.class, () ->
                new Rac1PcmStream(new byte[]{1, 2}, 22050, 1, 1));
        var stream = new Rac1PcmStream(new byte[]{1, 2}, 22050, 0, 1);
        assertEquals(1024 * 1024, stream.read(Integer.MAX_VALUE).remaining());
        stream.close();
    }
}
