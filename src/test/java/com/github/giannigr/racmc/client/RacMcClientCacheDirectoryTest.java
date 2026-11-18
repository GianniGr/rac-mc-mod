package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.ModSource;
import com.github.giannigr.racmc.cache.Rac1CacheLocation;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RacMcClientCacheDirectoryTest {
    @TempDir Path temporary;

    @Test
    void getterRunsInJdkIsolationAndNeverExposesRejectedConfiguration() throws Exception {
        String source = ModSource.of("com/github/giannigr/racmc/client/RacMcClient.java");
        assertTrue(source.contains("@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)"));
        String method = ModSource.methodOf(source, "public static Optional<Path> assetCacheDirectory(");
        // Compile the actual getter with only its immutable location dependency. Never initialize
        // RacMcClient: that would initialize Minecraft configuration/media and alter AWT state.
        String harness = """
                import java.nio.file.Path;
                import java.util.Optional;
                public class CacheDirectoryProbe {
                    record Location(Path directory, String failure) {
                        boolean usable() { return failure == null; }
                    }
                    private static Location CACHE_LOCATION;
                    public static Object probe(Path path, String failure) {
                        CACHE_LOCATION = new Location(path, failure);
                        return assetCacheDirectory();
                    }
                """ + method + "\n    }\n}";
        Path file = temporary.resolve("CacheDirectoryProbe.java");
        Files.writeString(file, harness);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", temporary.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var probe = loader.loadClass("CacheDirectoryProbe").getMethod("probe", Path.class, String.class);
            Path configured = temporary.resolve("effective");
            assertEquals(Optional.of(configured), probe.invoke(null, configured, null));
            assertEquals(Optional.empty(), probe.invoke(null, null, "invalid path"));
            assertEquals(Optional.empty(), probe.invoke(null, temporary.resolve("rejected"), "unwritable"));
            Path blocked = temporary.resolve("regular-file");
            Files.writeString(blocked, "not a cache directory");
            Path config = temporary.resolve("racmc-cache.properties");
            Files.writeString(config, "directory=" + blocked);
            var invalid = Rac1CacheLocation.resolve(config, temporary);
            assertFalse(invalid.usable());
            assertEquals(Optional.empty(), probe.invoke(null, invalid.directory(), invalid.failure()));
            assertEquals("not a cache directory", Files.readString(blocked));
        }
    }
}
