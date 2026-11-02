package com.github.giannigr.racmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the project bootstrap.
 *
 * <p>These tests verify that the mod identity is wired consistently across the three places it is
 * declared - {@code gradle.properties}, the generated {@code META-INF/neoforge.mods.toml} and the
 * Java constants - and that the generated metadata contains everything NeoForge requires to load
 * the mod. They deliberately avoid Minecraft classes so that they run on a plain JVM.
 */
class ModBootstrapMetadataTest {

    /** The mod id rules enforced by NeoForge (net.neoforged.fml.loading.moddiscovery.ModInfo). */
    private static final Pattern MOD_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    /** The mod version rules enforced by NeoForge. */
    private static final Pattern MOD_VERSION_PATTERN = Pattern.compile("^\\d+.*");

    private static Properties gradleProperties;
    private static Map<String, String> rootMetadata;
    private static Map<String, String> modMetadata;
    private static List<Map<String, String>> dependencies;

    @BeforeAll
    static void loadProjectFiles() throws IOException {
        Path projectDir = Path.of(System.getProperty("racmc.projectDir"));
        Path buildDir = Path.of(System.getProperty("racmc.buildDir"));

        gradleProperties = new Properties();
        try (var reader = Files.newBufferedReader(projectDir.resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            gradleProperties.load(reader);
        }

        Path metadataFile = buildDir.resolve("generated").resolve("sources").resolve("modMetadata")
                .resolve("META-INF").resolve("neoforge.mods.toml");
        assertTrue(Files.exists(metadataFile),
                "Generated mod metadata not found at " + metadataFile
                        + ". Run the 'generateModMetadata' Gradle task.");

        List<Section> sections = parseTomlSections(Files.readString(metadataFile, StandardCharsets.UTF_8));
        rootMetadata = firstMatching(sections, "");
        modMetadata = firstMatching(sections, "mods");
        dependencies = new ArrayList<>();
        for (Section section : sections) {
            if (section.name().startsWith("dependencies.")) {
                dependencies.add(section.entries());
            }
        }
    }

    @Test
    @DisplayName("The mod id is a valid NeoForge mod id")
    void modIdIsValid() {
        String modId = RacMcConstants.MOD_ID;
        assertTrue(MOD_ID_PATTERN.matcher(modId).matches(),
                "Mod id '" + modId + "' must match " + MOD_ID_PATTERN.pattern());
    }

    @Test
    @DisplayName("The mod id is identical in gradle.properties, the generated metadata and the Java constants")
    void modIdIsConsistentAcrossSources() {
        String fromProperties = gradleProperties.getProperty("mod_id");
        assertNotNull(fromProperties, "mod_id is missing from gradle.properties");
        assertEquals(fromProperties, RacMcConstants.MOD_ID,
                "mod_id in gradle.properties must match RacMcConstants.MOD_ID");
        assertEquals(fromProperties, modMetadata.get("modId"),
                "mod_id in gradle.properties must match modId in the generated neoforge.mods.toml");
    }

    @Test
    @DisplayName("The mod name and version are identical in gradle.properties, the generated metadata and the Java constants")
    void modNameAndVersionAreConsistent() {
        assertEquals(gradleProperties.getProperty("mod_name"), RacMcConstants.MOD_NAME,
                "mod_name in gradle.properties must match RacMcConstants.MOD_NAME");
        assertEquals(gradleProperties.getProperty("mod_name"), modMetadata.get("displayName"),
                "mod_name in gradle.properties must match displayName in the generated neoforge.mods.toml");
        assertEquals(gradleProperties.getProperty("mod_version"), modMetadata.get("version"),
                "mod_version in gradle.properties must match version in the generated neoforge.mods.toml");
        assertTrue(MOD_VERSION_PATTERN.matcher(modMetadata.getOrDefault("version", "")).matches(),
                "The mod version must start with a digit");
    }

    @Test
    @DisplayName("The generated metadata declares the mandatory loader fields")
    void mandatoryLoaderFieldsArePresent() {
        assertEquals("javafml", rootMetadata.get("modLoader"),
                "The mod loader must be javafml");
        assertNotNull(rootMetadata.get("loaderVersion"), "loaderVersion is mandatory");
        assertFalse(rootMetadata.get("loaderVersion").isBlank(), "loaderVersion must not be blank");
        assertNotNull(rootMetadata.get("license"),
                "The license field is mandatory in neoforge.mods.toml");
        assertFalse(rootMetadata.get("license").isBlank(), "The license field must not be blank");
        assertNotNull(modMetadata.get("description"), "The description is mandatory");
    }

    @Test
    @DisplayName("The license field mirrors gradle.properties")
    void licenseFieldMirrorsGradleProperties() {
        assertEquals(gradleProperties.getProperty("mod_license"), rootMetadata.get("license"),
                "The license field must be generated from the mod_license property");
    }

    @Test
    @DisplayName("The generated metadata requires NeoForge and Minecraft")
    void requiredDependenciesAreDeclared() {
        Map<String, Map<String, String>> byModId = new LinkedHashMap<>();
        for (Map<String, String> dependency : dependencies) {
            byModId.put(dependency.get("modId"), dependency);
        }

        Map<String, String> neoforge = byModId.get("neoforge");
        assertNotNull(neoforge, "A dependency on neoforge must be declared");
        assertEquals("required", neoforge.get("type"));
        assertEquals("[" + gradleProperties.getProperty("neo_version") + ",)", neoforge.get("versionRange"),
                "The NeoForge dependency range must start at the NeoForge version in use");

        Map<String, String> minecraft = byModId.get("minecraft");
        assertNotNull(minecraft, "A dependency on minecraft must be declared");
        assertEquals("required", minecraft.get("type"));
        assertEquals(gradleProperties.getProperty("minecraft_version_range"), minecraft.get("versionRange"),
                "The Minecraft dependency range must mirror minecraft_version_range");
    }

    @Test
    @DisplayName("The pinned Minecraft and NeoForge versions agree with each other")
    void minecraftAndNeoForgeVersionsAgree() {
        String minecraftVersion = gradleProperties.getProperty("minecraft_version");
        String neoVersion = gradleProperties.getProperty("neo_version");
        assertEquals("1.21.1", minecraftVersion, "The project targets Minecraft Java Edition 1.21.1");
        assertTrue(neoVersion.startsWith("21.1."),
                "NeoForge " + neoVersion + " is not part of the 21.1.x line that ships Minecraft 1.21.1");
    }

    /** A single {@code [[table]]} of the generated metadata. */
    private record Section(String name, Map<String, String> entries) {
    }

    private static Map<String, String> firstMatching(List<Section> sections, String name) {
        return sections.stream()
                .filter(section -> section.name().equals(name))
                .findFirst()
                .map(Section::entries)
                .orElseGet(Map::of);
    }

    /**
     * Minimal TOML reader for the subset used by the generated mod metadata: tables declared with
     * {@code [[name]]} and simple {@code key=value} entries. Repeated tables are kept as separate
     * sections.
     */
    private static List<Section> parseTomlSections(String content) {
        List<Section> sections = new ArrayList<>();
        Map<String, String> currentEntries = new LinkedHashMap<>();
        String currentName = "";
        sections.add(new Section("", currentEntries));

        boolean inMultilineString = false;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (inMultilineString) {
                if (line.endsWith("'''")) {
                    inMultilineString = false;
                }
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[[")) {
                int end = line.indexOf("]]");
                if (end < 0) {
                    continue;
                }
                currentName = line.substring(2, end).trim();
                currentEntries = new LinkedHashMap<>();
                sections.add(new Section(currentName, currentEntries));
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (value.startsWith("'''")) {
                inMultilineString = !value.endsWith("'''") || value.length() < 6;
                currentEntries.put(key, value.length() > 3 ? value.substring(3) : "");
                continue;
            }
            int comment = value.indexOf(" #");
            if (comment >= 0) {
                value = value.substring(0, comment).trim();
            }
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            currentEntries.put(key, value);
        }
        return sections;
    }
}
