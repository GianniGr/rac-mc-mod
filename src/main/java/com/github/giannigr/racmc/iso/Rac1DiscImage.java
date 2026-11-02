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

    /**
     * Reads a range of bytes at an absolute offset of the image.
     *
     * <p>The game addresses its data by absolute sector, not by the name of a file of the image
     * system, so the tables that point at the game data can only be read this way.
     *
     * @param byteOffset the offset of the first byte in the image
     * @param length the number of bytes to read
     * @return the bytes that were read
     * @throws IOException when the range cannot be read
     * @throws IllegalArgumentException when the range lies outside of the image
     */
    byte[] readBytes(long byteOffset, int length) throws IOException;

    /**
     * Reads a number of sectors at an absolute sector number of the image.
     *
     * @param sector the number of the first sector
     * @param sectorCount the number of sectors to read
     * @return the bytes that were read
     * @throws IOException when the range cannot be read
     * @throws IllegalArgumentException when the range lies outside of the image
     */
    byte[] readSectors(long sector, int sectorCount) throws IOException;
}
