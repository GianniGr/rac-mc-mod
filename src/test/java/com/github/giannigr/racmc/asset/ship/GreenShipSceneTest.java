package com.github.giannigr.racmc.asset.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class GreenShipSceneTest {
    @Test
    void readsOnlyChunkDirectoryAndRejectsOverlapsAndOutOfBounds() throws Exception {
        byte[] bytes = new byte[GreenShipScene.DIRECTORY_BYTES];
        ByteBuffer tab = littleEndian(bytes);
        tab.putInt(0).putInt(0x400).putInt(0x800).putInt(0x80);
        assertEquals(List.of(new GreenShipContainer.Slice(0x800, 0x400),
                new GreenShipContainer.Slice(0x1000, 0x80)),
                GreenShipScene.directory(bytes, 0x1800));
        assertThrows(AssetFormatException.class, () -> GreenShipScene.directory(bytes, 0x107f));
        tab.putInt(8, 0x300);
        assertThrows(AssetFormatException.class, () -> GreenShipScene.directory(bytes, 0x1800));
    }

    @Test
    void verifiesRetailCastSlots() throws Exception {
        byte[] bytes = new byte[0x60];
        ByteBuffer chunk = littleEndian(bytes);
        chunk.putShort(0, (short) 598).putShort(0xc, (short) 3);
        chunk.putInt(0x14, 0x20).putInt(0x18, 0x30).putInt(0x1c, 0x40);
        chunk.putInt(0x30, 10).putInt(0x40, 531);
        var cast = GreenShipScene.firstCast(bytes);
        assertEquals(598, cast.endTick());
        assertEquals(2, cast.shipSlot());
        assertEquals(531, cast.shipClass());
        assertEquals(0x40, cast.shipOffset());
        assertEquals(0, cast.ratchetSlot());
        assertEquals(0x20, cast.ratchetOffset());
        chunk.putInt(0x40, 533);
        assertThrows(AssetFormatException.class, () -> GreenShipScene.firstCast(bytes));
        chunk.putInt(0x40, 531);
        chunk.putInt(0x20, 530);
        assertThrows(AssetFormatException.class, () -> GreenShipScene.firstCast(bytes));
    }

    @Test
    void validatesStoredSizeSeparatelyFromDirectoryPadding() throws Exception {
        byte[] header = new byte[16];
        ByteBuffer b = littleEndian(header);
        b.put(0, (byte) 'W').put(1, (byte) 'A').put(2, (byte) 'D').putInt(3, 32_768);
        assertEquals(32_768, GreenShipScene.storedFirstChunkBytes(header, 43_008));
        assertThrows(AssetFormatException.class, () -> GreenShipScene.storedFirstChunkBytes(header, 32_767));
        assertThrows(AssetFormatException.class, () -> GreenShipScene.storedFirstChunkBytes(header, 51_201));
        assertThrows(AssetFormatException.class, () -> GreenShipScene.firstCompressedCast(header, 43_008));
    }

    @Test
    void decodesOnlySelectedWadAndReportsActorOffsetsInTheDecodedChunk() throws Exception {
        byte[] decoded = new byte[0x50];
        littleEndian(decoded).putShort(0, (short) 598).putShort(0xc, (short) 3)
                .putInt(0x14, 0x20).putInt(0x18, 0x30).putInt(0x1c, 0x40)
                .putInt(0x30, 10).putInt(0x40, 531);
        byte[] bytes = new byte[18 + decoded.length];
        littleEndian(bytes).put(0, (byte) 'W').put(1, (byte) 'A').put(2, (byte) 'D')
                .putInt(3, bytes.length).put(17, (byte) (decoded.length - 18));
        System.arraycopy(decoded, 0, bytes, 18, decoded.length);
        var result = GreenShipScene.firstCompressedCast(bytes, 43_008);
        assertEquals(0x40, result.shipOffset());
        assertEquals(0x20, result.ratchetOffset());
        // A truncated long-literal command must fail closed rather than escape the ship parser.
        byte[] truncated = java.util.Arrays.copyOf(bytes, 17);
        littleEndian(truncated).putInt(3, 17);
        assertThrows(AssetFormatException.class,
                () -> GreenShipScene.firstCompressedCast(truncated, 43_008));
    }

    private static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
