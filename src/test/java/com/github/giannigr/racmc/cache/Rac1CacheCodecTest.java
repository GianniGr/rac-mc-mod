package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the format of the cache file.
 *
 * <p>The checks use assets that were built by hand. No data of the game is used anywhere.
 */
class Rac1CacheCodecTest {

    /** The identity of the disc the checks use. */
    private static final String KEY = "NTSC-U v1.00|SCUS_971.99|test";

    @Test
    @DisplayName("A cache that was written can be read back")
    void aCacheCanBeReadBack() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);

        Rac1CacheContent content = Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION);

        assertEquals(KEY, content.discKey());
        assertEquals(4, content.staticAsset().mesh().vertexCount());
        assertEquals(2, content.staticAsset().mesh().triangleCount());
        assertEquals(1, content.staticAsset().textures().length);
        assertEquals(2, content.staticAsset().textures()[0].width());
        assertEquals(4, content.animatedAsset().mesh().vertexCount());
        assertEquals(SyntheticAssets.JOINT_COUNT, content.animatedAsset().skeleton().jointCount());
        assertEquals(1, content.animatedAsset().animations().length);
        assertEquals("sequence_0", content.animatedAsset().animations()[0].name());
        assertEquals(2, content.animatedAsset().animations()[0].keyCount());
        assertTrue(content.animatedAsset().animations()[0].loop());
        assertEquals(1, content.animatedAsset().textures().length);
        assertEquals(4, content.blaster().mesh().vertexCount());
        assertEquals(56, content.handJoint(), "The joint of the hand has to survive the round trip");
    }

    @Test
    @DisplayName("The identity of the assets survives the round trip")
    void theIdentityOfTheAssetsSurvivesTheRoundTrip() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);

        Rac1CacheContent content = Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION);

        assertEquals(452, content.staticAsset().classNumber(),
                "The number of the class of the scenery has to survive the round trip");
        assertEquals("tie_452", content.staticAsset().name(),
                "The name of the scenery has to survive the round trip");
        assertEquals(0, content.animatedAsset().classNumber(),
                "The number of the class of the character has to survive the round trip");
        assertEquals("moby_0", content.animatedAsset().name(),
                "The name of the character has to survive the round trip");
        assertEquals(0, content.blaster().classNumber(),
                "The number of the class of the weapon has to survive the round trip");
        assertEquals("moby_0", content.blaster().name(),
                "The name of the weapon has to survive the round trip");
    }

    @Test
    @DisplayName("The textures of the assets survive the round trip")
    void theTexturesOfTheAssetsSurviveTheRoundTrip() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);

        Rac1CacheContent content = Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION);

        assertEquals(1, content.staticAsset().textures().length);
        assertEquals(2, content.staticAsset().textures()[0].width());
        assertEquals(2, content.staticAsset().textures()[0].height());
        assertEquals(0xFF, content.staticAsset().textures()[0].pixels()[0] & 0xFF);
        assertEquals(0x40, content.staticAsset().textures()[0].pixels()[1] & 0xFF);
        assertEquals(1, content.animatedAsset().textures().length);
        assertEquals(1, content.blaster().textures().length);
    }

    @Test
    @DisplayName("The file of a cache starts with the magic of the format")
    void theFileStartsWithTheMagic() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);

        assertEquals('R', file[0]);
        assertEquals('A', file[1]);
        assertEquals('C', file[2]);
        assertEquals('M', file[3]);
    }

    @Test
    @DisplayName("A cache whose bytes were changed is refused")
    void aDamagedCacheIsRefused() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        file[file.length - 40] ^= 0x55;

        IOException failure = assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION));
        assertNotNull(failure.getMessage());
    }

    @Test
    @DisplayName("A cache that was cut short is refused")
    void aTruncatedCacheIsRefused() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        byte[] truncated = new byte[file.length / 2];
        System.arraycopy(file, 0, truncated, 0, truncated.length);

        assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(truncated, KEY, Rac1CacheStore.SCHEMA_VERSION));
    }

    @Test
    @DisplayName("A cache of another disc is refused")
    void aCacheOfAnotherDiscIsRefused() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);

        IOException failure = assertThrows(IOException.class, () -> Rac1CacheCodec.read(file,
                "PAL v2.00|SCES_509.16|other", Rac1CacheStore.SCHEMA_VERSION));
        assertTrue(failure.getMessage().contains("another disc"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    @Test
    @DisplayName("A cache that was written for another version of the schema is refused")
    void aCacheOfAnotherSchemaIsRefused() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);

        IOException failure = assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION + 1));
        assertTrue(failure.getMessage().contains("schema"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    @Test
    @DisplayName("Data that is not a cache file is refused")
    void dataThatIsNotACacheIsRefused() {
        byte[] broken = new byte[128];
        java.util.Arrays.fill(broken, (byte) 0x41);

        assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(broken, KEY, Rac1CacheStore.SCHEMA_VERSION));
        assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(new byte[4], KEY, Rac1CacheStore.SCHEMA_VERSION));
    }

    @Test
    @DisplayName("The identity of the disc is written into the file")
    void theIdentityIsWrittenIntoTheFile() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        String text = new String(file, StandardCharsets.ISO_8859_1);

        assertTrue(text.contains(KEY), "The file has to name the disc it belongs to");
    }

    @Test
    @DisplayName("The file carries the version of the format, of the schema and of the converter")
    void theFileCarriesThreeDifferentVersions() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);

        assertEquals(Rac1CacheCodec.FORMAT_VERSION, readShort(file, 4),
                "The header has to name the version of the format");
        assertEquals(Rac1CacheStore.SCHEMA_VERSION, readInt(file, 8),
                "The header has to name the version of the schema of the assets");
        assertEquals(Rac1CacheCodec.CONVERTER_VERSION, readInt(file, 12),
                "The header has to name the version of the converter");
    }

    @Test
    @DisplayName("A cache that was written for another version of the format is refused")
    void aCacheOfAnotherFormatVersionIsRefused() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        writeShort(file, 4, Rac1CacheCodec.FORMAT_VERSION - 1);

        IOException failure = assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION));
        assertTrue(failure.getMessage().contains("format version"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    @Test
    @DisplayName("A cache that was written by an older converter is refused")
    void aCacheOfAnOldConverterVersionIsRefused() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        writeInt(file, 12, Rac1CacheCodec.CONVERTER_VERSION - 1);
        writeInt(file, 44, Rac1CacheCodec.checksum(file, 0, 44));

        IOException failure = assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION));
        assertTrue(failure.getMessage().contains("converter version"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    @Test
    @DisplayName("A cache that was written by another converter is refused")
    void aCacheOfAnotherConverterVersionIsRefused() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        writeInt(file, 12, Rac1CacheCodec.CONVERTER_VERSION + 1);
        writeInt(file, 44, Rac1CacheCodec.checksum(file, 0, 44));

        IOException failure = assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION));
        assertTrue(failure.getMessage().contains("converter version"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    @Test
    @DisplayName("A damaged version of the converter is refused by the checksum of the header")
    void aDamagedConverterVersionIsRefusedByTheHeaderChecksum() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        file[12] ^= 0x40;

        IOException failure = assertThrows(IOException.class,
                () -> Rac1CacheCodec.read(file, KEY, Rac1CacheStore.SCHEMA_VERSION));
        assertTrue(failure.getMessage().contains("header"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    /**
     * Writes a short in the little endian order, the way the file holds it.
     */
    private static void writeShort(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    /**
     * Writes an int in the little endian order, the way the file holds it.
     */
    private static void writeInt(byte[] data, int offset, int value) {
        for (int index = 0; index < 4; index++) {
            data[offset + index] = (byte) ((value >> (index * 8)) & 0xFF);
        }
    }

    /**
     * Reads a short in the little endian order, the way the file holds it.
     */
    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Reads an int in the little endian order, the way the file holds it.
     */
    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }
}
