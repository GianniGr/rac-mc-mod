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
}
