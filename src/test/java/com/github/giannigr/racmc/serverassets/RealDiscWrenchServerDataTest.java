package com.github.giannigr.racmc.serverassets;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Retains converted caches only under an explicitly supplied private directory, never Git. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
@EnabledIfSystemProperty(named = "racmc.wrenchDataTestDirectory", matches = ".+")
class RealDiscWrenchServerDataTest {
    @Test
    void actualLocalImportAndCacheRecoveryRunWithoutMinecraftOrGui() throws Exception {
        Path parent = Path.of(System.getProperty("racmc.wrenchDataTestDirectory"));
        assertFalse(parent.toAbsolutePath().normalize().startsWith(
                Path.of(System.getProperty("racmc.projectDir")).toAbsolutePath().normalize()),
                "Real imported cache fixtures must stay outside Git");
        Files.createDirectories(parent);
        Path root = Files.createTempDirectory(parent, "headless-");
        Path output = root.resolve("probe.log");
        Path build = Path.of(System.getProperty("racmc.buildDir"));
        String classpath = build.resolve("classes/java/main") + File.pathSeparator
                + build.resolve("classes/java/test");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx512m", "-Djava.awt.headless=true", "-cp", classpath,
                HeadlessWrenchDataProbe.class.getName(), System.getProperty("racmc.isoImage"),
                root.toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), "Headless local import timed out");
            assertEquals(0, process.exitValue(), Files.readString(output));
            assertTrue(Files.readString(output).contains("PASS headless JDK-only"));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly(); // only the positively owned child from this exact test
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Owned child did not stop");
            }
        }
    }
}
