package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused checks for the configured place of the cache of the assets.
 *
 * <p>The checks use temporary directories and assets that were built by hand. No data of the game
 * and no file of the player is used anywhere.
 */
class Rac1CacheLocationTest {

    /** The identity of the disc the checks use. */
    private static final String KEY = "NTSC-U v1.00|SCUS_971.99|test";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Without a configuration the standard place of the mod is used")
    void withoutAConfigurationTheStandardPlaceIsUsed() throws IOException {
        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(location.usable(), "The standard place has to be usable: " + location.failure());
        assertEquals(configDirectory().resolve("cache"), location.directory());
    }

    @Test
    @DisplayName("A blank value means the standard place of the mod")
    void aBlankValueMeansTheStandardPlace() throws IOException {
        writeDirectory("");

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(location.usable(), "A blank value has to be usable: " + location.failure());
        assertEquals(configDirectory().resolve("cache"), location.directory());
    }

    @Test
    @DisplayName("The file of the configuration is written when there is none, with a blank value")
    void theConfigurationFileIsWrittenWhenThereIsNone() throws IOException {
        assertFalse(Files.exists(configFile()), "There is no configuration to begin with");

        Rac1CacheLocation.writeDefaultWhenMissing(configFile());

        assertTrue(Files.isRegularFile(configFile()),
                "The file has to exist, so that the setting can be found");
        assertEquals("", readValue(),
                "The value has to be there and has to be blank, which means the standard place");
        String text = Files.readString(configFile());
        assertTrue(text.contains("config/racmc/cache"),
                "The file has to say where the standard place is: " + text);
        assertTrue(text.contains("start the game again"),
                "The file has to say that a change is applied at the next start: " + text);
        assertTrue(text.contains("no escaping"),
                "The file has to say that a path is written as it is: " + text);
    }

    @Test
    @DisplayName("Writing the configuration again does not overwrite a value that was written")
    void writingTheConfigurationAgainKeepsTheValue() throws IOException {
        writeDirectory("somewhere");

        Rac1CacheLocation.writeDefaultWhenMissing(configFile());

        assertEquals("somewhere", readValue());
    }

    @Test
    @DisplayName("A path with backslashes is read the way it was written")
    void aPathWithBackslashesIsReadTheWayItWasWritten() throws IOException {
        Path elsewhere = tempDir.resolve("with space");
        Files.createDirectories(elsewhere);
        writeDirectory(elsewhere.toString());

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(location.usable(), "The directory has to be usable: " + location.failure());
        assertEquals(elsewhere.toAbsolutePath().normalize(), location.directory(),
                "The backslashes of the path must not be swallowed");
    }

    @Test
    @DisplayName("A relative value is resolved against the configuration directory and not "
            + "against the working directory")
    void aRelativeValueIsResolvedAgainstTheConfigurationDirectory() throws IOException {
        writeDirectory("sub");

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(location.usable(), "The directory has to be usable: " + location.failure());
        assertEquals(configDirectory().resolve("sub"), location.directory(),
                "The value belongs below config/racmc and not below the working directory");
    }

    @Test
    @DisplayName("A relative value may point above the configuration directory")
    void aRelativeValueMayPointAboveTheConfigurationDirectory() throws IOException {
        writeDirectory("../shared");

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(location.usable(), "The directory has to be usable: " + location.failure());
        assertEquals(tempDir.resolve("shared"), location.directory());
    }

    @Test
    @DisplayName("A relative value is normalized")
    void aRelativeValueIsNormalized() throws IOException {
        writeDirectory("./sub/./deep/../cache");

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(location.usable(), "The directory has to be usable: " + location.failure());
        assertEquals(configDirectory().resolve("sub").resolve("cache"), location.directory());
    }

    @Test
    @DisplayName("An absolute value is used as it is")
    void anAbsoluteValueIsUsedAsItIs() throws IOException {
        Path elsewhere = tempDir.resolve("elsewhere");
        Files.createDirectories(elsewhere);
        writeDirectory(elsewhere.toString());

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(location.usable(), "The directory has to be usable: " + location.failure());
        assertEquals(elsewhere.toAbsolutePath().normalize(), location.directory());
    }

