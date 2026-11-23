package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.rescue.Rac1PodAssets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Rac1PodLeaseTest {
    @TempDir Path dir;

    @Test
    void boundedPodReaderBorrowsSelectionWithoutASecondFullValidation() throws Exception {
        var disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, "SCES_509.16", SyntheticIso.elf(4096));
        var path = Files.write(dir.resolve("synthetic.iso"), disc.image());
        var profile = disc.profile();
        var key = profile.revision().label() + "|" + disc.productCode() + "|" + profile.imageSha1();
        try (var media = new Rac1MediaManager(disc.validator(), dir.resolve("media.properties"))) {
            long start = System.nanoTime();
            media.select(path);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (media.completedAttempts() == 0 && System.nanoTime() < until) Thread.sleep(2);
            var validationNanos = System.nanoTime() - start;
            assertTrue(media.isReady());
            assertEquals(disc.image().length, media.bytesRead());
            assertEquals(1, media.completedAttempts());
            long gen = media.generation();
            CompletableFuture.runAsync(() -> {
                try (var lease = media.lease(path, key)) {
                    assertEquals(gen, lease.generation());
                    lease.checkValid();
                    // Synthetic media contains no Veldin TOC. The real bounded importer must
                    // fail closed without opening another path or hashing the image again.
                    assertThrows(IllegalArgumentException.class, () -> Rac1PodAssets.read(lease));
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }).get(10, TimeUnit.SECONDS);
            assertEquals(1, media.completedAttempts());
            assertTrue(media.isReady());
            System.out.printf("pod lease fixture: %d synthetic bytes startup %.3f ms, full validations=1, pod repeats=0%n",
                    media.bytesRead(), validationNanos / 1_000_000.0);
        }
    }
}
