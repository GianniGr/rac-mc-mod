package com.github.giannigr.racmc.asset.clank;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.cache.Rac1CacheCodec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Bounded local envelope, not a model serializer or a RACM format/catalogue bump. */
public final class Rac1ClankSidecarCodec {
    public static final int MAX_BYTES = 96 * 1024 * 1024;
    public static final int FORMAT = 1;
    public static final int IMPORTER = 1;
    // Pin P35 c9ab8fb's public encoding. Independent of unrelated RACM section/catalogue versions.
    public static final int REQUIRED_MODEL_ENCODING = 1;
    private static final int MAGIC = 0x4b4e4c43; // CLNK, little endian
    private static final int HEADER = 32;

    private Rac1ClankSidecarCodec() {}

    public static byte[] encode(Rac1ClankAssets assets) throws IOException {
        byte[] identity = assets.discKey().getBytes(StandardCharsets.UTF_8);
        var sections = new ArrayList<byte[]>();
        long size = HEADER + identity.length;
        for (var role : Rac1ClankAssets.Role.values()) {
            byte[] bytes = assets.encodedModel(role);
            size += 12L + bytes.length;
            sections.add(bytes);
        }
        if (size > MAX_BYTES) throw new IOException("Clank sidecar exceeds aggregate bound");
        byte[] bytes = new byte[(int) size];
        var out = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(MAGIC).putInt(FORMAT).putInt(IMPORTER).putInt(REQUIRED_MODEL_ENCODING)
                .putInt(bytes.length).putInt(identity.length).putInt(3).putInt(0);
        out.put(identity);
        int index = 0;
        for (var role : Rac1ClankAssets.Role.values()) {
            byte[] section = sections.get(index++);
            out.putInt(role.classNumber()).putInt(section.length)
                    .putInt(Rac1CacheCodec.checksum(section)).put(section);
        }
        out.putInt(28, crc(bytes));
        return bytes;
    }

    public static Rac1ClankAssets decode(byte[] bytes, String expectedDisc) throws IOException {
        if (bytes.length < HEADER || bytes.length > MAX_BYTES)
            throw new IOException("Invalid Clank sidecar size");
        try {
            var in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (in.getInt() != MAGIC || in.getInt() != FORMAT || in.getInt() != IMPORTER
                    || in.getInt() != REQUIRED_MODEL_ENCODING || in.getInt() != bytes.length)
                throw new IOException("Stale/unknown Clank sidecar contract");
            int keyLength = in.getInt();
            if (keyLength < 1 || keyLength > 256 || in.getInt() != 3 || in.getInt() != crc(bytes))
                throw new IOException("Invalid Clank manifest/checksum");
            if (keyLength > in.remaining()) throw new IOException("Truncated Clank identity");
            byte[] key = new byte[keyLength];
            in.get(key);
            String disc = new String(key, StandardCharsets.UTF_8);
            Rac1ClankAssets.requireDiscKey(disc);
            if (!disc.equals(expectedDisc)) throw new IOException("Clank cache belongs to another disc");
            var models = new ArrayList<Rac1AnimatedAsset>();
            for (var role : Rac1ClankAssets.Role.values()) {
                if (in.remaining() < 12 || in.getInt() != role.classNumber())
                    throw new IOException("Wrong/missing Clank model role");
                int size = in.getInt(), checksum = in.getInt();
                if (size <= 0 || size > Rac1CacheCodec.MAX_MODEL_BYTES || size > in.remaining())
                    throw new IOException("Invalid Clank model size");
                byte[] section = new byte[size];
                in.get(section);
                if (Rac1CacheCodec.checksum(section) != checksum)
                    throw new IOException("Clank model checksum mismatch");
                models.add(Rac1CacheCodec.decodeAnimatedModel(section));
            }
            if (in.hasRemaining()) throw new IOException("Trailing Clank sidecar bytes");
            return new Rac1ClankAssets(disc, models.get(0), models.get(1), models.get(2));
        } catch (IllegalArgumentException | java.nio.BufferUnderflowException failure) {
            throw new IOException("Malformed Clank sidecar", failure);
        }
    }

    private static int crc(byte[] bytes) {
        var crc = new java.util.zip.CRC32();
        crc.update(bytes, 0, 28);
        crc.update(new byte[4]);
        crc.update(bytes, HEADER, bytes.length - HEADER);
        return (int) crc.getValue();
    }
}
