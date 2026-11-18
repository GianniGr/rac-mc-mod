package com.github.giannigr.racmc.asset.clank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Own namespace only. Unique staging files are retained on failure, never read as a cache. */
public final class Rac1ClankSidecarStore {
    private final Path directory;
    public Rac1ClankSidecarStore(Path effectiveUsableCacheDirectory) {
        directory = effectiveUsableCacheDirectory.toAbsolutePath().normalize();
    }

    public Path path(String discKey) {
        Rac1ClankAssets.requireDiscKey(discKey);
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(discKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return directory.resolve("clank-" + digest + ".clnk");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public Rac1ClankAssets read(String discKey) throws IOException {
        return readFile(path(discKey), discKey);
    }

    private static Rac1ClankAssets readFile(Path file, String discKey) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Clank sidecar is not a regular file");
        // Bound both the stat and the actual read, even if another process grows the file.
        if (Files.size(file) > Rac1ClankSidecarCodec.MAX_BYTES)
            throw new IOException("Oversized Clank sidecar");
        try (var stream = Files.newInputStream(file)) {
            byte[] bytes = stream.readNBytes(Rac1ClankSidecarCodec.MAX_BYTES + 1);
            return Rac1ClankSidecarCodec.decode(bytes, discKey);
        }
    }

    public void write(Rac1ClankAssets assets) throws IOException {
        byte[] bytes = Rac1ClankSidecarCodec.encode(assets);
        Files.createDirectories(directory);
        Path target = path(assets.discKey());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Clank cache target is not a regular file");
        Path staging = Files.createTempFile(directory, target.getFileName() + "-", ".staging");
        try (var channel = java.nio.channels.FileChannel.open(staging,
                java.nio.file.StandardOpenOption.WRITE)) {
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        publish(staging, assets.discKey());
    }

    /** Own-namespace publish boundary, also exercised independently of successful writes. */
    void publish(Path staging, String discKey) throws IOException {
        Path target = path(discKey);
        Path normalized = staging.toAbsolutePath().normalize();
        if (!directory.equals(normalized.getParent())
                || !normalized.getFileName().toString().startsWith(target.getFileName() + "-")
                || !normalized.getFileName().toString().endsWith(".staging"))
            throw new IOException("Not an owned Clank staging path");
        readFile(normalized, discKey);
        // No non-atomic fallback: a filesystem lacking atomic replacement fails closed.
        Files.move(normalized, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
