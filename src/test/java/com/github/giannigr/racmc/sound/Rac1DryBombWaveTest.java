package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Rac1DryBombWaveTest {
    private static final int START = Rac1DryBombWave.WAVE_START;

    @Test void findsStrictlyNextToneUsingOnlyApprovedWords() throws Exception {
        var readStarts = new ArrayList<Integer>();
        int next = Rac1DryBombWave.nextWaveStart((grain, field) -> {
            if (field == 0) return switch (grain) {
                case 10, 11, 12, 326, 560 -> 1;
                default -> 5; // Not a tone; never read its start word.
            };
            assertEquals(0x18, field);
            readStarts.add(grain);
            return switch (grain) {
                case 10 -> START + 80;
                case 11, 326 -> START;
                case 12 -> START - 16;
                case 560 -> START + 48;
                default -> throw new AssertionError("unexpected grain " + grain);
            };
        });
        assertEquals(START + 48, next);
        assertEquals(List.of(10, 11, 12, 326, 560), readStarts);
    }

    @Test void stopsOnUnboundedOrCorruptToneMetadata() {
        assertThrows(IOException.class, () -> boundary(START, START));
        assertThrows(IOException.class, () -> boundary(START, START + 65536 + 16));
        assertThrows(IOException.class, () -> boundary(START, START + 17));
        assertThrows(IOException.class, () -> boundary(START - 16, START + 16));
        assertEquals(START + 65536, assertDoesNotThrow(() -> boundary(START, START + 65536)));
    }

    private static int boundary(int selected, int next) throws IOException {
        return Rac1DryBombWave.nextWaveStart((grain, field) -> field == 0
                ? (grain == 326 || grain == 600 ? 1 : 0) : grain == 326 ? selected : next);
    }

    @Test void requiresNonLoopingEndAndNeverDecodesPadding() throws Exception {
        byte[] span = new byte[48];
        assertThrows(IOException.class, () -> Rac1DryBombWave.decodeSpan(span, START + 48));
        span[17] = 3;
        assertThrows(IOException.class, () -> Rac1DryBombWave.decodeSpan(span, START + 48));
        span[17] = 1;
        span[1] = 4;
        assertThrows(IOException.class, () -> Rac1DryBombWave.decodeSpan(span, START + 48));
        span[1] = 0;
        span[32] = (byte) 0xff; // Not another decoded frame after a valid end.
        var valid = Rac1DryBombWave.decodeSpan(span, START + 48);
        assertEquals(2, valid.decoded().frames());
        assertEquals(112, valid.decoded().pcm().length);
        assertNotEquals(valid.spanHash(), valid.adpcmHash());
        assertThrows(IOException.class, () -> Rac1DryBombWave.decodeSpan(span, START + 32));
    }

    @Test void dryClipDoesNotEnterTheSidecarOrChangeItsVersion() throws Exception {
        var original = Rac1SoundPipelineTest.catalog();
        var local = new Rac1AudioCatalog(original.clips(), original.clip(Rac1AudioEvent.CRATE_BREAK));
        var event = Rac1AudioEvent.BOMB_GLOVE_DRY_BURST;
        assertSame(local.dryBomb(), local.clip(event));
        assertFalse(event.importsSample());
        String hash = "a".repeat(40);
        var encoded = Rac1SoundCache.encode(hash, local);
        assertArrayEquals(Rac1SoundCache.encode(hash, original), encoded);
        assertNull(Rac1SoundCache.decode(encoded, hash).clip(event));
        assertEquals(5, Rac1SoundCache.VERSION);
    }
}
