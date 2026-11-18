package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscOriginalActionsTest {
    @Test void originalProviderClockRigAndActionsRunWithoutMinecraftJars() throws Exception {
        Path root = Files.createDirectories(Path.of(System.getProperty("racmc.wrenchDataTestDirectory"))
                .resolve("actions-" + UUID.randomUUID()));
        Path build = Path.of(System.getProperty("racmc.buildDir"));
        String classpath = build.resolve("classes/java/main") + File.pathSeparator
                + build.resolve("classes/java/test") + File.pathSeparator
                + System.getProperty("racmc.jomlJar");
        Path output = root.resolve("probe-results.txt");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-Djava.awt.headless=true", "-cp", classpath,
                HeadlessOriginalActionsProbe.class.getName(), System.getProperty("racmc.isoImage"),
                System.getProperty("racmc.wrenchActionCacheDirectory"), root.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Owned headless probe stopped");
            fail("Bounded headless action probe timed out: " + output);
        }
        assertEquals(0, process.exitValue(), () -> {
            try { return Files.readString(output); } catch (Exception e) { return e.toString(); }
        });
        assertTrue(Files.readString(output).contains("PASS real local data"));
    }

}
