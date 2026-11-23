package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.cache.shop.Rac1ShopSceneTableCodec;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopSceneTablesTest {
    private byte[] overlay(boolean pal) {
        int base = 0x1c8000, delta = pal ? 0x280 : 0;
        byte[] bytes = new byte[16 + 0x3000];
        var b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, base); b.putInt(4, bytes.length - 16);
        for (int i = 0; i < 64; i++) {
            b.putInt(16 + 0x1ca598 + delta - base + i * 4, -1);
            b.putInt(16 + 0x1ca698 + delta - base + i * 4, 9);
        }
        b.putInt(16 + 0x1ca598 + delta - base + ('A' - 32) * 4, (9 * 16 << 16) | 18 * 16);
        for (int i = 0; i < 24; i++) {
            b.putInt(16 + 0x1ca538 + delta - base + i * 4, 20321 + i % 12);
            b.putFloat(16 + 0x1ca798 + delta - base + i * 4, .01f);
        }
        for (int i = 0; i < 8; i++)
            b.putFloat(16 + 0x1c8d90 + delta + 15 * 48 - base + i * 4, i + 1);
        b.putInt(16 + 0x1c9480 + delta + 15 * 64 + 32 - base, 1006);
        return bytes;
    }
    @Test void explicitPalMappingAnd48BytePanelStrideProduceEquivalentTables() throws Exception {
        var ntsc = Rac1ShopSceneTables.read(overlay(false), false,new int[4],.25f);
        var pal = Rac1ShopSceneTables.read(overlay(true), true,new int[4],.25f);
        assertEquals(ntsc.led(), pal.led());
        assertArrayEquals(ntsc.tickerIds(), pal.tickerIds());
        assertArrayEquals(new float[]{1,2,3,4,5,6,7,8}, pal.blasterPanel());
        assertEquals(1006, pal.ammoClass());
        assertEquals((9 * 16 << 16) | 18 * 16, ntsc.led().cell('A'));
        assertTrue(ntsc.requiredTextIds().contains(20332));
        int[] refs = ntsc.tickerIds(); refs[0] = 1; assertEquals(20321, ntsc.tickerIds()[0]);
        assertEquals(ntsc.led(), Rac1ShopSceneTableCodec.read(Rac1ShopSceneTableCodec.write(ntsc)).led());
        assertEquals(.25f,Rac1ShopSceneTableCodec.read(Rac1ShopSceneTableCodec.write(ntsc)).vendorPackedScale());
    }
    @Test void malformedGlyphAdvancesSourceBoundsAndCodecExtentFail() throws Exception {
        int[] cells = new int[64], advances = new int[64]; Arrays.fill(cells, -1);
        cells[0] = (63 * 16 << 16);
        assertThrows(IllegalArgumentException.class, () -> new Rac1ShopLedFont(cells, advances));
        cells[0] = -1; advances[0] = 33;
        assertThrows(IllegalArgumentException.class, () -> new Rac1ShopLedFont(cells, advances));
        assertThrows(AssetFormatException.class, () -> Rac1ShopSceneTables.read(new byte[0], false,new int[4],.25f));
        assertThrows(AssetFormatException.class, () -> Rac1ShopSceneTables.read(overlay(false), false,new int[0],.25f));
        assertThrows(AssetFormatException.class, () -> Rac1ShopSceneTables.read(overlay(false), false,new int[4],Float.NaN));
        byte[] valid = Rac1ShopSceneTableCodec.write(Rac1ShopSceneTables.read(overlay(false), false,new int[4],.25f));
        assertThrows(java.io.IOException.class, () -> Rac1ShopSceneTableCodec.read(Arrays.copyOf(valid, valid.length - 1)));
        valid[0] = 2;
        assertThrows(java.io.IOException.class, () -> Rac1ShopSceneTableCodec.read(valid));
    }
}
