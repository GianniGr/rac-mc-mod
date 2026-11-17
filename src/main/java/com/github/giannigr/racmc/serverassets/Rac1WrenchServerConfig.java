package com.github.giannigr.racmc.serverassets;

import com.github.giannigr.racmc.cache.Rac1CacheLocation;
import com.github.giannigr.racmc.iso.Rac1MediaConfig;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Operator-local configuration; no Minecraft singleton, GUI, network or global access gate. */
public record Rac1WrenchServerConfig(Path image, Path cacheDirectory, Path sharedReadCacheDirectory) {
    public static final String FILE_NAME = "racmc-server-wrench.properties";
    public static final String DEFAULT_CACHE = "server-wrench-cache";
    private static final String TEMPLATE = """
            # Optional server-local original wrench animation data. No GUI or asset networking.
            # Blank iso disables managed wrench actions, NOT unrelated server content.
            # Literal paths (Windows backslashes need no escaping), relative to config/racmc.
            # Settings are read at server startup. Changing them never moves/deletes old caches.
            iso=
            cacheDirectory=server-wrench-cache
            """;

    public static Rac1WrenchServerConfig read(Path modDirectory, boolean dedicated) throws IOException {
        Path base = modDirectory.toAbsolutePath().normalize();
        Path file = base.resolve(FILE_NAME);
        if (dedicated && !Files.exists(file)) {
            Files.createDirectories(base);
            try {
                Files.writeString(file, TEMPLATE, StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException concurrentCreation) {
                // Read the operator's concurrently created file, never overwrite it.
            }
        }
        if (Files.exists(file)) {
            Map<String, String> values = readLiteral(file);
            String iso = values.getOrDefault("iso", "");
            Path cache = resolve(base, values.getOrDefault("cacheDirectory", ""), DEFAULT_CACHE);
            Path image = iso.isBlank() ? null : resolve(base, iso, null);
            if (image != null) {
                for (Path part : Path.of(iso)) {
                    if ("..".equals(part.toString())) {
                        throw new IOException("The local ISO path must not contain '..'.");
                    }
                }
            }
            return new Rac1WrenchServerConfig(image, cache, null);
        }
        if (dedicated) {
            // Even if an operator removes/renames the file during startup, never read client settings.
            return new Rac1WrenchServerConfig(null, base.resolve(DEFAULT_CACHE), null);
        }
        // Integrated server only: read the legitimate common setting, not RacMcClient/Minecraft.
        var media = Rac1MediaConfig.read(Rac1MediaConfig.fileIn(base));
        var shared = Rac1CacheLocation.resolveForRead(Rac1CacheLocation.fileIn(base), base);
        if (!shared.usable()) {
            throw new IOException(shared.failure());
        }
        Path serverCache = base.resolve(DEFAULT_CACHE);
        Path serverTarget = intendedRealPath(serverCache);
        Path sharedTarget = intendedRealPath(shared.directory());
        if (serverTarget.startsWith(sharedTarget) || sharedTarget.startsWith(serverTarget)) {
            throw new IOException("Server and shared client caches must use separate directories.");
        }
        return new Rac1WrenchServerConfig(media == null ? null : media.path(),
                serverCache, shared.directory());
    }

    /** Resolve existing ancestors too, so a junction cannot alias the read-only shared cache. */
    private static Path intendedRealPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        return existing == null ? absolute
                : existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
    }

    private static Path resolve(Path base, String raw, String defaultValue) throws IOException {
        Path parsed = Path.of(raw.isBlank() ? defaultValue : raw);
        if (!parsed.isAbsolute() && parsed.getRoot() != null) {
            throw new IOException("Use an absolute path or a path relative to " + base);
        }
        return (parsed.isAbsolute() ? parsed : base.resolve(parsed)).normalize();
    }

    private static Map<String, String> readLiteral(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file)) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#") || text.startsWith("!")) {
                continue;
            }
            int separator = text.indexOf('=');
            if (separator >= 0) {
                values.putIfAbsent(text.substring(0, separator).strip(),
                        text.substring(separator + 1).strip());
            }
        }
        return values;
    }
}
