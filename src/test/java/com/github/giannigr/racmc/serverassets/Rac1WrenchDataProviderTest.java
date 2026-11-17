package com.github.giannigr.racmc.serverassets;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.serverassets.Rac1WrenchDataStatus.State.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.io.RandomAccessFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Rac1WrenchDataProviderTest {
    @TempDir Path directory;

    private static void await(Rac1WrenchDataProvider provider) throws Exception {
        long limit = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (provider.status().state() == VALIDATING || provider.status().state() == IMPORTING) {
            assertTrue(System.nanoTime() < limit, "Headless service did not finish");
            Thread.sleep(10);
        }
    }

    @Test
    void oversizedCacheIsRejectedBeforeWholeFileAllocation() throws Exception {
        Path file = directory.resolve("oversized.racm");
        try (var sparse = new RandomAccessFile(file.toFile(), "rw")) {
            sparse.setLength(Rac1WrenchDataProvider.MAX_CACHE_BYTES + 1);
        }
        assertFalse(Rac1WrenchDataProvider.cacheWithinLimit(file));
        assertTrue(Rac1WrenchDataProvider.cacheWithinLimit(directory.resolve("missing.racm")));
    }

    @Test
    void missingConfigurationOnlyDisablesActionsAndCloseJoinsTheOwnedWorker() throws Exception {
        var provider = Rac1WrenchDataProvider.open(directory, true);
        try {
            await(provider);
            assertEquals(MISSING, provider.status().state());
            assertTrue(provider.data().isEmpty());
        } finally {
            provider.close();
        }
        assertEquals(CLOSED, provider.status().state());
        assertTrue(provider.data().isEmpty());
        assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(
                t -> t.isAlive() && t.getName().equals("racmc-server-wrench-data")));
    }

    @Test
    void absentIsoIsFailureNotCachedOrClientNetworkData() throws Exception {
        Files.writeString(directory.resolve(Rac1WrenchServerConfig.FILE_NAME), "iso=missing.iso\n");
        try (var provider = Rac1WrenchDataProvider.open(directory, true)) {
            await(provider);
            assertEquals(FAILED, provider.status().state());
            assertTrue(provider.data().isEmpty());
            assertFalse(Files.exists(directory.resolve(Rac1WrenchServerConfig.DEFAULT_CACHE)));
        }
    }

    @Test
    void malformedConfigFailsGracefullyAndImmediateCloseNeverPublishesReady() throws Exception {
        Files.createDirectory(directory.resolve(Rac1WrenchServerConfig.FILE_NAME));
        try (var provider = Rac1WrenchDataProvider.open(directory, true)) {
            await(provider);
            assertEquals(FAILED, provider.status().state());
        }
        var provider = Rac1WrenchDataProvider.open(directory, true);
        provider.close();
        Thread.sleep(20);
        assertEquals(CLOSED, provider.status().state());
        assertTrue(provider.data().isEmpty());
    }
}
