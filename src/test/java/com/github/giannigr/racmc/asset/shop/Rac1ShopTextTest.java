package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopTextTest {
    byte[] fixture() {
        byte[] data = new byte[100];
        var b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, 32); b.putInt(32, 1); b.putInt(36, 68);
        b.putInt(40, 24); b.putInt(44, 21044);
        data[56] = 0x10; data[57] = ' '; data[58] = 0x0c; data[59] = 'X';
        return data;
    }
    @Test void selectsByIdAndPreservesByteGlyphsWithoutRetailFixture() throws Exception {
        var text = Rac1ShopText.read(fixture(), 0, Set.of(21044));
        assertArrayEquals(new byte[]{0x10, ' ', 0x0c, 'X'}, text.get(21044));
        byte[] returned = text.get(21044); returned[0] = 1;
        assertEquals(0x10, text.get(21044)[0]);
        assertFalse(text.complete());
        assertEquals(3, Rac1ShopText.languageSlot("de_DE"));
        assertEquals(4, Rac1ShopText.languageSlot("es_mx"));
        assertEquals(0, Rac1ShopText.languageSlot("unsupported"));
        assertThrows(IllegalArgumentException.class, () -> text.get(1));
    }
    @Test void rejectsMissingCorruptTruncatedAndUnterminatedRecords() {
        assertThrows(AssetFormatException.class, () -> Rac1ShopText.read(fixture(), 0, Set.of(1)));
        assertThrows(AssetFormatException.class, () -> Rac1ShopText.read(new byte[31], 0, Set.of(1)));
        assertThrows(AssetFormatException.class, () -> Rac1ShopText.read(fixture(), 1, Set.of(21044)));
        for (int offset : new int[]{-1, 0, 23, 68, Integer.MAX_VALUE}) {
            byte[] bad = fixture(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(40, offset);
            assertThrows(AssetFormatException.class, () -> Rac1ShopText.read(bad, 0, Set.of(21044)));
        }
        byte[] bad = fixture(); Arrays.fill(bad, 56, bad.length, (byte)'A');
        assertThrows(AssetFormatException.class, () -> Rac1ShopText.read(bad, 0, Set.of(21044)));
    }
}
