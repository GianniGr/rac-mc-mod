package com.github.giannigr.racmc.asset.ship;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.MobyClassReader;
import com.github.giannigr.racmc.asset.MobySequenceReader;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.Rac1TextureReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Class 531's raw spaceships[1] container. The extra class and texture are not imported. */
public final class GreenShipContainer {
    public static final int HEADER_BYTES = 16;
    private static final int MAX_CONTAINER_BYTES = 512 * 1024;
    private static final int TEXTURE_SIZE = 256;
    private static final int PALETTE = 0x30;
    private static final int PIXELS = 0x430;
    private static final int MIP_ZERO_END = PIXELS + TEXTURE_SIZE * TEXTURE_SIZE;

    private GreenShipContainer() {}

    public record Slice(int offset, int length) {}
    public record Layout(Slice hullClass, Slice hullTexture) {}

    /** Coordinates remain in the conventions of the normal Moby decoder, not renderer space. */
    public record Hull(MobyClassReader.Mesh mesh, MobyClassReader.Skeleton skeleton,
                       MobySequenceReader.Keys parked, MobySequenceReader.Keys closed,
                       int glassJoint, float classScale, int metalPacketCount, Rac1Texture texture) {}

    public static Layout layout(byte[] header, long extentBytes) throws AssetFormatException {
        if (header.length != HEADER_BYTES || extentBytes > MAX_CONTAINER_BYTES
                || extentBytes < HEADER_BYTES) {
            throw new AssetFormatException("Invalid green ship container extent/header.");
        }
        ByteBuffer fields = littleEndian(header);
        int hull = fields.getInt();
        int texture = fields.getInt();
        int extra = fields.getInt();
        int extraTexture = fields.getInt();
        if (hull < HEADER_BYTES || (long) hull + MobyClassReader.HEADER_SIZE > extra
                || extra >= texture || (long) texture + MIP_ZERO_END > extraTexture
                || extraTexture >= extentBytes) {
            throw new AssetFormatException("Green ship container sections overlap or are out of bounds.");
        }
        return new Layout(new Slice(hull, extra - hull), new Slice(texture, MIP_ZERO_END));
    }

    public static Hull decode(byte[] hull, byte[] texture) throws AssetFormatException {
        if (hull.length < MobyClassReader.HEADER_SIZE || hull.length > MAX_CONTAINER_BYTES
                || texture.length != MIP_ZERO_END) {
            throw new AssetFormatException("Invalid green ship class/texture slice size.");
        }
        MobyClassReader reader = MobyClassReader.of(hull);
        float scale = reader.scale();
        if (!Float.isFinite(scale) || scale <= 0 || reader.sequenceCount() < 3
                || MobyClassReader.HEADER_SIZE + reader.sequenceCount() * 4 > hull.length) {
            throw new AssetFormatException("Invalid green ship scale or sequence table.");
        }
        int parkedOffset = reader.sequenceOffset(1);
        int closedOffset = reader.sequenceOffset(2);
        int sequenceLimit = hull.length - MobySequenceReader.SEQUENCE_HEADER_SIZE;
        if (parkedOffset < MobyClassReader.HEADER_SIZE || parkedOffset > sequenceLimit
                || closedOffset < MobyClassReader.HEADER_SIZE || closedOffset > sequenceLimit) {
            throw new AssetFormatException("Green ship sequences 1 and 2 must both be in bounds.");
        }
        int glassJoint = glassJoint(hull, reader.jointCount());
        var mesh = reader.readMesh();
        if (mesh.vertexCount() == 0 || mesh.indices().length == 0) {
            throw new AssetFormatException("Green ship has no normal hull triangles.");
        }
        var parked = reader.readSequence(parkedOffset);
        var closed = reader.readSequence(closedOffset);
        if (parked.times().length == 0 || closed.times().length == 0) {
            throw new AssetFormatException("Green ship sequences are missing frame zero.");
        }
        return new Hull(mesh, reader.readSkeleton(), parked, closed, glassJoint, scale,
                Byte.toUnsignedInt(hull[6]), decodeTexture(texture));
    }

    static Rac1Texture decodeTexture(byte[] texture) throws AssetFormatException {
        ByteBuffer data = littleEndian(texture);
        if (texture.length != MIP_ZERO_END || data.getInt(0) != 1 || data.getInt(4) != 0x10
                || data.getInt(0x10) != 0x50494632 // 2FIP
                || data.getInt(0x18) != TEXTURE_SIZE || data.getInt(0x1c) != TEXTURE_SIZE
                || data.getInt(0x20) != 0x13) {
            throw new AssetFormatException("Green ship texture is not one 256x256 PSMT8 PIF.");
        }
        byte[] argb = new byte[TEXTURE_SIZE * TEXTURE_SIZE * 4];
        for (int pixel = 0; pixel < argb.length / 4; pixel++) {
            int index = Byte.toUnsignedInt(texture[PIXELS + pixel]);
            // CSM1 swaps bits 3 and 4 in the palette address, not in the pixel order.
            int entry = PALETTE + 4 * ((index & 0xe7) | (index & 0x08) << 1 | (index & 0x10) >> 1);
            int dst = pixel * 4;
            argb[dst] = (byte) Rac1TextureReader.alpha(Byte.toUnsignedInt(texture[entry + 3]));
            System.arraycopy(texture, entry, argb, dst + 1, 3);
        }
        return new Rac1Texture(TEXTURE_SIZE, TEXTURE_SIZE, argb);
    }

    static int glassJoint(byte[] hull, int jointCount) throws AssetFormatException {
        ByteBuffer data = littleEndian(hull);
        if (hull.length < MobyClassReader.HEADER_SIZE) {
            throw new AssetFormatException("Truncated green ship class header.");
        }
        int listTable = data.getInt(0x1c);
        if (listTable < MobyClassReader.HEADER_SIZE || listTable > hull.length - 8
                || data.getInt(listTable) < 1) {
            throw new AssetFormatException("Missing green ship glass joint list.");
        }
        int list = data.getInt(listTable + 4);
        if (list < MobyClassReader.HEADER_SIZE || list > hull.length - 4) {
            throw new AssetFormatException("Invalid green ship glass joint list offset.");
        }
        int count = Short.toUnsignedInt(data.getShort(list));
        int targets = Short.toUnsignedInt(data.getShort(list + 2));
        long end = (long) list + 4 + count + targets;
        if (count == 0 || count > jointCount || targets > jointCount
                || end >= hull.length || hull[(int) end] != (byte) 0xff) {
            throw new AssetFormatException("Invalid green ship glass joint chain size.");
        }
        for (int index = 0; index < count; index++) {
            if (Byte.toUnsignedInt(hull[list + 4 + index]) >= jointCount) {
                throw new AssetFormatException("Green ship glass chain names an invalid joint.");
            }
        }
        return Byte.toUnsignedInt(hull[list + 3 + count]);
    }

    private static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
