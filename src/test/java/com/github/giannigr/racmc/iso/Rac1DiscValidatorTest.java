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
 * Focused checks for the validation of candidate Ratchet &amp; Clank disc images.
 *
 * <p>Every image used here is synthetic. No game disc image is needed for these checks and none
 * is part of the repository.
 */
class Rac1DiscValidatorTest {

    /** The product code of the supported European PAL release. */
    private static final String PAL_PRODUCT_CODE = "SCES_509.16";

    /** The product code of the supported North American NTSC-U release. */
    private static final String NTSC_U_PRODUCT_CODE = "SCUS_971.99";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A PAL v2.00 disc is accepted")
    void palRevisionIsAccepted() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(4096));
        Path image = write("pal.iso", disc.image());

        IsoValidationResult result = disc.validator().validate(image);

        assertEquals(IsoValidationStatus.SUPPORTED, result.status(), result.message());
        assertEquals(Rac1DiscRevision.PAL_V2_00, result.revision());
        assertEquals(PAL_PRODUCT_CODE, result.productCode());
        assertTrue(result.isSupported());
    }

    @Test
    @DisplayName("An NTSC-U v1.00 disc is accepted")
    void ntscRevisionIsAccepted() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.NTSC_U_V1_00, NTSC_U_PRODUCT_CODE,
                SyntheticIso.elf(4096));
        Path image = write("ntsc.iso", disc.image());

        IsoValidationResult result = disc.validator().validate(image);

        assertEquals(IsoValidationStatus.SUPPORTED, result.status(), result.message());
        assertEquals(Rac1DiscRevision.NTSC_U_V1_00, result.revision());
    }

    @Test
    @DisplayName("A disc of an unsupported revision of the game is refused")
    void unsupportedRevisionIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        Path image = write("pal-v1.iso", SyntheticIso.create()
                .volumeIdentifier("RATCHETANDCLANK")
                .file("SYSTEM.CNF", SyntheticIso.systemCnf("SCES_509.13", "1.00", "PAL"))
                .file("SCES_509.13", SyntheticIso.elf(4096))
                .build());

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.UNSUPPORTED_REVISION, result.status(), result.message());
        assertFalse(result.isSupported());
        assertTrue(result.message().contains("SCES_509.13"), result.message());
        assertTrue(result.message().contains("PAL v2.00"), result.message());
    }

    @Test
    @DisplayName("The Japanese release of the game is refused")
    void japaneseRevisionIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        Path image = write("japanese.iso", SyntheticIso.create()
                .volumeIdentifier("RATCHETANDCLANK")
                .file("SYSTEM.CNF", SyntheticIso.systemCnf("SCPS_150.37", "1.00", "NTSC"))
                .file("SCPS_150.37", SyntheticIso.elf(4096))
                .build());

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.UNSUPPORTED_REVISION, result.status(), result.message());
    }

    @Test
    @DisplayName("A disc of another game is refused")
    void anotherGameIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        Path image = write("other.iso", SyntheticIso.create()
                .volumeIdentifier("ANOTHERGAME")
                .file("SYSTEM.CNF", SyntheticIso.systemCnf("SLUS_205.85", "1.00", "NTSC"))
                .file("SLUS_205.85", SyntheticIso.elf(4096))
                .build());

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.UNSUPPORTED_IMAGE, result.status(), result.message());
    }

    @Test
    @DisplayName("An ISO 9660 disc without a SYSTEM.CNF file is refused")
    void discWithoutSystemCnfIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        Path image = write("no-cnf.iso", SyntheticIso.create()
                .volumeIdentifier("ANOTHERGAME")
                .file("README.TXT", "not a game disc")
                .build());

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.UNSUPPORTED_IMAGE, result.status(), result.message());
        assertTrue(result.message().contains("SYSTEM.CNF"), result.message());
    }

    @Test
    @DisplayName("A file that is not an ISO 9660 image is refused")
    void fileThatIsNotAnIsoIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();
        byte[] content = new byte[64 * 1024];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) index;
        }

        Path image = write("random.iso", content);

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.NOT_AN_ISO, result.status(), result.message());
    }

    @Test
    @DisplayName("A file that is too small to be a disc image is refused")
    void tooSmallFileIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        Path image = write("small.iso", new byte[1024]);

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.NOT_AN_ISO, result.status(), result.message());
    }

    @Test
    @DisplayName("An ISO 9660 image with another logical block size is refused")
    void otherBlockSizeIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        Path image = write("blocksize.iso", SyntheticIso.create()
                .logicalBlockSize(1024)
                .file("SYSTEM.CNF", SyntheticIso.systemCnf(PAL_PRODUCT_CODE, "2.00", "PAL"))
                .build());

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.NOT_AN_ISO, result.status(), result.message());
    }

    @Test
    @DisplayName("A CHD container is refused with a clear message")
    void chdContainerIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        Path image = write("disc.chd", new byte[64 * 1024]);

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.UNSUPPORTED_CONTAINER, result.status(), result.message());
        assertTrue(result.message().contains("CHD"), result.message());
    }

    @Test
    @DisplayName("A cue sheet is refused with a clear message")
    void cueSheetIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        Path image = write("disc.cue", new byte[64 * 1024]);

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.UNSUPPORTED_CONTAINER, result.status(), result.message());
    }

    @Test
    @DisplayName("A file that does not exist is refused")
    void missingFileIsRefused() {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        IsoValidationResult result = validator.validate(tempDir.resolve("missing.iso"));

        assertEquals(IsoValidationStatus.INVALID_PATH, result.status(), result.message());
    }

    @Test
    @DisplayName("A directory is refused")
    void directoryIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();
        Path directory = Files.createDirectory(tempDir.resolve("folder"));

        IsoValidationResult result = validator.validate(directory);

        assertEquals(IsoValidationStatus.INVALID_PATH, result.status(), result.message());
    }

    @Test
    @DisplayName("A path that walks out of its own directory is refused")
    void pathWithParentSegmentIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();
        Files.createDirectory(tempDir.resolve("folder"));
        Path traversal = tempDir.resolve("folder").resolve("..").resolve("disc.iso");

        IsoValidationResult result = validator.validate(traversal);

        assertEquals(IsoValidationStatus.INVALID_PATH, result.status(), result.message());
        assertTrue(result.message().contains(".."), result.message());
    }

    @Test
    @DisplayName("A missing path is refused")
    void nullPathIsRefused() {
        Rac1DiscValidator validator = new Rac1DiscValidator();

        IsoValidationResult result = validator.validate(null);

        assertEquals(IsoValidationStatus.INVALID_PATH, result.status(), result.message());
    }

    @Test
    @DisplayName("A boot executable with the wrong size is refused")
    void wrongExecutableSizeIsRefused() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(4096));

        Path image = write("short.iso", SyntheticIso.create()
                .file("SYSTEM.CNF", SyntheticIso.systemCnf(PAL_PRODUCT_CODE, "2.00", "PAL"))
                .file(PAL_PRODUCT_CODE, SyntheticIso.elf(2048))
                .build());

        IsoValidationResult result = disc.validator().validate(image);

        assertEquals(IsoValidationStatus.CORRUPT_IMAGE, result.status(), result.message());
    }

    @Test
    @DisplayName("A modified boot executable is refused")
    void modifiedExecutableIsRefused() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(4096));
        byte[] modified = disc.executable().clone();
        modified[100] = (byte) (modified[100] + 1);

        Path image = write("modified.iso", SyntheticIso.create()
                .file("SYSTEM.CNF", SyntheticIso.systemCnf(PAL_PRODUCT_CODE, "2.00", "PAL"))
                .file(PAL_PRODUCT_CODE, modified)
                .build());

        IsoValidationResult result = disc.validator().validate(image);

        assertEquals(IsoValidationStatus.CORRUPT_IMAGE, result.status(), result.message());
        assertTrue(result.message().contains("digest"), result.message());
    }

    @Test
    @DisplayName("A disc that was changed outside of the boot executable is refused")
    void changeOutsideOfTheExecutableIsRefused() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(4096));
        // The change is in the SYSTEM.CNF file, so the boot executable stays exactly the same.
        byte[] changed = disc.image().clone();
        int position = indexOf(changed, "VER = 2.00".getBytes(StandardCharsets.ISO_8859_1));
        assertTrue(position > 0, "The synthetic image has to hold a VER line");
        changed[position + "VER = ".length()] = '3';

        Path image = write("changed.iso", changed);

        IsoValidationResult result = disc.validator().validate(image);

        assertEquals(IsoValidationStatus.CORRUPT_IMAGE, result.status(), result.message());
        assertTrue(result.message().contains("whole"), result.message());
    }

    @Test
    @DisplayName("An image whose size is not the size of a supported disc image is refused")
    void wrongImageSizeIsRefused() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(4096));
        // One byte more than the profile allows.
        byte[] changed = java.util.Arrays.copyOf(disc.image(), disc.image().length + 1);

        Path image = write("wrong-size.iso", changed);

        IsoValidationResult result = disc.validator().validate(image);

        assertEquals(IsoValidationStatus.CORRUPT_IMAGE, result.status(), result.message());
        assertTrue(result.message().contains("bytes"), result.message());
    }

    @Test
    @DisplayName("A disc without the boot executable of its revision is refused")
    void missingBootExecutableIsRefused() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(4096));

        Path image = write("missing-exe.iso", SyntheticIso.create()
                .file("SYSTEM.CNF", SyntheticIso.systemCnf(PAL_PRODUCT_CODE, "2.00", "PAL"))
                .build());

        IsoValidationResult result = disc.validator().validate(image);

        assertEquals(IsoValidationStatus.CORRUPT_IMAGE, result.status(), result.message());
    }

    @Test
    @DisplayName("A disc with a malformed directory record is refused")
    void malformedDirectoryRecordIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();
        byte[] broken = new byte[64];
        broken[0] = 5;

        Path image = write("broken-record.iso", SyntheticIso.create()
                .rawRootDirectory(broken)
                .build());

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.CORRUPT_IMAGE, result.status(), result.message());
    }

    @Test
    @DisplayName("A disc with a directory record whose name could escape a directory is refused")
    void directoryRecordWithUnusableNameIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();
        byte[] record = SyntheticIso.record(SyntheticIso.ROOT_DIRECTORY_SECTOR, 64, 0x00,
                "../escape".getBytes(StandardCharsets.ISO_8859_1));

        Path image = write("escape.iso", SyntheticIso.create()
                .rawRootDirectory(record)
                .build());

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.CORRUPT_IMAGE, result.status(), result.message());
    }

    @Test
    @DisplayName("A disc whose root directory points outside of the image is refused")
    void directoryOutsideOfTheImageIsRefused() throws IOException {
        Rac1DiscValidator validator = new Rac1DiscValidator();
        byte[] record = SyntheticIso.record(1_000_000, 2048, 0x02, new byte[] {0x00});

        Path image = write("outside.iso", SyntheticIso.create()
                .rawRootRecord(record)
                .build());

        IsoValidationResult result = validator.validate(image);

        assertEquals(IsoValidationStatus.CORRUPT_IMAGE, result.status(), result.message());
    }

    @Test
    @DisplayName("A disc whose boot executable is not named in SYSTEM.CNF is identified by its content")
    void bootExecutableIsFoundByItsContent() throws IOException {
        SyntheticDisc disc = SyntheticDisc.createWithoutBootLine(Rac1DiscRevision.PAL_V2_00,
                PAL_PRODUCT_CODE, SyntheticIso.elf(4096));
        Path image = write("elf.iso", disc.image());

        IsoValidationResult result = disc.validator().validate(image);

        assertEquals(IsoValidationStatus.SUPPORTED, result.status(), result.message());
        assertEquals(Rac1DiscRevision.PAL_V2_00, result.revision());
    }

    @Test
    @DisplayName("A supported disc is opened and its files can be read")
    void supportedDiscIsOpened() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(4096));
        Path image = write("open.iso", disc.image());

        try (Rac1IsoSelectionResult outcome = disc.validator().inspect(image)) {
            assertTrue(outcome.isSupported(), outcome.result().message());
            Rac1DiscImage opened = outcome.image();
            assertEquals(Rac1DiscRevision.PAL_V2_00, opened.revision());
            assertEquals(image.toAbsolutePath().normalize(), opened.sourcePath());
            assertTrue(opened.listFilePaths().contains("SYSTEM.CNF"), opened.listFilePaths().toString());

            byte[] configuration = opened.readFile("SYSTEM.CNF", 4096);
            assertTrue(new String(configuration, StandardCharsets.ISO_8859_1)
                    .contains(PAL_PRODUCT_CODE));

            try (var stream = opened.openFile(PAL_PRODUCT_CODE)) {
                assertEquals(disc.executable().length, stream.readAllBytes().length);
            }
        }
    }

    @Test
    @DisplayName("The verification reports how far it has come")
    void verificationReportsProgress() throws IOException {
        SyntheticDisc disc = SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00, PAL_PRODUCT_CODE,
                SyntheticIso.elf(4096));
        Path image = write("progress.iso", disc.image());
        long[] reported = new long[1];

        disc.validator().inspect(image, (bytesRead, totalBytes) -> reported[0] = bytesRead)
                .close();

        assertEquals(disc.image().length, reported[0],
                "The verification has to read the whole image");
    }

    private Path write(String name, byte[] content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int index = 0; index + needle.length <= haystack.length; index++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[index + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }
}
