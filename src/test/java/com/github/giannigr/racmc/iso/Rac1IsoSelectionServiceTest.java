package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused checks for the selection service and for the catalogue of the supported revisions.
 */
class Rac1IsoSelectionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A supported disc is kept by the selection service")
    void supportedDiscIsKept() throws IOException {
        Rac1IsoSelectionService service = newService();
        try {
            Path image = writeSupportedImage("supported.iso");

            IsoValidationResult result = service.select(image);

            assertEquals(IsoValidationStatus.SUPPORTED, result.status(), result.message());
            assertTrue(service.selectedImage().isPresent());
            assertEquals(Rac1DiscRevision.PAL_V2_00, service.selectedRevision().orElseThrow());
            assertEquals(image.toAbsolutePath().normalize(), service.selectedPath().orElseThrow());
        } finally {
            service.close();
        }
    }

    @Test
    @DisplayName("A refused disc clears the previous selection")
    void refusedDiscClearsTheSelection() throws IOException {
        Rac1IsoSelectionService service = newService();
        try {
            service.select(writeSupportedImage("supported.iso"));
            assertTrue(service.selectedImage().isPresent());

            IsoValidationResult result = service.select(tempDir.resolve("missing.iso"));

            assertEquals(IsoValidationStatus.INVALID_PATH, result.status(), result.message());
            assertFalse(service.selectedImage().isPresent());
            assertTrue(service.selectedRevision().isEmpty());
        } finally {
            service.close();
        }
    }

    @Test
    @DisplayName("The catalogue holds exactly the two supported retail revisions")
    void catalogueHoldsTheSupportedRevisions() {
        Rac1DiscCatalog catalog = Rac1DiscCatalog.supported();

        assertEquals(2, catalog.profiles().size());
        assertEquals(Rac1DiscRevision.PAL_V2_00, catalog.byProductCode("SCES_509.16").orElseThrow().revision());
        assertEquals(Rac1DiscRevision.NTSC_U_V1_00, catalog.byProductCode("SCUS_971.99").orElseThrow().revision());
        assertEquals(Rac1DiscRevision.NTSC_U_V1_00, catalog.byProductCode("scus_971.99").orElseThrow().revision());
    }

    @Test
    @DisplayName("The catalogue refuses the product codes of other revisions")
    void catalogueRefusesOtherRevisions() {
        Rac1DiscCatalog catalog = Rac1DiscCatalog.supported();

        assertTrue(catalog.byProductCode("SCES_509.13").isEmpty(), "the PAL v1.00 release");
        assertTrue(catalog.byProductCode("SCPS_150.37").isEmpty(), "the Japanese release");
        assertTrue(catalog.byProductCode("SCUS_972.09").isEmpty(), "the North American demo");
        assertTrue(catalog.byProductCode("SCED_510.75").isEmpty(), "the European demo");
        assertTrue(catalog.byProductCode(null).isEmpty());
    }

    @Test
    @DisplayName("The catalogue names both supported revisions in its messages")
    void catalogueNamesBothRevisions() {
        String labels = Rac1DiscCatalog.supported().supportedLabels();

        assertTrue(labels.contains("PAL v2.00"), labels);
        assertTrue(labels.contains("NTSC-U v1.00"), labels);
    }

    @Test
    @DisplayName("The boot executable digests of the catalogue are the published ones")
    void catalogueUsesThePublishedDigests() {
        DiscProfile pal = Rac1DiscCatalog.supported().byProductCode("SCES_509.16").orElseThrow();
        DiscProfile ntsc = Rac1DiscCatalog.supported().byProductCode("SCUS_971.99").orElseThrow();

        assertEquals(1388100L, pal.executableSize());
        assertEquals("17f8a846329fd10798cb97847e9610e5eecd1867668420ed627b2292a96b122b",
                pal.executableSha256());
        assertEquals(1383028L, ntsc.executableSize());
        assertEquals("e050581032e4bb3f20341307da5b69b76f1574910519155380ea771e55c3c0c9",
                ntsc.executableSha256());
    }

    private Rac1IsoSelectionService newService() {
        byte[] executable = SyntheticIso.elf(1024);
        DiscProfile profile = new DiscProfile(Rac1DiscRevision.PAL_V2_00, "SCES_509.16", "SCES_509.16",
                executable.length, sha256(executable));
        return new Rac1IsoSelectionService(
                new Rac1DiscValidator(Rac1DiscCatalog.of(List.of(profile))));
    }

    private Path writeSupportedImage(String name) throws IOException {
        byte[] executable = SyntheticIso.elf(1024);
        Path image = tempDir.resolve(name);
        Files.write(image, SyntheticIso.create()
                .file("SYSTEM.CNF", SyntheticIso.systemCnf("SCES_509.16", "2.00", "PAL"))
                .file("SCES_509.16", executable)
                .build());
        return image;
    }

    private static String sha256(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is not available", exception);
        }
    }

    @Test
    @DisplayName("A profile with impossible values is refused")
    void impossibleProfileIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DiscProfile(Rac1DiscRevision.PAL_V2_00, "SCES_509.16", "SCES_509.16",
                        0L, "17f8a846329fd10798cb97847e9610e5eecd1867668420ed627b2292a96b122b"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DiscProfile(Rac1DiscRevision.PAL_V2_00, "SCES_509.16", "SCES_509.16",
                        10L, "not a digest"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new DiscProfile(null, "SCES_509.16", "SCES_509.16", 10L,
                        "17f8a846329fd10798cb97847e9610e5eecd1867668420ed627b2292a96b122b"));
    }

    @Test
    @DisplayName("The catalogue of the tests can hold synthetic profiles")
    void syntheticCatalogueIsSupported() {
        DiscProfile profile = new DiscProfile(Rac1DiscRevision.PAL_V2_00, "SCES_509.16", "SCES_509.16",
                10L, "17f8a846329fd10798cb97847e9610e5eecd1867668420ed627b2292a96b122b");
        Rac1DiscCatalog catalog = Rac1DiscCatalog.of(List.of(profile));

        assertEquals(1, catalog.profiles().size());
        assertTrue(catalog.byProductCode("SCES_509.16").isPresent());
    }
}
