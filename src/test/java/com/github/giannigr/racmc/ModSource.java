package com.github.giannigr.racmc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The source of the classes of the mod, read out of the files of the project.
 *
 * <p>Some guards of the mod live in the classes of the game of blocks, where a player of the
 * game of blocks would have to be built to check them. A player of the game of blocks cannot be
 * built on a plain virtual machine, so the order of such guards is read out of the source
 * instead.
 */
public final class ModSource {

    /** The place of the source of the mod, relative to the directory of the project. */
    private static final String ROOT = "src/main/java/";

    /** The directory of the project. */
    private static final Path PROJECT = findTheProject();

    private ModSource() {
        throw new UnsupportedOperationException("Utility class");
    }
    /**
     * The source of a class of the mod.
     *
     * @param name the place of the class, with the slashes of the place of the packages
     * @return the text of the source
     */
    public static String of(String name) {
        try {
            return Files.readString(PROJECT.resolve(ROOT + name), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * The body of a method of a source.
     *
     * @param source the text of the source
     * @param signature the beginning of the line of the method
     * @return the text of the method
     */
    public static String methodOf(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "the method is not in the source: " + signature);
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, "the method has no end: " + signature);
        return source.substring(start, end);
    }

    /**
     * The directory of the project.
     *
     * @return the directory
     */
    private static Path findTheProject() {
        String given = System.getProperty("racmc.projectDir");
        if (given != null && Files.isRegularFile(
                Paths.get(given).resolve(ROOT + "com/github/giannigr/racmc/item/BlasterItem.java"))) {
            return Paths.get(given);
        }
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null && !Files.isRegularFile(
                directory.resolve(ROOT + "com/github/giannigr/racmc/item/BlasterItem.java"))) {
            directory = directory.getParent();
        }
        assertTrue(directory != null, "the source of the mod is not found");
        return directory;
    }
}
