package com.github.giannigr.racmc.serverassets;

import com.github.giannigr.racmc.asset.Rac1AttachmentSampler;
import com.github.giannigr.racmc.cache.Rac1CacheLocation;
import com.github.giannigr.racmc.cache.Rac1CacheStore;
import com.github.giannigr.racmc.iso.Rac1MediaConfig;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import static com.github.giannigr.racmc.serverassets.Rac1WrenchDataStatus.State.*;
import static com.github.giannigr.racmc.serverassets.Rac1WrenchDataStatus.Origin.*;

/** Executed in a separate JDK-only JVM: no NeoForge/Minecraft/JUnit jars on its classpath. */
public final class HeadlessWrenchDataProbe {
    private HeadlessWrenchDataProbe() { }

    public static void main(String[] args) throws Exception {
        require(GraphicsEnvironment.isHeadless(), "Probe must remain headless");
        try {
            Class.forName("net.minecraft.client.Minecraft", false,
                    ClassLoader.getSystemClassLoader());
            throw new AssertionError("Minecraft must not be on this process classpath");
        } catch (ClassNotFoundException expected) {
            // The actual local importer/provider runs without any Minecraft or GUI dependency.
        }
        Path image = Path.of(args[0]);
        Path root = Path.of(args[1]);
        var originalModified = Files.getLastModifiedTime(image);
        long originalSize = Files.size(image);
        Path dedicated = Files.createDirectories(root.resolve("dedicated-config"));
        Files.writeString(dedicated.resolve(Rac1WrenchServerConfig.FILE_NAME),
                "iso=" + image + "\ncacheDirectory=server-cache\n");
        String key;
        try (var provider = Rac1WrenchDataProvider.open(dedicated, true)) {
            await(provider);
            require(provider.status().origin() == IMPORT, "First dedicated startup must import");
            var data = provider.data().orElseThrow();
            key = data.discKey();
            sample(data);
        }
        // A valid existing cache cannot bypass the operator-local ISO requirement.
        Files.writeString(dedicated.resolve(Rac1WrenchServerConfig.FILE_NAME),
                "iso=\ncacheDirectory=server-cache\n");
        try (var provider = Rac1WrenchDataProvider.open(dedicated, true)) {
            awaitTerminal(provider);
            require(provider.status().state() == MISSING && provider.data().isEmpty(),
                    "Blank ISO must not trust the existing cache alone");
        }
        Files.writeString(dedicated.resolve(Rac1WrenchServerConfig.FILE_NAME),
                "iso=" + dedicated.resolve("missing.iso") + "\ncacheDirectory=server-cache\n");
        try (var provider = Rac1WrenchDataProvider.open(dedicated, true)) {
            awaitTerminal(provider);
            require(provider.status().state() == FAILED && provider.data().isEmpty(),
                    "Missing ISO must not trust the existing cache alone");
        }
        Files.writeString(dedicated.resolve(Rac1WrenchServerConfig.FILE_NAME),
                "iso=" + image + "\ncacheDirectory=server-cache\n");
        try (var provider = Rac1WrenchDataProvider.open(dedicated, true)) {
            await(provider);
            require(provider.status().origin() == CACHE, "Restart must validate and reuse own cache");
            sample(provider.data().orElseThrow());
        }
        Path cacheDirectory = dedicated.resolve("server-cache");
        Path cache = new Rac1CacheStore(cacheDirectory).fileFor(key);
        byte[] damaged = Files.readAllBytes(cache);
        damaged[0] ^= 0x10;
        Files.write(cache, damaged);
        try (var provider = Rac1WrenchDataProvider.open(dedicated, true)) {
            await(provider);
            require(provider.status().origin() == IMPORT, "Corrupt cache must rebuild from local ISO");
            sample(provider.data().orElseThrow());
        }
        var sharedModified = Files.getLastModifiedTime(cache);
        byte[] sharedBytes = Files.readAllBytes(cache);
        Path integrated = Files.createDirectories(root.resolve("integrated-config"));
        new Rac1MediaConfig(image, "", 0, "", 0).write(Rac1MediaConfig.fileIn(integrated));
        Files.writeString(Rac1CacheLocation.fileIn(integrated),
                "directory=" + cacheDirectory + "\n");
        try (var provider = Rac1WrenchDataProvider.open(integrated, false)) {
            await(provider);
            require(provider.status().origin() == SHARED_CACHE,
                    "Integrated server must reuse legitimate common setting/cache without client types");
            sample(provider.data().orElseThrow());
            require(!Files.exists(integrated.resolve(Rac1WrenchServerConfig.DEFAULT_CACHE)),
                    "Shared read must not create a writable server cache");
        }
        require(java.util.Arrays.equals(sharedBytes, Files.readAllBytes(cache)),
                "Shared cache bytes must not be rewritten");
        require(sharedModified.equals(Files.getLastModifiedTime(cache)),
                "Shared cache modification time must not change");
        require(originalSize == Files.size(image)
                        && originalModified.equals(Files.getLastModifiedTime(image)),
                "ISO must remain unchanged");
        require(GraphicsEnvironment.isHeadless(), "Importer must not disable headless mode");
        require(Thread.getAllStackTraces().keySet().stream().noneMatch(
                t -> t.isAlive() && t.getName().equals("racmc-server-wrench-data")),
                "All owned service workers must have ended");
        System.out.println("PASS headless JDK-only: import, validated cache restart, corrupt recovery, "
                + "no cache-only gate bypass, integrated read-only reuse, original attachment sampling, "
                + "ISO unchanged, workers joined.");
    }

    private static void await(Rac1WrenchDataProvider provider) throws Exception {
        awaitTerminal(provider);
        require(provider.status().state() == READY, "Local data unavailable: " + provider.status());
    }

    private static void awaitTerminal(Rac1WrenchDataProvider provider) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (provider.status().state() == VALIDATING || provider.status().state() == IMPORTING) {
            require(System.nanoTime() < deadline, "Timed out waiting for local data");
            Thread.sleep(10);
        }
    }

    private static void sample(Rac1WrenchData data) {
        for (int sequence : new int[] { 26, 43 }) {
            require(data.heroSequence(sequence).orElseThrow().sourceTiming().available(),
                    "Original event metadata required");
            for (float seconds : new float[] { 0, 0.2f, 0.5f }) {
                finite(Rac1AttachmentSampler.sample(data.hero(),
                        "ratchet_sequence_" + sequence, seconds, 0).orElseThrow());
            }
        }
        for (int sequence : new int[] { 1, 6, 10 }) {
            finite(Rac1AttachmentSampler.sample(data.wrench(),
                    "sequence_" + sequence, 0.2f, 1).orElseThrow());
        }
    }

    private static void finite(float[] matrix) {
        for (float value : matrix) {
            require(Float.isFinite(value), "Nonfinite original attachment pose");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
