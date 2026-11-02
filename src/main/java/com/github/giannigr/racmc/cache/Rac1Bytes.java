package com.github.giannigr.racmc.cache;

import java.io.ByteArrayOutputStream;

/**
 * The writer and the reader of the numbers of a cache file.
 *
 * <p>Every number of a cache file is stored in the little endian order. The writer and the reader
 * of this class do that, so that the sections of the file and its header use the same order.
 */
final class Rac1Bytes {

    /** The writer of the numbers. */
    static final class Writer {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void putInt(int value) {
            out.write(value & 0xFF);
            out.write((value >> 8) & 0xFF);
            out.write((value >> 16) & 0xFF);
            out.write((value >> 24) & 0xFF);
        }

        void putFloat(float value) {
            putInt(Float.floatToRawIntBits(value));
        }

        void putBoolean(boolean value) {
            out.write(value ? 1 : 0);
        }

        void putBytes(byte[] value) {
            out.writeBytes(value);
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    /** The reader of the numbers. */
    static final class Reader {

        private final byte[] data;
        private int at;

        Reader(byte[] data) {
            this.data = data;
        }

        int getInt() throws java.io.IOException {
            if (at + 4 > data.length) {
                throw new java.io.IOException("The cache file ends before a number was read.");
            }
            int value = (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8)
                    | ((data[at + 2] & 0xFF) << 16) | ((data[at + 3] & 0xFF) << 24);
            at += 4;
            return value;
        }

        float getFloat() throws java.io.IOException {
            return Float.intBitsToFloat(getInt());
        }

        boolean getBoolean() throws java.io.IOException {
            if (at >= data.length) {
                throw new java.io.IOException("The cache file ends before a value was read.");
            }
            return data[at++] != 0;
        }

        void getBytes(byte[] target) throws java.io.IOException {
            if (at + target.length > data.length) {
                throw new java.io.IOException("The cache file ends before the data was read.");
            }
            System.arraycopy(data, at, target, 0, target.length);
            at += target.length;
        }

        /**
         * The number of the bytes that were not read yet.
         *
         * @return the number of bytes
         */
        int remaining() {
            return data.length - at;
        }
    }

    private Rac1Bytes() {
    }
}
