package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused checks for the handling of paths.
 *
 * <p>Two kinds of paths have to be safe: the path of the file the player selects, and the paths
 * of the files inside a disc image. The second kind matters most, because a malformed image must
 * never be able to make a later extraction step write outside of its target directory.
 */
class IsoPathSafetyTest {

    private static final String PRODUCT_CODE = "SCES_509.16";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A relative path inside an image is accepted")
    void relativePathIsAccepted() {
        assertEquals("SYSTEM.CNF", IsoImagePath.requireSafe("SYSTEM.CNF"));
        assertEquals("folder/file.bin", IsoImagePath.requireSafe("folder/file.bin"));
    }

    @Test
    @DisplayName("An absolute path inside an image is refused")
    void absolutePathIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("\\windows\\system32"));
    }

    @Test
    @DisplayName("A path inside an image that walks upwards is refused")
    void traversalPathIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe(".."));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("../escape.bin"));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("folder/../../escape.bin"));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("./file.bin"));
    }

    @Test
    @DisplayName("A path inside an image with an empty segment is refused")
    void emptySegmentIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("folder//file.bin"));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("folder/"));
    }

    @Test
    @DisplayName("A path inside an image with a drive or stream separator is refused")
    void driveSeparatorIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("C:/file.bin"));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("file:stream"));
    }

    @Test
    @DisplayName("A path inside an image with a control character is refused")
    void controlCharacterIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("file\u0000.bin"));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("file\n.bin"));
    }

    @Test
    @DisplayName("A path inside an image that is too long is refused")
    void tooLongPathIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> IsoImagePath.requireSafe("x".repeat(IsoImagePath.MAX_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> IsoImagePath.requireSafe("x".repeat(IsoImagePath.MAX_SEGMENT_LENGTH + 1)));
    }

    @Test
    @DisplayName("A missing or empty path inside an image is refused")
    void missingPathIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe(null));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe(""));
        assertThrows(IllegalArgumentException.class, () -> IsoImagePath.requireSafe("   "));
    }

    @Test
    @DisplayName("An opened image refuses to read a file through an unsafe path")
    void openedImageRefusesUnsafePaths() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("image.iso", disc);

        try (Rac1IsoSelectionResult outcome = disc.validator().inspect(image)) {
            Rac1DiscImage opened = outcome.image();
            assertThrows(IllegalArgumentException.class, () -> opened.readFile("../escape.bin", 1024));
            assertThrows(IllegalArgumentException.class, () -> opened.openFile("../escape.bin"));
            assertThrows(IllegalArgumentException.class, () -> opened.readFile("/absolute", 1024));
            assertThrows(IllegalArgumentException.class, () -> opened.readFile("missing.bin", 1024));
            assertThrows(IllegalArgumentException.class, () -> opened.readFile("SYSTEM.CNF", 4));
        }
    }

    @Test
    @DisplayName("A file of an image is read completely and only up to its own size")
    void fileIsReadCompletely() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("image.iso", disc);

        try (Rac1IsoSelectionResult outcome = disc.validator().inspect(image)) {
            Rac1DiscImage opened = outcome.image();
            byte[] content = opened.readFile(PRODUCT_CODE, 1024 * 1024);
            assertEquals(disc.executable().length, content.length);
            assertEquals(disc.executable()[0], content[0]);

            try (var stream = opened.openFile(PRODUCT_CODE)) {
                assertEquals(disc.executable().length, stream.readAllBytes().length);
            }
        }
    }

    @Test
    @DisplayName("The files of an image are listed with their paths")
    void filesAreListed() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PRODUCT_CODE,
                SyntheticIso.elf(1024));
        Path image = writeImage("image.iso", disc);

        try (Rac1IsoSelectionResult outcome = disc.validator().inspect(image)) {
            assertTrue(outcome.image().listFilePaths().contains("SYSTEM.CNF"));
            assertTrue(outcome.image().listFilePaths().contains(PRODUCT_CODE));
        }
    }

    private Path writeImage(String name, SyntheticDisc disc) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, disc.image());
        return file;
    }
}
