package com.github.giannigr.racmc.serverassets;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.cache.Rac1CacheLocation;
import com.github.giannigr.racmc.iso.Rac1MediaConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Rac1WrenchServerConfigTest {
    @TempDir Path directory;

    @Test
    void dedicatedTemplateIsOptionalAndDoesNotReadClientSetting() throws Exception {
        new Rac1MediaConfig(directory.resolve("client.iso"), "", 0, "", 0)
                .write(Rac1MediaConfig.fileIn(directory));
        var config = Rac1WrenchServerConfig.read(directory, true);
        assertNull(config.image());
        assertNull(config.sharedReadCacheDirectory());
        assertTrue(Files.isRegularFile(directory.resolve(Rac1WrenchServerConfig.FILE_NAME)));
        assertFalse(Files.exists(config.cacheDirectory()));
    }

    @Test
    void literalPathsAreRelativeToConfigAndFirstDuplicateWins() throws Exception {
        Files.writeString(directory.resolve(Rac1WrenchServerConfig.FILE_NAME),
                "iso=images/disc with spaces.iso\niso=wrong.iso\ncacheDirectory=local-cache\n");
        var config = Rac1WrenchServerConfig.read(directory, true);
        assertEquals(directory.resolve("images/disc with spaces.iso"), config.image());
        assertEquals(directory.resolve("local-cache"), config.cacheDirectory());
    }

    @Test
    void integratedFallbackUsesCommonConfigurationWithoutCreatingSharedDirectory() throws Exception {
        Path iso = directory.resolve("local.iso");
        new Rac1MediaConfig(iso, "", 0, "", 0).write(Rac1MediaConfig.fileIn(directory));
        Files.writeString(Rac1CacheLocation.fileIn(directory), "directory=shared-cache\n");
        var config = Rac1WrenchServerConfig.read(directory, false);
        assertEquals(iso, config.image());
        assertEquals(directory.resolve("shared-cache"), config.sharedReadCacheDirectory());
        assertFalse(Files.exists(config.sharedReadCacheDirectory()));
        assertFalse(Files.exists(directory.resolve(Rac1WrenchServerConfig.FILE_NAME)));
    }

    @Test
    void explicitBlankOverrideDisablesIntegratedFallback() throws Exception {
        new Rac1MediaConfig(directory.resolve("client.iso"), "", 0, "", 0)
                .write(Rac1MediaConfig.fileIn(directory));
        Files.writeString(directory.resolve(Rac1WrenchServerConfig.FILE_NAME), "iso=\n");
        var config = Rac1WrenchServerConfig.read(directory, false);
        assertNull(config.image());
        assertNull(config.sharedReadCacheDirectory());
    }

    @Test
    void sharedReadCacheCannotAliasOrContainWritableServerCache() throws Exception {
        Files.writeString(Rac1CacheLocation.fileIn(directory),
                "directory=" + Rac1WrenchServerConfig.DEFAULT_CACHE);
        assertThrows(IOException.class, () -> Rac1WrenchServerConfig.read(directory, false));
        Files.writeString(Rac1CacheLocation.fileIn(directory), "directory=.\n");
        assertThrows(IOException.class, () -> Rac1WrenchServerConfig.read(directory, false));
    }

    @Test
    void unusableConfigAndParentSegmentsAreNotGuessed() throws Exception {
        Files.createDirectory(directory.resolve(Rac1WrenchServerConfig.FILE_NAME));
        assertThrows(IOException.class, () -> Rac1WrenchServerConfig.read(directory, true));
        Path other = Files.createDirectory(directory.resolve("other"));
        Files.writeString(other.resolve(Rac1WrenchServerConfig.FILE_NAME), "iso=../disc.iso\n");
        assertThrows(IOException.class, () -> Rac1WrenchServerConfig.read(other, true));
    }
}