    @Test
    @DisplayName("A value that is not a path of the system is refused")
    void aValueThatIsNotAPathIsRefused() throws IOException {
        writeDirectory("bad" + (char) 0 + "name");

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertFalse(location.usable(), "A path that cannot be built has to be refused");
        assertNull(location.directory(), "A refused location must not hold a directory");
        assertNotNull(location.failure());
        assertTrue(location.failure().contains("bad"),
                "The message has to name the value: " + location.failure());
        assertTrue(location.failure().contains(Rac1CacheLocation.FILE_NAME),
                "The message has to name the file to change: " + location.failure());
        assertTrue(location.failure().contains(Rac1CacheLocation.DIRECTORY_KEY),
                "The message has to name the key to change: " + location.failure());
        assertTrue(location.failure().contains("start the game again"),
                "The message has to say what to do: " + location.failure());
    }

    @Test
    @DisplayName("A value that names a file is refused")
    void aValueThatNamesFileIsRefused() throws IOException {
        Files.writeString(configDirectory().resolve("racmc-media.properties"), "path=x.iso\n");
        writeDirectory("racmc-media.properties");

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertFalse(location.usable(), "A file has to be refused instead of being written into");
        assertNull(location.directory(), "A refused location must not hold a directory");
        assertNotNull(location.failure());
        assertTrue(location.failure().contains("is a file and not a directory"),
                "The message has to say what is wrong: " + location.failure());
    }

    @Test
    @DisplayName("A value whose directory cannot be created is refused")
    void aValueWhoseDirectoryCannotBeCreatedIsRefused() throws IOException {
        Files.writeString(configDirectory().resolve("blocked"), "not a directory");
        writeDirectory("blocked/cache");

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertFalse(location.usable(), "A directory that cannot be created has to be refused");
        assertNull(location.directory(), "A refused location must not hold a directory");
        assertNotNull(location.failure());
        assertTrue(location.failure().contains("could not be created"),
                "The message has to say what is wrong: " + location.failure());
    }

    @Test
    @DisplayName("A configuration that is not a file is refused")
    void aConfigurationThatIsNotAFileIsRefused() throws IOException {
        Files.createDirectories(configFile());

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertFalse(location.usable(), "A configuration that cannot be read has to be refused");
        assertNull(location.directory(), "A refused location must not hold a directory");
        assertNotNull(location.failure());
        assertTrue(location.failure().contains("could not be read"),
                "The message has to say what is wrong: " + location.failure());
    }

