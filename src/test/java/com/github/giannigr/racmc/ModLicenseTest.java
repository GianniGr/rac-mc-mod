package com.github.giannigr.racmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the licence of the project.
 *
 * <p>The project owner selected the MIT licence for the source code of the mod. The licence has to
 * be stated in the same way everywhere: in the {@code LICENSE} file, in the {@code mod_license}
 * property that the mod metadata is generated from and in the documentation.
 */
class ModLicenseTest {

    /** The licence the project owner selected for the source code. */
    private static final String SELECTED_LICENSE = "MIT";

    /** Matches a simple {@code key="value"} entry of the generated mod metadata. */
    private static final Pattern METADATA_ENTRY = Pattern.compile("(?m)^(\\w+)\\s*=\\s*\"([^\"]*)\"");

    private static Properties gradleProperties;
    private static String generatedMetadata;
    private static String metadataTemplate;
    private static String licenseFile;
    private static String readme;

    @BeforeAll
    static void loadProjectFiles() throws IOException {
        Path projectDir = Path.of(System.getProperty("racmc.projectDir"));
        Path buildDir = Path.of(System.getProperty("racmc.buildDir"));

        gradleProperties = new Properties();
        try (var reader = Files.newBufferedReader(projectDir.resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            gradleProperties.load(reader);
        }

        Path generated = buildDir.resolve("generated").resolve("sources").resolve("modMetadata")
                .resolve("META-INF").resolve("neoforge.mods.toml");
        assertTrue(Files.exists(generated),
                "Generated mod metadata not found at " + generated
                        + ". Run the 'generateModMetadata' Gradle task.");
        generatedMetadata = Files.readString(generated, StandardCharsets.UTF_8);

        metadataTemplate = Files.readString(
                projectDir.resolve("src").resolve("main").resolve("templates")
                        .resolve("META-INF").resolve("neoforge.mods.toml"),
                StandardCharsets.UTF_8);

        licenseFile = Files.readString(projectDir.resolve("LICENSE"), StandardCharsets.UTF_8);
        readme = Files.readString(projectDir.resolve("README.md"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("The project has a LICENSE file")
    void licenseFileExists() {
        assertFalse(licenseFile.isBlank(), "The LICENSE file must not be empty");
    }

    @Test
    @DisplayName("The LICENSE file holds the MIT licence")
    void licenseFileIsTheMitLicense() {
        assertTrue(licenseFile.contains("MIT License"),
                "The LICENSE file must name the MIT licence");
        assertTrue(licenseFile.contains("Permission is hereby granted, free of charge"),
                "The LICENSE file must hold the permission grant of the MIT licence");
        assertTrue(licenseFile.contains("THE SOFTWARE IS PROVIDED \"AS IS\""),
                "The LICENSE file must hold the disclaimer of the MIT licence");
        assertTrue(licenseFile.contains("WITHOUT WARRANTY OF ANY KIND"),
                "The LICENSE file must hold the warranty disclaimer of the MIT licence");
    }

    @Test
    @DisplayName("The mod_license property holds the selected licence")
    void modLicensePropertyHoldsTheSelectedLicense() {
        assertEquals(SELECTED_LICENSE, gradleProperties.getProperty("mod_license"),
                "mod_license in gradle.properties must hold the licence the owner selected");
    }

    @Test
    @DisplayName("The generated mod metadata declares the selected licence")
    void generatedMetadataDeclaresTheSelectedLicense() {
        assertEquals(SELECTED_LICENSE, metadataEntry(generatedMetadata, "license"),
                "The license field of the generated metadata must hold the selected licence");
    }

    @Test
    @DisplayName("The metadata template takes the licence from the gradle property")
    void metadataTemplateUsesTheProperty() {
        assertTrue(metadataTemplate.contains("license=\"${mod_license}\""),
                "The license field of the metadata template must be generated from mod_license");
    }

    @Test
    @DisplayName("The licence placeholder of the foundation is gone")
    void placeholderIsGone() {
        assertFalse(gradleProperties.getProperty("mod_license").contains("All Rights Reserved"),
                "The placeholder of the foundation must not be used as a licence any more");
        assertFalse(generatedMetadata.contains("All Rights Reserved"),
                "The generated metadata must not carry the placeholder of the foundation");
        assertFalse(readme.contains("All Rights Reserved"),
                "The README must not describe the placeholder of the foundation as the licence");
    }

    @Test
    @DisplayName("The README states the MIT licence")
    void readmeStatesTheLicense() {
        assertTrue(readme.contains("MIT"), "The README must name the licence of the project");
    }

    @Test
    @DisplayName("The licence only covers the source code of the project")
    void licenseOnlyCoversTheSourceCode() {
        assertTrue(readme.contains("Original game assets are not included"),
                "The README must keep the statement that no game assets are included");
    }

    private static String metadataEntry(String metadata, String key) {
        Matcher matcher = METADATA_ENTRY.matcher(metadata);
        while (matcher.find()) {
            if (matcher.group(1).equals(key)) {
                return matcher.group(2);
            }
        }
        return null;
    }
}
