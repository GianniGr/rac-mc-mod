package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopEnvironmentMapsTest {
    private static final String SHA = "0123456789012345678901234567890123456789";

    @Test void literalClutPermutationAndEveryAlphaRemainLossless() throws Exception {
        byte[] index = index(), gs = gs();
        var maps = Rac1ShopEnvironmentMaps.read(index, gs, SHA, 1);
        for (var role : Rac1ShopEnvironmentMaps.Role.values()) {
            byte[] actual = maps.images().get(role).rgbaGs();
            // Independent literal index pairs: CSM1 swaps 8..15 with 16..23.
            int[] indices = {0, 7, 8, 15, 16, 23, 24, 31, 255};
            int[] slots =   {0, 7,16, 23,  8, 15, 24, 31, 255};
            for (int i = 0; i < indices.length; i++) {
                int p = indices[i]*4, slot = slots[i];
                assertArrayEquals(new byte[]{(byte)slot, (byte)(255-slot), 42, (byte)(slot%129)},
                        Arrays.copyOfRange(actual, p, p+4));
            }
            boolean[] alpha = new boolean[129];
            for (int p = 3; p < actual.length; p += 4) alpha[actual[p]&255] = true;
            for (boolean present : alpha) assertTrue(present);
        }
        assertSame(maps.images().get(Rac1ShopEnvironmentMaps.Role.CHROME), maps.forSelector(-2));
        assertSame(maps.images().get(Rac1ShopEnvironmentMaps.Role.GLASS_ENVIRONMENT), maps.forSelector(-3));
        assertThrows(IllegalArgumentException.class, () -> maps.forSelector(0x19));
        byte[] snapshot = maps.forSelector(-2).rgbaGs();
        Arrays.fill(gs, (byte)0);
        byte[] exposed = maps.forSelector(-2).rgbaGs();
        exposed[0] = 100;
        assertArrayEquals(snapshot, maps.forSelector(-2).rgbaGs());
        assertThrows(UnsupportedOperationException.class, () -> maps.images().clear());
    }

    @Test void rejectsMalformedDescriptorsAndOutOfBoundsTextures() {
        rejects(0, 4097);
        rejects(4, Integer.MAX_VALUE);
        rejects(0x90, -256);
        rejects(0x90, 1025);
        rejects(0x100+16, 0); // pixel descriptor is PSMT8, not PSMCT32
        rejects(0x100+16+4, 64 | (128<<16));
        rejects(0x100+16+12, 0x7fffff00);
        rejects(0x100+16+12, 1025);
        rejects(0x100+3*16+8, 1024); // ambiguous destination
        byte[] gs = gs();
        gs[3] = (byte)129;
        assertThrows(AssetFormatException.class, () -> Rac1ShopEnvironmentMaps.read(index(), gs, SHA, 1));
        assertThrows(AssetFormatException.class, () -> Rac1ShopEnvironmentMaps.read(index(), new byte[1024], SHA, 1));
        assertThrows(AssetFormatException.class, () -> Rac1ShopEnvironmentMaps.read(new byte[4], gs(), SHA, 1));
        assertThrows(AssetFormatException.class, () -> Rac1ShopEnvironmentMaps.read(index(), gs(), SHA, 2));
        assertThrows(AssetFormatException.class, () -> Rac1ShopEnvironmentMaps.read(index(), gs(), "invalid", 1));
        assertThrows(AssetFormatException.class, () -> Rac1ShopEnvironmentMaps.read(index(), new byte[4*1024*1024+1], SHA, 1));
    }

    @Test void descriptorStagingOffsetIsNotAssumedToEqualDestination() throws Exception {
        byte[] index = index(), gs = gs();
        byte[] moved = Arrays.copyOf(gs, gs.length+16384);
        System.arraycopy(gs, 1024, moved, gs.length, 16384);
        put(index, 0x100+16+12, gs.length);
        assertArrayEquals(Rac1ShopEnvironmentMaps.read(index(), gs, SHA, 1).forSelector(-2).rgbaGs(),
                Rac1ShopEnvironmentMaps.read(index, moved, SHA, 1).forSelector(-2).rgbaGs());
    }

    @Test void imageRejectsExtentPastPhysicalGsAddressSpace() {
        var role = Rac1ShopEnvironmentMaps.Role.CHROME;
        byte[] rgba = new byte[128*128*4];
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1ShopEnvironmentMaps.Image(role, 4*1024*1024-256, 0, rgba));
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1ShopEnvironmentMaps.Image(role, 1024, 4*1024*1024-256, rgba));
    }

    private static void rejects(int at, int value) {
        byte[] index = index();
        put(index, at, value);
        assertThrows(AssetFormatException.class, () -> Rac1ShopEnvironmentMaps.read(index, gs(), SHA, 1));
    }
    private static byte[] index() {
        byte[] b = new byte[0x140];
        put(b, 0, 4); put(b, 4, 0x100);
        put(b, 0x90, 1024); put(b, 0x94, 0);
        put(b, 0x98, 18432); put(b, 0x9c, 17408);
        int[] addresses = {0, 1024, 17408, 18432};
        for (int d = 0; d < 4; d++) {
            int at = 0x100+d*16, side = d==1 ? 128 : 64;
            put(b, at, d%2==0 ? 0 : 19);
            put(b, at+4, d%2==0 ? 0 : side | (side<<16));
            put(b, at+8, addresses[d]); put(b, at+12, addresses[d]);
        }
        return b;
    }
    private static byte[] gs() {
        byte[] b = new byte[22528];
        for (int base : new int[]{0,17408})
            for (int i = 0; i < 256; i++) {
                b[base+i*4] = (byte)i; b[base+i*4+1] = (byte)(255-i);
                b[base+i*4+2] = 42; b[base+i*4+3] = (byte)(i%129);
            }
        for (int p = 0; p < 16384; p++) b[1024+p] = (byte)p;
        for (int p = 0; p < 4096; p++) b[18432+p] = (byte)p;
        return b;
    }
    private static void put(byte[] b, int at, int value) {
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(at, value);
    }
}
