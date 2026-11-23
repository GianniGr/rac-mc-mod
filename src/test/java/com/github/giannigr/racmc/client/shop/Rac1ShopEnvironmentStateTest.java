package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopEnvironmentStateTest {
    @Test void originalFieldsHaveLiteralGsMeanings() {
        var state = new Rac1ShopEnvironmentState(0x8000000044L, 0x5360bL, 0x120L, 5);
        // ALPHA: (Cs-Cd)*As/128+Cd; FIX=128 is present but not selected.
        assertEquals(0, state.alpha()&3);
        assertEquals(1, (state.alpha()>>>2)&3);
        assertEquals(0, (state.alpha()>>>4)&3);
        assertEquals(1, (state.alpha()>>>6)&3);
        assertEquals(128, (state.alpha()>>>32)&255);
        assertEquals(1, state.test()&1);
        assertEquals(5, (state.test()>>>1)&7); // GEQUAL
        assertEquals(96, (state.test()>>>4)&255);
        assertEquals(3, (state.test()>>>12)&3); // RGB_ONLY
        assertEquals(0, (state.test()>>>14)&1);
        assertEquals(1, (state.test()>>>16)&1);
        assertEquals(2, (state.test()>>>17)&3); // depth GEQUAL
        assertTrue(state.linearMagnification()); assertTrue(state.linearMinification());
        assertTrue(state.clampS()); assertTrue(state.clampT());
        assertFalse(state.mipmaps()); assertFalse(state.destinationAlphaTest());
        assertTrue(state.depthGreaterOrEqual());
    }

    @Test void alphaFailStillWritesRgbAndBoundaryIsInclusive() {
        var state = new Rac1ShopEnvironmentState(0x8000000044L, 0x5360bL, 0x120L, 5);
        for (int alpha = 0; alpha < 256; alpha++) {
            var mask = state.alphaTest(alpha);
            assertTrue(mask.rgb());
            assertEquals(alpha >= 96, mask.alpha());
            assertEquals(alpha >= 96, mask.depth());
        }
        assertThrows(IllegalArgumentException.class, () -> state.alphaTest(-1));
        assertThrows(IllegalArgumentException.class, () -> state.alphaTest(256));
    }

    @Test void rejectsSubstituteBlendDepthFilterOrWrapState() {
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1ShopEnvironmentState(0x48, 0x5360b, 0x120, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1ShopEnvironmentState(0x8000000044L, 0x5308b, 0x120, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1ShopEnvironmentState(0x8000000044L, 0x5360b, 0x100, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1ShopEnvironmentState(0x8000000044L, 0x5360b, 0x120, 0));
        var withK = new Rac1ShopEnvironmentState(0x8000000044L, 0x5360b, 0x123400000120L, 5);
        assertEquals(0x123400000120L, withK.tex1());
    }
}
