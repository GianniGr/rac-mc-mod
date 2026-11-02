package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused checks for the media manager, the state machine that keeps the game closed until a
 * verified disc image is configured.
 */
class Rac1MediaManagerTest {

    private static final String PAL_PRODUCT_CODE = "SCES_509.16";

    private static final long TIMEOUT_MILLIS = 20_000L;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Without a configured image the game stays closed")
    void withoutMediaTheGameStaysClosed() {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Rac1MediaManager manager = newManager(disc);
        try {
            manager.start();

            assertEquals(Rac1MediaState.UNCONFIGURED, manager.state());
            assertFalse(manager.isReady());
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("A verified image opens the gate and is remembered")
    void verifiedImageOpensTheGate() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());
        Path config = configFile();

        Rac1MediaManager manager = newManager(disc);
        try {
            manager.start();
            awaitVerification(manager, () -> manager.select(image));

            assertEquals(Rac1MediaState.VALID, manager.state());
            assertTrue(manager.isReady());
            assertEquals(image.toAbsolutePath().normalize(), manager.configuredPath().orElseThrow());

            Rac1MediaConfig stored = Rac1MediaConfig.read(config);
            assertTrue(stored != null, "The path of the image has to be remembered");
            assertEquals(image.toAbsolutePath().normalize(), stored.path());
            assertEquals("PAL v2.00", stored.revision());
            assertEquals(disc.image().length, stored.imageSize());
            assertEquals(SyntheticDisc.sha1(disc.image()), stored.imageSha1());
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("The remembered image is verified again when the game starts")
    void rememberedImageIsVerifiedAgain() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());

        Rac1MediaManager first = newManager(disc);
        try {
            first.start();
            awaitVerification(first, () -> first.select(image));
            assertEquals(Rac1MediaState.VALID, first.state());
        } finally {
            first.close();
        }

        Rac1MediaManager second = newManager(disc);
        try {
            awaitVerification(second, second::start);

            assertEquals(Rac1MediaState.VALID, second.state());
            assertTrue(second.isReady());
            assertEquals(image.toAbsolutePath().normalize(), second.configuredPath().orElseThrow());
        } finally {
            second.close();
        }
    }

    @Test
    @DisplayName("The game stays closed while the remembered image is verified")
    void theGameStaysClosedWhileTheImageIsVerified() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());

        Rac1MediaManager first = newManager(disc);
        try {
            first.start();
            awaitVerification(first, () -> first.select(image));
        } finally {
            first.close();
        }

        Rac1MediaManager second = newManager(disc);
        try {
            second.start();

            assertEquals(Rac1MediaState.CHECKING, second.state(),
                    "The game stays closed while the image is verified");
            assertFalse(second.isReady());

            awaitVerification(second, () -> {
            });
            assertEquals(Rac1MediaState.VALID, second.state());
        } finally {
            second.close();
        }
    }

    @Test
    @DisplayName("A missing image sends the game back to the gate")
    void missingImageSendsTheGameBackToTheGate() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());

        Rac1MediaManager first = newManager(disc);
        try {
            first.start();
            awaitVerification(first, () -> first.select(image));
        } finally {
            first.close();
        }

        Files.delete(image);

        Rac1MediaManager second = newManager(disc);
        try {
            awaitVerification(second, second::start);

            assertEquals(Rac1MediaState.INVALID, second.state());
            assertFalse(second.isReady());
        } finally {
            second.close();
        }
    }

    @Test
    @DisplayName("A changed image sends the game back to the gate")
    void changedImageSendsTheGameBackToTheGate() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());

        Rac1MediaManager first = newManager(disc);
        try {
            first.start();
            awaitVerification(first, () -> first.select(image));
        } finally {
            first.close();
        }

        byte[] changed = disc.image().clone();
        changed[changed.length - 1] = (byte) (changed[changed.length - 1] ^ 0x5A);
        Files.write(image, changed);

        Rac1MediaManager second = newManager(disc);
        try {
            awaitVerification(second, second::start);

            assertEquals(Rac1MediaState.INVALID, second.state());
            assertFalse(second.isReady());
        } finally {
            second.close();
        }
    }

    @Test
    @DisplayName("A failed replacement does not remove the valid image")
    void failedReplacementKeepsTheValidImage() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());
        Path broken = writeImage("broken.iso", new byte[64 * 1024]);

        Rac1MediaManager manager = newManager(disc);
        try {
            manager.start();
            awaitVerification(manager, () -> manager.select(image));
            assertEquals(Rac1MediaState.VALID, manager.state());

            awaitVerification(manager, () -> manager.select(broken));

            assertEquals(Rac1MediaState.VALID, manager.state(),
                    "The valid image stays in place when a replacement fails");
            assertTrue(manager.isReady());
            assertEquals(image.toAbsolutePath().normalize(), manager.configuredPath().orElseThrow());
            assertFalse(manager.attemptResult().orElseThrow().isSupported(),
                    "The failed attempt is reported to the player");

            Rac1MediaConfig stored = Rac1MediaConfig.read(configFile());
            assertTrue(stored != null, "The configuration still holds the valid image");
            assertEquals(image.toAbsolutePath().normalize(), stored.path());
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("The verification reports how far it has come")
    void verificationReportsProgress() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());

        Rac1MediaManager manager = newManager(disc);
        try {
            manager.start();
            awaitVerification(manager, () -> manager.select(image));

            assertEquals(Rac1MediaState.VALID, manager.state());
            assertEquals(disc.image().length, manager.bytesTotal(),
                    "The manager has to know how much it has to read");
            assertTrue(manager.bytesRead() > 0, "The manager has to report the progress");
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("The verification runs on a worker thread")
    void verificationRunsOnAWorkerThread() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());

        Rac1MediaManager manager = newManager(disc);
        try {
            manager.start();

            // The call returns before the verification is finished, so the screen is never blocked.
            long before = manager.completedAttempts();
            manager.select(image);
            assertEquals(before, manager.completedAttempts(),
                    "The verification must not run on the calling thread");

            awaitVerification(manager, () -> {
            });
            assertEquals(Rac1MediaState.VALID, manager.state());
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("The configuration holds the path of the image and nothing else")
    void configurationHoldsOnlyThePath() throws IOException, InterruptedException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("disc.iso", disc.image());

        Rac1MediaManager manager = newManager(disc);
        try {
            manager.start();
            awaitVerification(manager, () -> manager.select(image));
        } finally {
            manager.close();
        }

        String content = Files.readString(configFile(), StandardCharsets.UTF_8);
        assertTrue(content.contains("path="), content);
        assertFalse(content.contains("BOOT2"), "No part of the image may be stored");
        assertTrue(content.length() < 1024, "The configuration stays small");
    }

    private Rac1MediaManager newManager(SyntheticDisc disc) {
        return new Rac1MediaManager(new Rac1DiscValidator(disc.catalog()), configFile());
    }

    private Path configFile() {
        return Rac1MediaConfig.fileIn(tempDir.resolve("config"));
    }

    private Path writeImage(String name, byte[] content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file;
    }

    /**
     * Runs a trigger and waits until the verification it started is over.
     */
    private static void awaitVerification(Rac1MediaManager manager, Runnable trigger)
            throws InterruptedException {
        long target = manager.completedAttempts() + 1;
        trigger.run();
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (manager.completedAttempts() >= target) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("The verification did not finish in time");
    }
}
