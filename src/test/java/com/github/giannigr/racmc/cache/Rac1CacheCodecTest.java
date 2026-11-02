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
}
