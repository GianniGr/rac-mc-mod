package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The check of a real disc image.
 *
 * <p>This test is opt-in and is skipped unless the path of a disc image is given on the command
 * line:
 *
 * <pre>
 * .\gradlew.bat test --rerun -Dracmc.isoImage=C:/path/to/the/disc.iso
 * </pre>
 *
 * <p>An optional second property names the revision the image is expected to have:
 *
 * <pre>
 * -Dracmc.isoExpectedRevision="PAL v2.00"
 * </pre>
 *
 * <p>The image is only read. It is never copied, moved, changed or written to, and no part of it
 * ends up in the repository or in a report of the test run.
 */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscImageTest {

    /**
     * A real disc image of a supported revision is accepted.
     */
    @Test
    @DisplayName("A real disc image of a supported revision is accepted")
    void aRealDiscImageIsAccepted() {
        String image = System.getProperty("racmc.isoImage");
        String expectedRevision = System.getProperty("racmc.isoExpectedRevision");
        assertNotNull(image, "The path of the disc image must be given");

        Path path = Path.of(image);
        assertTrue(Files.isRegularFile(path), "The given path must be a regular file");

        Rac1DiscValidator validator = new Rac1DiscValidator();
        IsoValidationResult result = validator.validate(path);

        assertTrue(result.isSupported(), result.message());
        assertNotNull(result.revision(), "An accepted image must have a revision");
        if (expectedRevision != null && !expectedRevision.isBlank()) {
            assertEquals(expectedRevision, result.revision().label(),
                    "The image has a different revision than the expected one");
        }
    }
}