    @Test
    @DisplayName("A configuration that cannot be read is refused")
    void aConfigurationThatCannotBeReadIsRefused() throws IOException {
        // A byte sequence that is not valid UTF-8: the file cannot be read at all.
        Files.write(configFile(), new byte[] { 'd', 'i', 'r', 'e', 'c', 't', 'o', 'r', 'y', '=',
                (byte) 0xC3, (byte) 0x28 });

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertFalse(location.usable(), "A broken configuration has to be refused");
        assertNull(location.directory(), "A refused location must not hold a directory");
        assertNotNull(location.failure());
        assertTrue(location.failure().contains("could not be read"),
                "The message has to say what is wrong: " + location.failure());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @DisplayName("A value that depends on the working directory of the drive is refused")
    void aValueThatDependsOnTheWorkingDirectoryIsRefused() throws IOException {
        writeDirectory("\\data");

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertFalse(location.usable(),
                "A path without a drive would depend on where the game was started from");
        assertNull(location.directory(), "A refused location must not hold a directory");
        assertNotNull(location.failure());
        assertTrue(location.failure().contains("does not name a directory on its own"),
                "The message has to say what is wrong: " + location.failure());
    }

    @Test
    @DisplayName("A changed location keeps the cache that was written before")
    void aChangedLocationKeepsTheCacheThatWasThere() throws IOException {
        Rac1CacheLocation first = Rac1CacheLocation.resolve(configFile(), configDirectory());
        Rac1CacheStore oldStore = new Rac1CacheStore(first.directory());
        oldStore.store(KEY, SyntheticAssets.content(KEY));
        Path oldFile = oldStore.fileFor(KEY);
        assertTrue(Files.isRegularFile(oldFile), "The cache has to be there");

        Path moved = tempDir.resolve("moved-cache");
        writeDirectory(moved.toString());
        Rac1CacheLocation second = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(second.usable(), "The new directory has to be usable: " + second.failure());
        assertEquals(moved.toAbsolutePath().normalize(), second.directory());
        assertTrue(Files.isRegularFile(oldFile),
                "The cache that was written before must not be moved or deleted");
        assertTrue(new Rac1CacheStore(second.directory()).load(KEY).isEmpty(),
                "The cache of the old place must not be used from the new one");
        assertTrue(new Rac1CacheStore(first.directory()).load(KEY).isPresent(),
                "The cache of the old place has to stay usable");
    }

    @Test
    @DisplayName("The configured directory is used again after a restart")
    void theConfiguredDirectoryIsUsedAgainAfterARestart() throws IOException {
        Path elsewhere = tempDir.resolve("restart-cache");
        writeDirectory(elsewhere.toString());

        Rac1CacheLocation firstRun = Rac1CacheLocation.resolve(configFile(), configDirectory());
        Rac1CacheLocation secondRun = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(firstRun.usable(), "The directory has to be usable: " + firstRun.failure());
        assertTrue(secondRun.usable(), "The directory has to be usable: " + secondRun.failure());
        assertEquals(elsewhere.toAbsolutePath().normalize(), firstRun.directory());
        assertEquals(firstRun.directory(), secondRun.directory(),
                "A new start of the game has to find the same directory");
    }

    @Test
    @DisplayName("The first value of the key is the one that is used")
    void theFirstValueOfTheKeyIsTheOneThatIsUsed() throws IOException {
        Path elsewhere = tempDir.resolve("elsewhere");
        Files.createDirectories(elsewhere);
        Files.writeString(configFile(),
                "# a comment of the player" + System.lineSeparator()
                        + Rac1CacheLocation.DIRECTORY_KEY + "=first" + System.lineSeparator()
                        + Rac1CacheLocation.DIRECTORY_KEY + "=second" + System.lineSeparator(),
                StandardCharsets.UTF_8);

        Rac1CacheLocation location = Rac1CacheLocation.resolve(configFile(), configDirectory());

        assertTrue(location.usable(), "The directory has to be usable: " + location.failure());
        assertEquals(configDirectory().resolve("first"), location.directory(),
                "The first value of the key is the one that counts, and the later ones are "
                        + "ignored");
    }

    /**
     * The configuration directory of the mod inside the temporary directory of the checks.
     */
    private Path configDirectory() throws IOException {
        Path directory = tempDir.resolve("config");
        Files.createDirectories(directory);
        return directory;
    }

    /**
     * The file of the configuration of the cache.
     */
    private Path configFile() throws IOException {
        return Rac1CacheLocation.fileIn(configDirectory());
    }

    /**
     * Writes the configured directory into the file of the configuration, the way a player
     * writes it.
     */
    private void writeDirectory(String value) throws IOException {
        Files.writeString(configFile(),
                Rac1CacheLocation.DIRECTORY_KEY + "=" + value + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    /**
     * Reads the configured directory out of the file of the configuration.
     */
    private String readValue() throws IOException {
        for (String line : Files.readAllLines(configFile(), StandardCharsets.UTF_8)) {
            String text = line.strip();
            if (text.startsWith(Rac1CacheLocation.DIRECTORY_KEY + "=")) {
                return text.substring(Rac1CacheLocation.DIRECTORY_KEY.length() + 1).strip();
            }
        }
        return "";
    }
}
