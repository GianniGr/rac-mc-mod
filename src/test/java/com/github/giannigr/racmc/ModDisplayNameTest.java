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
 * Focused checks for the name of the project and of the mod.
 *
 * <p>The project owner approved one canonical name for the project and for the mod:
 * {@code Ratchet & Clank: Getting Wood}. It has to appear unchanged in the mod metadata, in the
 * Java constants, in the README and in the development guide, while the mod id and the Java package
 * stay the same.
 */
class ModDisplayNameTest {

    /** The canonical project and mod name the owner approved. */
    private static final String CANONICAL_NAME = "Ratchet & Clank: Getting Wood";

    /** The mod id that must not change. */
    private static final String CANONICAL_MOD_ID = "racmc";

    /** The Java package that must not change. */
    private static final String CANONICAL_PACKAGE = "com.github.giannigr.racmc";

    /** Matches a simple {@code key="value"} entry of the generated mod metadata. */
    private static final Pattern METADATA_ENTRY = Pattern.compile("(?m)^(\\w+)\\s*=\\s*\"([^\"]*)\"");

    private static Properties gradleProperties;
    private static String generatedMetadata;
    private static String readme;
    private static String developmentGuide;

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

        readme = Files.readString(projectDir.resolve("README.md"), StandardCharsets.UTF_8);
        developmentGuide = Files.readString(projectDir.resolve("docs").resolve("development.md"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("The mod name property holds the canonical name")
    void modNamePropertyHoldsTheCanonicalName() {
        assertEquals(CANONICAL_NAME, gradleProperties.getProperty("mod_name"),
                "mod_name in gradle.properties must hold the canonical project and mod name");
    }

    @Test
    @DisplayName("The Java constant holds the canonical name")
    void constantHoldsTheCanonicalName() {
        assertEquals(CANONICAL_NAME, RacMcConstants.MOD_NAME,
                "RacMcConstants.MOD_NAME must hold the canonical project and mod name");
    }

    @Test
    @DisplayName("The generated mod metadata shows the canonical name")
    void generatedMetadataShowsTheCanonicalName() {
        assertEquals(CANONICAL_NAME, metadataEntry(generatedMetadata, "displayName"),
                "The displayName of the generated metadata must hold the canonical name");
    }

    @Test
    @DisplayName("The README names the project with the canonical name")
    void readmeNamesTheProject() {
        assertTrue(readme.startsWith("# " + CANONICAL_NAME),
                "The README must start with the canonical name as its title");
        assertTrue(readme.contains(CANONICAL_NAME),
                "The README must use the canonical name");
    }

    @Test
    @DisplayName("The development guide names the project with the canonical name")
    void developmentGuideNamesTheProject() {
        assertTrue(developmentGuide.contains(CANONICAL_NAME),
                "The development guide must use the canonical name");
    }

    @Test
    @DisplayName("The mod id and the Java package stay the same")
    void identityStaysTheSame() {
        assertEquals(CANONICAL_MOD_ID, gradleProperties.getProperty("mod_id"),
                "The mod id must not change");
        assertEquals(CANONICAL_MOD_ID, RacMcConstants.MOD_ID,
                "The mod id constant must not change");
        assertEquals(CANONICAL_PACKAGE, gradleProperties.getProperty("mod_group_id"),
                "The group id must not change");
        assertEquals(CANONICAL_PACKAGE, RacMcMod.class.getPackageName(),
                "The package of the mod must not change");
        assertEquals(CANONICAL_MOD_ID, metadataEntry(generatedMetadata, "modId"),
                "The mod id of the generated metadata must not change");
    }

    @Test
    @DisplayName("The project keeps its unofficial fan project disclaimer")
    void disclaimerIsKept() {
        assertTrue(readme.contains("unofficial fan project"),
                "The README must keep the statement that this is an unofficial fan project");
        assertTrue(readme.contains("not affiliated with or endorsed by"),
                "The README must keep the statement that the project is not endorsed");
    }

    @Test
    @DisplayName("The old display name of the foundation is gone")
    void oldNameIsGone() {
        assertFalse(readme.contains("Ratchet & Clank Mod"),
                "The README must not use the display name of the foundation any more");
        assertFalse(developmentGuide.contains("Ratchet & Clank Mod"),
                "The development guide must not use the display name of the foundation any more");
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
