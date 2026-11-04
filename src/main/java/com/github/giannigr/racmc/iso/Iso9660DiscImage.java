package com.github.giannigr.racmc.iso;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A disc image that is read from an ISO 9660 file system.
 *
 * <p>The image keeps the file it was opened from open and reads single sectors or file extents
 * from it. Nothing is buffered beyond the file that is currently being read, so an image of any
 * size can be used.
 */
final class Iso9660DiscImage implements Rac1DiscImage {

    /** The largest file that {@link #readFile(String, long)} may read by default. */
    static final long DEFAULT_MAX_READ_BYTES = 64L * 1024L * 1024L;

    /** The size of the buffer that streams read into. */
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;

    private final Path source;
    private final Rac1DiscRevision revision;
    private final RandomAccessFile image;
    private final long imageSize;
    private final Iso9660Tree tree;

    Iso9660DiscImage(Path source, Rac1DiscRevision revision, RandomAccessFile image, long imageSize,
                     Iso9660Tree tree) {
        this.source = Objects.requireNonNull(source, "source");
        this.revision = Objects.requireNonNull(revision, "revision");
        this.image = Objects.requireNonNull(image, "image");
        this.imageSize = imageSize;
        this.tree = Objects.requireNonNull(tree, "tree");
    }

    @Override
    public Rac1DiscRevision revision() {
        return revision;
    }

    @Override
    public Path sourcePath() {
        return source;
    }

    @Override
    public long imageSizeBytes() {
        return imageSize;
    }

    @Override
    public List<String> listFilePaths() {
        return new ArrayList<>(tree.files().keySet());
    }

    @Override
    public byte[] readFile(String isoPath, long maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("The maximum number of bytes to read must be positive");
        }
        Iso9660File file = requireFile(isoPath);
        if (file.size() > maxBytes) {
            throw new IllegalArgumentException("The file '" + isoPath + "' is larger than the "
                    + maxBytes + " bytes that may be read");
        }
        byte[] content = new byte[(int) file.size()];
        try {
            Iso9660Tree.readFully(image, file.offset(), content, imageSize);
        } catch (IsoFormatException exception) {
            throw new IOException("The file '" + isoPath + "' points outside of the image", exception);
        }
        return content;
    }

    @Override
    public InputStream openFile(String isoPath) throws IOException {
        Iso9660File file = requireFile(isoPath);
        long offset = file.offset();
        if (offset < 0 || offset > imageSize - file.size()) {
            throw new IOException("The file '" + isoPath + "' points outside of the image");
        }
        image.seek(offset);
        return new BoundedImageStream(image, file.size());
    }

    @Override
    public byte[] readBytes(long byteOffset, int length) throws IOException {
        if (length < 0) {
            throw new IllegalArgumentException("The number of bytes to read must not be negative");
        }
        if (byteOffset < 0 || byteOffset > imageSize - length) {
            throw new IllegalArgumentException("The range lies outside of the image");
        }
        byte[] content = new byte[length];
        image.seek(byteOffset);
        image.readFully(content);
        return content;
    }

    @Override
    public byte[] readSectors(long sector, int sectorCount) throws IOException {
        if (sectorCount < 0) {
            throw new IllegalArgumentException("The number of sectors to read must not be negative");
        }
        return readBytes(sector * Iso9660Tree.SECTOR_SIZE, sectorCount * Iso9660Tree.SECTOR_SIZE);
    }

    private Iso9660File requireFile(String isoPath) {
        String safePath = IsoImagePath.requireSafe(isoPath);
        return tree.find(safePath).orElseThrow(() -> new IllegalArgumentException(
                "The image does not contain the file '" + safePath + "'"));
    }

    @Override
    public void close() throws IOException {
        image.close();
    }

    /**
     * A stream over a part of the image.
     *
     * <p>The stream reads at most the number of bytes it was created for and never reads past the
     * end of the image. Closing the stream leaves the image open, because the image is shared.
     */
    private static final class BoundedImageStream extends InputStream {

        private final RandomAccessFile image;
        private long remaining;

        private BoundedImageStream(RandomAccessFile image, long length) {
            this.image = image;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = image.read();
            if (value < 0) {
                return -1;
            }
            remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) {
                return 0;
            }
            if (remaining <= 0) {
                return -1;
            }
            int chunk = (int) Math.min(length, remaining);
            int read = image.read(buffer, offset, chunk);
            if (read < 0) {
                return -1;
            }
            remaining -= read;
            return read;
        }

        @Override
        public int available() {
            return (int) Math.min(Integer.MAX_VALUE, remaining);
        }

        @Override
        public void close() {
            // The image itself is closed by the owner of the stream.
            remaining = 0;
        }
    }
}
