package com.github.giannigr.racmc.asset.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class GreenShipContainerTest {
    @Test
    void plansOnlyMainClassAndTextureMipZero() throws Exception {
        byte[] header = integers(0x30, 0x500, 0x400, 0x20000);
        var layout = GreenShipContainer.layout(header, 0x30000);
        assertEquals(new GreenShipContainer.Slice(0x30, 0x3d0), layout.hullClass());
        assertEquals(new GreenShipContainer.Slice(0x500, 0x10430), layout.hullTexture());
        assertThrows(AssetFormatException.class, () -> GreenShipContainer.layout(header, 0x20000));
        assertThrows(AssetFormatException.class,
                () -> GreenShipContainer.layout(integers(0x30, 0x500, 0x400, 0x10500), 0x30000));
        assertThrows(AssetFormatException.class,
                () -> GreenShipContainer.layout(integers(0x30, -1, 0x400, 0x20000), 0x30000));
    }

    @Test
    void decodesShipPifWithCsm1AndGsAlpha() throws Exception {
        byte[] pif = textureBytes();
        ByteBuffer buf = ByteBuffer.wrap(pif).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x30 + 16 * 4, 0x40112233);
        pif[0x431] = 8;
        var texture = GreenShipContainer.decodeTexture(pif);
        assertEquals(256, texture.width());
        assertEquals(256, texture.height());
        assertEquals(0x80332211, texture.pixel(1, 0));
        buf.putInt(0x1c, 128);
        assertThrows(AssetFormatException.class, () -> GreenShipContainer.decodeTexture(pif));
    }

    @Test
    void decodesNormalHullTriangleAndBothFrameZeroPoses() throws Exception {
        byte[] hull = new byte[0x500];
        ByteBuffer data = ByteBuffer.wrap(hull).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(0, 0x60).put(4, (byte) 1).put(8, (byte) 1).put(0xc, (byte) 3);
        data.putInt(0x14, 0x300).putInt(0x18, 0x350).putInt(0x1c, 0x370).putFloat(0x24, 1);
        data.putInt(0x4c, 0x380).putInt(0x50, 0x400);
        data.putInt(0x60, 0x100).putShort(0x64, (short) 7).putInt(0x68, 0x200)
                .put(0x6c, (byte) 6).put(0x6f, (byte) 3);
        data.putInt(0x100, 0x65030000); // V2-16: three UV pairs
        data.putInt(0x110, 0x6e030000); // V4-8: strip header and indices
        data.put(0x118, (byte) 1).put(0x119, (byte) 2).put(0x11a, (byte) 3);
        data.put(0x11b, (byte) 3).put(0x11c, (byte) 3).put(0x11d, (byte) 3); // strip flush
        data.putInt(0x120, 0x6c040000); // V4-32: one texture-zero material
        data.putInt(0x20c, 3).putInt(0x214, 3).putInt(0x218, 0x20).putInt(0x21c, 0x60);
        data.putShort(0x23a, (short) 1024).putShort(0x24c, (short) 1024);
        for (int axis = 0; axis < 4; axis++) {
            data.putFloat(0x300 + 20 * axis, 1);
        }
        data.putInt(0x370, 1).putInt(0x374, 0x378)
                .putShort(0x378, (short) 1).put(0x37d, (byte) 0xff);
        frameZero(data, 0x380, 0x3b0);
        frameZero(data, 0x400, 0x430);

        var result = GreenShipContainer.decode(hull, textureBytes());
        assertEquals(3, result.mesh().vertexCount());
        assertEquals(1, result.mesh().triangleCount());
        assertEquals(1, result.parked().times().length);
        assertEquals(1, result.closed().times().length);
        assertEquals(0, result.glassJoint());
        data.putInt(0x50, Integer.MAX_VALUE);
        assertThrows(AssetFormatException.class, () -> GreenShipContainer.decode(hull, textureBytes()));
    }

    @Test
    void listZeroUsesTerminalChainJointNotTargetOrListNumber() throws Exception {
        byte[] hull = new byte[0x100];
        ByteBuffer data = ByteBuffer.wrap(hull).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(0x1c, 0x80).putInt(0x80, 1).putInt(0x84, 0xa0);
        data.putShort(0xa0, (short) 2).putShort(0xa2, (short) 1);
        hull[0xa4] = 0;
        hull[0xa5] = 4;
        hull[0xa6] = 3;
        hull[0xa7] = (byte) 0xff;
        assertEquals(4, GreenShipContainer.glassJoint(hull, 5));
        assertThrows(AssetFormatException.class, () -> GreenShipContainer.glassJoint(hull, 4));
        hull[0xa7] = 0;
        assertThrows(AssetFormatException.class, () -> GreenShipContainer.glassJoint(hull, 5));
    }

    private static byte[] integers(int... fields) {
        ByteBuffer buffer = ByteBuffer.allocate(fields.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int field : fields) {
            buffer.putInt(field);
        }
        return buffer.array();
    }

    private static byte[] textureBytes() {
        byte[] pif = new byte[0x10430];
        ByteBuffer.wrap(pif).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0, 1).putInt(4, 0x10).putInt(0x10, 0x50494632)
                .putInt(0x18, 256).putInt(0x1c, 256).putInt(0x20, 0x13);
        return pif;
    }

    private static void frameZero(ByteBuffer data, int sequence, int frame) {
        data.put(sequence + 0x10, (byte) 1).putInt(sequence + 0x1c, frame);
        data.putFloat(frame, 60).putShort(frame + 6, (short) 1).putShort(frame + 8, (short) 8)
                .putShort(frame + 12, (short) 8).putShort(frame + 22, (short) 0x7fff);
    }
}
