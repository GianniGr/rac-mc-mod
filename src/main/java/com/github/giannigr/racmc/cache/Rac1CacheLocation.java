package com.github.giannigr.racmc.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * The place the cache of the assets is kept, as the player configured it.
 *
 * <p>The configuration lives in the file {@value #FILE_NAME} of the configuration directory of the
 * mod, next to the configuration of the disc image. It is a plain file of lines of the form
 * {@code key=value}; lines that start with {@code #} are comments. The value of a line is taken
 * exactly as it is written, so a path needs no escaping for backslashes. The key
 * {@value #DIRECTORY_KEY} holds the directory of the cache:
 *
 * <ul>
 *   <li>when the key is missing or blank, the cache stays in the standard place of the mod:
 *       {@value #DEFAULT_DIRECTORY} inside the configuration directory of the mod, which is
 *       {@code config/racmc/cache} of the Minecraft instance;</li>
 *   <li>an absolute path is used as it is;</li>
 *   <li>a relative path is resolved against the configuration directory of the mod and never
 *       against the working directory of the process; and</li>
 *   <li>the value is read when the game starts, so a change is used the next time the game is
 *       started.</li>
 *   <li>when the key is written more than once, the first value is the one that counts and the
 *       later ones are ignored, so the file always names one directory.</li>
 * </ul>
 *
 * <p>The directory is normalized and checked. A value that is not a path of the system, a value
 * that names a file and a value whose directory cannot be created or written to are refused with
 * a message that names the file to change. A refused directory never falls back to another one:
 * the cache stays unusable and the game stays closed instead of running with a cache that is only
 * half there.
 *
 * <p>The setting never moves and never deletes a cache that was written before. A cache of the
 * old place simply stays where it is; the player gets it back by setting the old value again.
 *
 * @param directory the directory of the cache, or {@code null} when the configured value could
 *        not be turned into a path at all
 * @param failure the reason why the directory cannot be used, or {@code null} when it can be used
 */
public record Rac1CacheLocation(Path directory, String failure) {

    /** The name of the file the configuration is stored in. */
    public static final String FILE_NAME = "racmc-cache.properties";

    /** The key of the directory of the cache in that file. */
    public static final String DIRECTORY_KEY = "directory";

    /**
     * The directory of the cache when the key is missing or blank, below the configuration of
     * the mod.
     */
    public static final String DEFAULT_DIRECTORY = "cache";

    /** The comment that is written into a configuration file that the mod creates. */
    private static final String COMMENT = "Where the cache of the converted assets of Ratchet & "
            + "Clank: Getting Wood is kept.\n"
            + "Leave \"directory\" blank to keep it in the standard place of this Minecraft "
            + "instance: config/racmc/cache.\n"
            + "An absolute path keeps it anywhere else. A relative path is resolved against "
            + "config/racmc of this instance - never against the working directory - and may use "
            + "\"..\".\n"
            + "Write the path exactly as it is; the file needs no escaping for backslashes.\n"
            + "The value is read when the game starts, so start the game again after a change.\n"
            + "Changing it never moves and never deletes a cache that was written before.";

    /**
     * Creates a location.
     *
     * @throws IllegalArgumentException when a directory is given without a reason why it cannot be
     *         used, or a reason without a directory
     */
    public Rac1CacheLocation {
        if ((directory == null) == (failure == null)) {
            throw new IllegalArgumentException(
                    "Either the directory or the reason why it cannot be used is missing");
        }
    }

    /**
     * Whether the cache can be kept there.
     *
     * @return {@code true} when a directory was found and checked
     */
    public boolean usable() {
        return failure == null;
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
     * Writes the configuration file with a blank value when there is none yet.
     *
     * <p>The file is what makes the setting discoverable: it holds the key with a blank value and
     * a comment that says how the value is read. An existing file is never changed here, so a
     * value that the player wrote is kept.
     *
     * @param file the file of the configuration
     * @throws IOException when the file cannot be written
     */
    public static void writeDefaultWhenMissing(Path file) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder text = new StringBuilder();
        for (String line : COMMENT.split("\n")) {
            text.append("# ").append(line).append(System.lineSeparator());
        }
        text.append(DIRECTORY_KEY).append('=').append(System.lineSeparator());
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Reads the configuration and finds the directory of the cache.
     *
     * <p>The directory is created when it does not exist yet, so that a place which cannot be
     * written to is refused here and not in the middle of the conversion of the assets.
     *
     * @param file the file of the configuration
     * @param configDirectory the configuration directory of the mod, the base of every relative
     *        value
     * @return the location, which holds either the directory or the reason why it cannot be used
     */
    public static Rac1CacheLocation resolve(Path file, Path configDirectory) {
        Path base = configDirectory.toAbsolutePath().normalize();
        String raw;
        try {
            raw = read(file);
        } catch (IOException exception) {
            return refused(file, "", "the configuration could not be read ("
                    + describe(exception) + ")");
        }

        Path candidate;
        if (raw.isEmpty()) {
            candidate = base.resolve(DEFAULT_DIRECTORY);
        } else {
            Path parsed;
            try {
                parsed = Path.of(raw);
            } catch (InvalidPathException exception) {
                return refused(file, raw, "\"" + raw + "\" is not a path of this system ("
                        + describe(exception) + ")");
            }
            if (parsed.isAbsolute()) {
                candidate = parsed;
            } else if (parsed.getRoot() != null) {
                // A path with a root but without a drive - "\data" on Windows - is relative to
                // the drive the working directory is on, so it would depend on where the game
                // was started from. Such a value is refused instead of being guessed at.
                return refused(file, raw, "\"" + raw + "\" does not name a directory on its own; "
                        + "write an absolute path or a path below " + base);
            } else {
                candidate = base.resolve(parsed);
            }
        }

        candidate = candidate.normalize();
        if (Files.exists(candidate) && !Files.isDirectory(candidate)) {
            return refused(file, raw, "\"" + candidate + "\" is a file and not a directory");
        }
        try {
            Files.createDirectories(candidate);
        } catch (IOException exception) {
            return refused(file, raw, "\"" + candidate + "\" could not be created ("
                    + describe(exception) + ")");
        }
        if (!Files.isWritable(candidate)) {
            return refused(file, raw, "\"" + candidate + "\" cannot be written to");
        }
        return new Rac1CacheLocation(candidate, null);
    }

    /**
     * Reads the configured directory out of the file.
     *
     * <p>The value of the key is taken exactly as it is written: the file is read line by line and
     * no escape sequence is interpreted, so a path with backslashes and a path with a space are
     * read the way the player wrote them.
     *
     * @param file the file of the configuration
     * @return the value of the key, or an empty string when the file does not exist or holds no
     *         value
     * @throws IOException when the file exists but is not a file that can be read
     */
    private static String read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("it is not a file");
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#") || text.startsWith("!")) {
                continue;
            }
            int separator = text.indexOf('=');
            if (separator < 0) {
                continue;
            }
            if (!DIRECTORY_KEY.equals(text.substring(0, separator).strip())) {
                continue;
            }
            return text.substring(separator + 1).strip();
        }
        return "";
    }

    /**
     * Builds the outcome of a directory that cannot be used.
     *
     * @param file the file of the configuration, which is where the value has to be changed
     * @param raw the value that was configured, or an empty string when there was none
     * @param reason what is wrong with it
     * @return the location, which holds the reason and no directory
     */
    private static Rac1CacheLocation refused(Path file, String raw, String reason) {
        String configured = raw.isEmpty() ? "" : ", configured as \"" + raw + "\"";
        String message = "The cache directory cannot be used" + configured + ": " + reason + ". "
                + "Change the key \"" + DIRECTORY_KEY + "\" in " + file
                + " - leave it blank to use the standard place - and start the game again.";
        return new Rac1CacheLocation(null, message);
    }

    /**
     * The message of a failure, so that the reason is never empty.
     *
     * @param exception the failure
     * @return the message, or the name of the kind of the failure
     */
    private static String describe(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
