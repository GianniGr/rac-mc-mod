package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.SyntheticVendor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class Rac1VendorCacheTest {
    private static ByteBuffer buffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] file() throws IOException {
        return Rac1CacheCodec.write(SyntheticVendor.content(), Rac1CacheStore.SCHEMA_VERSION);
    }

    private static Rac1CacheContent read(byte[] bytes) throws IOException {
        return Rac1CacheCodec.read(bytes, "synthetic-vendor", Rac1CacheStore.SCHEMA_VERSION);
    }

    @Test
    void pairGeometrySkeletonAnimationAndTexturesRoundTrip() throws Exception {
        var expected = SyntheticVendor.content();
        var actual = read(file());
        assertNotNull(actual.vendor());
        assertEquals(11, actual.vendor().body().classNumber());
        assertEquals(1143, actual.vendor().hologram().classNumber());
        assertArrayEquals(expected.vendor().body().mesh().positions(),
                actual.vendor().body().mesh().positions());
        assertArrayEquals(expected.vendor().hologram().skeleton().inverseBind(),
                actual.vendor().hologram().skeleton().inverseBind());
        assertArrayEquals(expected.vendor().body().animations()[0].rotations(),
                actual.vendor().body().animations()[0].rotations());
        assertArrayEquals(expected.vendor().hologram().textures()[0].pixels(),
                actual.vendor().hologram().textures()[0].pixels());
    }

    @Test
    void oldCrateSchemaAndConverterAreInvalidated() throws Exception {
        byte[] oldSchema = Rac1CacheCodec.write(SyntheticVendor.content(), 4);
        assertTrue(assertThrows(IOException.class, () -> read(oldSchema))
                .getMessage().contains("schema"));
        byte[] oldConverter = file();
        buffer(oldConverter).putInt(12, 3);
        buffer(oldConverter).putInt(44, Rac1CacheCodec.checksum(oldConverter, 0, 44));
        assertTrue(assertThrows(IOException.class, () -> read(oldConverter))
                .getMessage().contains("converter"));
        assertEquals(5, Rac1CacheStore.SCHEMA_VERSION);
        assertEquals(4, Rac1CacheCodec.CONVERTER_VERSION);
    }

    @Test
    void missingVendorSectionCannotSilentlyUseOldContent() throws Exception {
        byte[] bytes = file();
        int entry = Rac1CacheCodec.HEADER_SIZE + 4 * Rac1CacheCodec.SECTION_ENTRY_SIZE;
        buffer(bytes).putInt(entry, 99);
        assertTrue(assertThrows(IOException.class, () -> read(bytes))
                .getMessage().contains("all of the assets"));
    }

    @Test
    void duplicateVendorSectionIsRejected() throws Exception {
        byte[] bytes = file();
        buffer(bytes).putInt(Rac1CacheCodec.HEADER_SIZE, Rac1CacheCodec.SECTION_VENDOR);
        assertThrows(IOException.class, () -> read(bytes));
    }

    @Test
    void corruptPairLengthFailsEvenWithRepairedChecksums() throws Exception {
        byte[] bytes = file();
        int entry = Rac1CacheCodec.HEADER_SIZE + 4 * Rac1CacheCodec.SECTION_ENTRY_SIZE;
        int offset = (int) buffer(bytes).getLong(entry + 8);
        int length = (int) buffer(bytes).getLong(entry + 16);
        buffer(bytes).putInt(offset + 1, Integer.MAX_VALUE);
        buffer(bytes).putInt(entry + 24, Rac1CacheCodec.checksum(bytes, offset, length));
        int payload = Rac1CacheCodec.HEADER_SIZE + 5 * Rac1CacheCodec.SECTION_ENTRY_SIZE;
        buffer(bytes).putInt(40, Rac1CacheCodec.checksum(bytes, payload, bytes.length - payload));
        buffer(bytes).putInt(44, Rac1CacheCodec.checksum(bytes, 0, 44));
        assertTrue(assertThrows(IOException.class, () -> read(bytes))
                .getMessage().contains("vendor asset"));
    }

    @Test
    void syntheticFixtureWithoutPairStillHasAnExplicitVendorSection() throws Exception {
        byte[] bytes = Rac1CacheCodec.write(SyntheticAssets.content("synthetic-vendor"),
                Rac1CacheStore.SCHEMA_VERSION);
        assertNull(read(bytes).vendor());
        assertEquals(5, buffer(bytes).getInt(20));
    }
}
