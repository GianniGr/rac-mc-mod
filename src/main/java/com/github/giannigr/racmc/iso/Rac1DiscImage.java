package com.github.giannigr.racmc.iso;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * A validated Ratchet &amp; Clank disc image that assets can be read from.
 *
 * <p>This is the interface the importer works against. It exposes the revision of the disc and
 * gives read-only access to the files of the image, so that a later extraction step can be written
 * against it without knowing how the image is stored or how it was validated.
 *
 * <p>The interface deliberately does not extract anything. It hands out the bytes of the files of
 * the image and nothing else; what is done with them is the business of the extraction step that
 * comes later.
 */
public interface Rac1DiscImage extends Closeable {

    /**
     * The revision of the disc.
     *
     * @return the revision
     */
    Rac1DiscRevision revision();

    /**
     * The file the image was read from.
     *
     * @return the path of the image
     */
    Path sourcePath();

    /**
     * The size of the image in bytes.
     *
     * @return the size in bytes
     */
    long imageSizeBytes();

    /**
     * The paths of the files of the image.
     *
     * @return the paths, relative to the root of the image
     */
    List<String> listFilePaths();

    /**
     * Reads a file of the image into memory.
     *
     * @param isoPath the path of the file inside the image, for example {@code SYSTEM.CNF}
     * @param maxBytes the largest number of bytes that may be read
     * @return the contents of the file
     * @throws IOException when the file cannot be read
     * @throws IllegalArgumentException when the path is unsafe or the file is larger than
     *         {@code maxBytes}
     */
    byte[] readFile(String isoPath, long maxBytes) throws IOException;

    /**
     * Opens a file of the image for reading.
     *
     * <p>The returned stream reads the file in bounded chunks, so an arbitrarily large file can be
     * processed without loading it into memory.
     *
     * @param isoPath the path of the file inside the image
     * @return a stream over the contents of the file
     * @throws IOException when the file cannot be read
     * @throws IllegalArgumentException when the path is unsafe or the image does not contain it
     */
    InputStream openFile(String isoPath) throws IOException;
}
