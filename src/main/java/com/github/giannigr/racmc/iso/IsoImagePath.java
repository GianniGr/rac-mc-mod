package com.github.giannigr.racmc.iso;

import java.util.Locale;

/**
 * The paths of the files inside a disc image.
 *
 * <p>A path inside an image is a relative path with {@code /} separators. The helper rejects every
 * path that could escape the image or that could be used to build a path outside an extraction
 * directory, so that a malformed image cannot make the extractor write outside of its target.
 */
public final class IsoImagePath {

    /** The longest path inside an image that is accepted. */
    public static final int MAX_LENGTH = 1024;

    /** The longest single segment of a path inside an image that is accepted. */
    public static final int MAX_SEGMENT_LENGTH = 255;

    private IsoImagePath() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks a path inside an image and returns it unchanged.
     *
     * <p>The path has to be relative, must not contain a {@code .} or {@code ..} segment, must not
     * contain a drive or stream separator and must not contain control characters.
     *
     * @param isoPath the path to check
     * @return the path, unchanged
     * @throws IllegalArgumentException when the path is missing or unsafe
     */
    public static String requireSafe(String isoPath) {
        if (isoPath == null) {
            throw new IllegalArgumentException("The path inside the image must not be null");
        }
        if (isoPath.isBlank()) {
            throw new IllegalArgumentException("The path inside the image must not be empty");
        }
        if (isoPath.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The path inside the image is longer than "
                    + MAX_LENGTH + " characters");
        }
        if (isoPath.startsWith("/") || isoPath.startsWith("\\")) {
            throw new IllegalArgumentException("The path inside the image must be relative");
        }
        for (int index = 0; index < isoPath.length(); index++) {
            char character = isoPath.charAt(index);
            if (character < 0x20 || character == ':' || character == '\\') {
                throw new IllegalArgumentException(
                        "The path inside the image contains an unusable character");
            }
        }
        for (String segment : isoPath.split("/", -1)) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("The path inside the image contains an empty segment");
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        "The path inside the image must not contain a relative segment");
            }
            if (segment.length() > MAX_SEGMENT_LENGTH) {
                throw new IllegalArgumentException("A segment of the path inside the image is longer than "
                        + MAX_SEGMENT_LENGTH + " characters");
            }
        }
        return isoPath;
    }

    /**
     * The key a path is stored under, so that lookups ignore the case of the file name.
     *
     * @param isoPath the path inside the image
     * @return the lookup key of the path
     */
    static String key(String isoPath) {
        return isoPath.toUpperCase(Locale.ROOT);
    }
}
