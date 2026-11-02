package com.github.giannigr.racmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the description of the mod in the generated mod metadata.
 *
 * <p>NeoForge documents {@code description} as a mandatory field of {@code META-INF/neoforge.mods.toml}.
 * The existing metadata test only asserts that the parsed description is not {@code null}, which is
 * vacuous: the minimal TOML reader used there always yields an empty string for a multi line
 * {@code '''} value. A metadata file with an empty description therefore passes every existing
 * check. These tests read the raw metadata text instead, so an emptied or truncated description is
 * detected.
 */
class ModDescriptionTest {

    /** Matches a multi line TOML string value, capturing its content. */
    private static final Pattern DESCRIPTION = Pattern.compile(
            "(?m)^description\\s*=\\s*'''(.*?)'''", Pattern.DOTALL);

    private static String generatedDescription;
    private static String templateDescription;

    @BeforeAll
    static void readDescriptions() throws IOException {
        Path projectDir = Path.of(System.getProperty("racmc.projectDir"));
        Path buildDir = Path.of(System.getProperty("racmc.buildDir"));

        Path generated = buildDir.resolve("generated").resolve("sources").resolve("modMetadata")
                .resolve("META-INF").resolve("neoforge.mods.toml");
        assertTrue(Files.exists(generated),
                "Generated mod metadata not found at " + generated
                        + ". Run the 'generateModMetadata' Gradle task.");

        generatedDescription = extract(Files.readString(generated, StandardCharsets.UTF_8));
        templateDescription = extract(Files.readString(
                projectDir.resolve("src").resolve("main").resolve("templates")
                        .resolve("META-INF").resolve("neoforge.mods.toml"),
                StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("The generated metadata declares a non-blank description")
    void descriptionIsNotBlank() {
        assertFalse(generatedDescription.isBlank(),
                "The description in the generated neoforge.mods.toml must not be empty");
    }

    @Test
    @DisplayName("The generated description matches the description in the metadata template")
    void descriptionMatchesTheTemplate() {
        assertFalse(templateDescription.isBlank(),
                "The description in the metadata template must not be empty");
        assertEquals(templateDescription, generatedDescription,
                "The generated description must be the description of the metadata template");
    }

    /**
     * Extracts the content of the multi line {@code description} value of a mod metadata file.
     *
     * <p>The line separators are normalised because the metadata template and the metadata file
     * generated from it do not necessarily use the same line separator: the template is a checked
     * out working tree file, the generated file is written by the resource expansion.
     *
     * @param content the metadata file content
     * @return the description without its surrounding whitespace, or an empty string when the
     *         metadata file declares no description
     */
    private static String extract(String content) {
        Matcher matcher = DESCRIPTION.matcher(content);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace("\r\n", "\n").strip();
    }
}
