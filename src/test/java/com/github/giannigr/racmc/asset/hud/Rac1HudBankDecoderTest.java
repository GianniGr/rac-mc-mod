package com.github.giannigr.racmc.asset.hud;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1HudBankDecoderTest {
    private static byte[] header() {
        byte[] bytes = new byte[228];
        var b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort(0, (short) 2).putShort(2, (short) 2);
        b.putInt(4, 180).putInt(8, 196).putInt(12, 204).putInt(16, 212);
        for (int i = 0; i < 5; i++) { b.putInt(0x14 + i * 4, 1); b.putInt(0x34 + i * 4, 2); }
        b.putInt(0x54, 1026);
        b.putShort(180, (short) 0x754F).putShort(182, (short) 2);
        b.putShort(188, (short) 0xFFFF);
        b.putShort(202, (short) 1);
        b.putInt(204, 0x80000000); b.putInt(212, 0x80000400); b.putInt(220, 0x80000401);
        return bytes;
    }
    private static byte[][] banks() {
        byte[][] banks = {new byte[1026], new byte[0], new byte[0], new byte[0], new byte[0]};
        banks[0][16 * 4] = 12; banks[0][16 * 4 + 1] = 34;
        banks[0][16 * 4 + 2] = 56; banks[0][16 * 4 + 3] = 64;
        banks[0][1024] = 8;
        banks[0][1025] = 16;
        banks[0][8 * 4 + 3] = (byte) 128;
        return banks;
    }
    private static void word(byte[] b, int at, int value) {
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(at, value);
    }
    @Test void frameReferencesLinearTexelsClutPermutationAndAlpha() throws Exception {
        var frames = new Rac1HudBankDecoder(header(), banks()).decode(0x754F);
        assertEquals(2, frames.size());
        assertEquals(0x800C2238, frames.getFirst().pixel(0, 0));
        assertEquals(0xFF000000, frames.getLast().pixel(0, 0));
    }
    @Test void rejectsMalformedTableOffsetsAndBankLengths() {
        for (int at : new int[]{4, 8, 12, 16, 0x14, 0x34, 0x54}) {
            byte[] h = header(); word(h, at, Integer.MAX_VALUE);
            assertThrows(AssetFormatException.class, () -> new Rac1HudBankDecoder(h, banks()));
        }
        assertThrows(AssetFormatException.class, () -> new Rac1HudBankDecoder(new byte[50], banks()));
        assertThrows(AssetFormatException.class, () -> new Rac1HudBankDecoder(header(), new byte[0][]));
    }
    @Test void rejectsBadFramePageAndPixelPaletteBounds() throws Exception {
        byte[] h = header(); h[198] = 127;
        assertThrows(AssetFormatException.class, () -> new Rac1HudBankDecoder(h, banks()).decode(0x754F));
        byte[] badPixels = header(); word(badPixels, 212, 0x7FFFFFFF);
        assertThrows(AssetFormatException.class, () -> new Rac1HudBankDecoder(badPixels, banks()).decode(0x754F));
        byte[] badPalette = header(); word(badPalette, 204, 16);
        assertThrows(AssetFormatException.class, () -> new Rac1HudBankDecoder(badPalette, banks()).decode(0x754F));
        byte[] badDimension = header(); badDimension[218] = 31;
        assertThrows(AssetFormatException.class, () -> new Rac1HudBankDecoder(badDimension, banks()).decode(0x754F));
        assertThrows(AssetFormatException.class, () -> new Rac1HudBankDecoder(header(), banks()).decode(123));
        assertThrows(AssetFormatException.class,
                () -> Rac1HudBankDecoder.indexed(new byte[0], 0, 1, 1, new byte[1024], 0));
    }
    @Test void genericElfAndOverlayVirtualRangesAreBounded() throws Exception {
        byte[] elf = new byte[92];
        word(elf, 0, 0x464C457F); elf[4] = 1; elf[5] = 1;
        word(elf, 28, 52); elf[42] = 32; elf[44] = 1;
        word(elf, 52, 1); word(elf, 56, 84); word(elf, 60, 0x1000); word(elf, 68, 8);
        elf[86] = 42;
        assertEquals(42, Rac1HudImporter.elfRange(elf, 0x1002, 4)[0]);
        assertThrows(AssetFormatException.class, () -> Rac1HudImporter.elfRange(elf, 0x1007, 2));
        assertThrows(AssetFormatException.class, () -> Rac1HudImporter.elfRange(elf, 0x1000, -1));
        byte[] overlay = new byte[20]; word(overlay, 0, 0x2000); word(overlay, 4, 4); overlay[16] = 7;
        assertEquals(7, Rac1HudImporter.overlayRange(overlay, 0x2000, 4)[0]);
        assertThrows(AssetFormatException.class, () -> Rac1HudImporter.overlayRange(overlay, 0x2003, 2));
        word(overlay, 4, Integer.MAX_VALUE);
        assertThrows(AssetFormatException.class, () -> Rac1HudImporter.overlayRange(overlay, 0x2000, 1));
    }
}
