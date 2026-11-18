package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class SoundBytes {
    private SoundBytes() {}
    static ByteBuffer view(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }
    static void range(int offset, long size, int length) throws IOException {
        if (offset < 0 || size < 0 || offset + size > length) throw new IOException("Sound range outside container");
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
