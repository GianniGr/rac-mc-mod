package com.github.giannigr.racmc.iso;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * The configuration of the disc image the mod uses.
 *
 * <p>The configuration holds the path of the file the player chose and a few facts about it. It
 * never holds any part of the disc image itself: no bytes of the image are copied into the
 * configuration or anywhere else.
 *
 * @param path the path of the configured disc image
 * @param revision the label of the revision the image was verified as
 * @param imageSize the size of the image in bytes
 * @param imageSha1 the SHA-1 digest of the whole image
 * @param modifiedTimeMillis the time the file was changed the last time, in milliseconds since
 *        the epoch
 */
public record Rac1MediaConfig(Path path,
                              String revision,
                              long imageSize,
                              String imageSha1,
                              long modifiedTimeMillis) {

    /** The name of the file the configuration is stored in. */
    public static final String FILE_NAME = "racmc-media.properties";

    /**
     * Creates a configuration.
     *
     * @throws NullPointerException when the path or the revision is {@code null}
     */
    public Rac1MediaConfig {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(imageSha1, "imageSha1");
    }

    /**
     * The file the configuration is stored in, inside the given directory.
     *
     * @param directory the configuration directory of the mod
     * @return the file of the configuration
     */
    public static Path fileIn(Path directory) {
        return directory.resolve(FILE_NAME);
    }

    /**
     * Reads a configuration.
     *
     * @param file the file to read
     * @return the configuration, or {@code null} when the file does not exist or holds no path
     * @throws IOException when the file cannot be read
     */
    public static Rac1MediaConfig read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String path = properties.getProperty("path");
        if (path == null || path.isBlank()) {
            return null;
        }
        return new Rac1MediaConfig(
                Path.of(path),
                properties.getProperty("revision", ""),
                parseLong(properties.getProperty("imageSize")),
                properties.getProperty("imageSha1", ""),
                parseLong(properties.getProperty("modifiedTime")));
    }

    /**
     * Writes the configuration.
     *
     * <p>The file is written to a temporary file first and then moved into place, so that a
     * failure in the middle of the write cannot leave a broken configuration behind.
     *
     * @param file the file to write
     * @throws IOException when the file cannot be written
     */
    public void write(Path file) throws IOException {
        Path directory = file.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Properties properties = new Properties();
        properties.setProperty("path", path.toAbsolutePath().normalize().toString());
        properties.setProperty("revision", revision);
        properties.setProperty("imageSize", Long.toString(imageSize));
        properties.setProperty("imageSha1", imageSha1);
        properties.setProperty("modifiedTime", Long.toString(modifiedTimeMillis));

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            properties.store(writer, "The disc image of Ratchet & Clank: Getting Wood. "
                    + "Only the path of the image and facts about it are stored here.");
        }
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
