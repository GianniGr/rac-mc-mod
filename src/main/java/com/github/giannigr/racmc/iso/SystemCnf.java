package com.github.giannigr.racmc.iso;

import java.util.Locale;
import java.util.Objects;

/**
 * The contents of the {@code SYSTEM.CNF} file of a PlayStation 2 disc.
 *
 * <p>The file is plain ASCII in a {@code KEY = VALUE} form. The keys the validator uses are
 * {@code BOOT2}, which names the boot executable of the disc, {@code VER}, which holds the
 * revision of the disc, and {@code VMODE}, which holds its video mode.
 */
final class SystemCnf {

    private final String bootExecutable;
    private final String version;
    private final String videoMode;

    private SystemCnf(String bootExecutable, String version, String videoMode) {
        this.bootExecutable = bootExecutable;
        this.version = version;
        this.videoMode = videoMode;
    }

    /**
     * Parses the contents of a {@code SYSTEM.CNF} file.
     *
     * <p>Parsing is deliberately forgiving: a key that is missing or malformed is reported as an
     * absent value instead of failing the whole file, so that an image with an unusual but
     * otherwise valid {@code SYSTEM.CNF} can still be identified by its boot executable.
     *
     * @param content the raw bytes of the file
     * @return the parsed values
     */
    static SystemCnf parse(byte[] content) {
        Objects.requireNonNull(content, "content");
        String text = new String(content, java.nio.charset.StandardCharsets.ISO_8859_1);

        String bootExecutable = null;
        String version = null;
        String videoMode = null;

        for (String line : text.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            switch (key.toUpperCase(Locale.ROOT)) {
                case "BOOT2" -> bootExecutable = parseBootPath(value);
                case "VER" -> version = emptyToNull(value);
                case "VMODE" -> videoMode = emptyToNull(value);
                default -> {
                    // Other keys are not used by the validator.
                }
            }
        }
        return new SystemCnf(bootExecutable, version, videoMode);
    }

    /**
     * Extracts the file name of the boot executable from the value of the {@code BOOT2} key.
     *
     * <p>The value has the form {@code cdrom0:\SCUS_971.99;1}. The device prefix and the version
     * suffix of the file name are removed, so that only the plain file name is left.
     *
     * @param value the raw value of the key
     * @return the file name of the boot executable, or {@code null} when the value is unusable
     */
    private static String parseBootPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String remainder = value;
        int device = remainder.lastIndexOf(':');
        if (device >= 0) {
            remainder = remainder.substring(device + 1);
        }
        remainder = remainder.replace('\\', '/');
        int slash = remainder.lastIndexOf('/');
        if (slash >= 0) {
            remainder = remainder.substring(slash + 1);
        }
        int version = remainder.indexOf(';');
        if (version >= 0) {
            remainder = remainder.substring(0, version);
        }
        remainder = remainder.trim();
        return remainder.isEmpty() ? null : remainder;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * The file name of the boot executable the disc boots from.
     *
     * @return the file name, or {@code null} when the file does not declare one
     */
    String bootExecutable() {
        return bootExecutable;
    }

    /**
     * The revision the disc reports, for example {@code 2.00}.
     *
     * @return the revision, or {@code null} when the file does not declare one
     */
    String version() {
        return version;
    }

    /**
     * The video mode the disc reports, for example {@code PAL}.
     *
     * @return the video mode, or {@code null} when the file does not declare one
     */
    String videoMode() {
        return videoMode;
    }
}
