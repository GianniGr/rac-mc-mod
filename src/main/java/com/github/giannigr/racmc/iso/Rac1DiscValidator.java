package com.github.giannigr.racmc.iso;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates candidate Ratchet &amp; Clank disc images.
 *
 * <p>The validator answers one question: is the file a disc image of a Ratchet &amp; Clank release
 * that this mod supports, and is it intact? It reads only the few parts of the image that are
 * needed to answer that, so an image of several gigabytes is checked in a fraction of a second.
 *
 * <p>A candidate is accepted when all of the following hold:
 *
 * <ol>
 *   <li>the file is an ISO 9660 image with 2048 byte sectors;</li>
 *   <li>it contains a {@code SYSTEM.CNF} file that names a boot executable;</li>
 *   <li>the boot executable is the one of a supported revision;</li>
 *   <li>the boot executable has the expected size; and</li>
 *   <li>the boot executable has the expected SHA-256 digest.</li>
 * </ol>
 *
 * <p>Everything else is refused with a message that says what was wrong: an unsupported revision,
 * a disc of another game, a damaged image, a file that is not an ISO 9660 image at all or a
 * container format that is not supported.
 */
public final class Rac1DiscValidator {

    /** The largest {@code SYSTEM.CNF} file that is read. */
    private static final int MAX_SYSTEM_CNF_BYTES = 64 * 1024;

    /** The size of the buffer that the digest of the boot executable is computed with. */
    private static final int DIGEST_BUFFER_SIZE = 64 * 1024;

    /** The size of the buffer that the digest of the whole image is computed with. */
    private static final int IMAGE_DIGEST_BUFFER_SIZE = 1024 * 1024;

    /** The smallest file that can hold an ISO 9660 primary volume descriptor. */
    private static final long MINIMUM_ISO_SIZE =
            (long) (Iso9660Tree.PRIMARY_VOLUME_DESCRIPTOR_SECTOR + 1) * Iso9660Tree.SECTOR_SIZE;

    private final Rac1DiscCatalog catalog;

    /**
     * Creates a validator that uses the supported retail revisions.
     */
    public Rac1DiscValidator() {
        this(Rac1DiscCatalog.supported());
    }

    /**
     * Creates a validator that uses the given catalogue.
     *
     * @param catalog the catalogue of the revisions that are accepted
     */
    public Rac1DiscValidator(Rac1DiscCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * The catalogue of the revisions this validator accepts.
     *
     * @return the catalogue
     */
    public Rac1DiscCatalog catalog() {
        return catalog;
    }

    /**
     * Checks a candidate image and opens it when it is supported.
     *
     * <p>When the image is accepted, the returned result carries the opened image and the caller
     * has to close it. When the image is refused, no image is opened.
     *
     * @param path the file to check, may be {@code null}
     * @return the outcome of the check
     */
    public Rac1IsoSelectionResult inspect(Path path) {
        return inspect(path, IsoVerificationProgress.NONE);
    }

    /**
     * Checks a candidate image and opens it when it is supported.
     *
     * <p>When the image is accepted, the returned result carries the opened image and the caller
     * has to close it. When the image is refused, no image is opened.
     *
     * @param path the file to check, may be {@code null}
     * @param progress the listener that is told how far the verification has come, may be
     *        {@code null}
     * @return the outcome of the check
     */
    public Rac1IsoSelectionResult inspect(Path path, IsoVerificationProgress progress) {
        IsoVerificationProgress listener = progress == null ? IsoVerificationProgress.NONE : progress;
        if (path == null) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.INVALID_PATH,
                    "No disc image was selected.", null));
        }

        Path normalized;
        try {
            normalized = path.toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.INVALID_PATH,
                    "The selected path is not a usable file system path.", null));
        }
        if (containsRelativeSegment(path)) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.INVALID_PATH,
                    "The selected path must not contain a '..' segment.", normalized));
        }

        String fileName = normalized.getFileName() == null ? "" : normalized.getFileName().toString();
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(".chd")) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(
                    IsoValidationStatus.UNSUPPORTED_CONTAINER,
                    "CHD disc images are not supported. Convert the disc image to an ISO file and select that file.",
                    normalized));
        }
        if (lowerFileName.endsWith(".cue")) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(
                    IsoValidationStatus.UNSUPPORTED_CONTAINER,
                    "A .cue file is a cue sheet that points at the tracks of a disc image. Select the ISO file of the disc image.",
                    normalized));
        }

        if (!Files.exists(normalized)) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.INVALID_PATH,
                    "The selected file does not exist: " + normalized, normalized));
        }
        if (!Files.isRegularFile(normalized)) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.INVALID_PATH,
                    "The selected path is not a regular file: " + normalized, normalized));
        }
        if (!Files.isReadable(normalized)) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.IO_ERROR,
                    "The selected file cannot be read: " + normalized, normalized));
        }

        long size;
        try {
            size = Files.size(normalized);
        } catch (IOException exception) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.IO_ERROR,
                    "The size of the selected file could not be read: " + exception.getMessage(), normalized));
        }
        if (size < MINIMUM_ISO_SIZE) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.NOT_AN_ISO,
                    "The selected file is too small to be a disc image.", normalized));
        }

        RandomAccessFile handle = null;
        try {
            handle = new RandomAccessFile(normalized.toFile(), "r");
            Rac1IsoSelectionResult outcome = inspectImage(normalized, size, handle, listener);
            if (outcome.image() != null) {
                // The opened image owns the handle now, so it must not be closed below.
                handle = null;
            }
            return outcome;
        } catch (IsoFormatException exception) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(exception.status(),
                    exception.getMessage(), normalized));
        } catch (IOException exception) {
            return new Rac1IsoSelectionResult(IsoValidationResult.failed(IsoValidationStatus.IO_ERROR,
                    "The selected file could not be read: " + exception.getMessage(), normalized));
        } finally {
            if (handle != null) {
                try {
                    handle.close();
                } catch (IOException ignored) {
                    // The result of the check does not depend on the close.
                }
            }
        }
    }

    /**
     * Checks a candidate image without keeping it open.
     *
     * @param path the file to check, may be {@code null}
     * @return the outcome of the check
     */
    public IsoValidationResult validate(Path path) {
        try (Rac1IsoSelectionResult outcome = inspect(path)) {
            return outcome.result();
        } catch (IOException exception) {
            return IsoValidationResult.failed(IsoValidationStatus.IO_ERROR,
                    "The selected file could not be closed: " + exception.getMessage(), null);
        }
    }

    private Rac1IsoSelectionResult inspectImage(Path normalized, long size, RandomAccessFile handle,
                                                  IsoVerificationProgress progress)
            throws IsoFormatException, IOException {
        Iso9660Tree tree = Iso9660Tree.parse(handle, size);

        Iso9660File systemCnf = tree.find("SYSTEM.CNF").orElse(null);
        if (systemCnf == null) {
            return refused(IsoValidationStatus.UNSUPPORTED_IMAGE,
                    "The selected file is an ISO 9660 disc, but it does not contain a SYSTEM.CNF file, "
                            + "so it is not a Ratchet & Clank disc.", normalized);
        }

        SystemCnf configuration = SystemCnf.parse(readSystemCnf(handle, size, systemCnf));
        String productCode = configuration.bootExecutable();
        if (productCode == null) {
            productCode = findBootExecutableByMagic(handle, size, tree);
        }
        if (productCode == null) {
            return refused(IsoValidationStatus.UNSUPPORTED_IMAGE,
                    "The SYSTEM.CNF file of the selected disc does not name a boot executable, "
                            + "so the disc cannot be identified.", normalized);
        }

        Optional<DiscProfile> profile = catalog.byProductCode(productCode);
        if (profile.isEmpty()) {
            String details = describe(configuration);
            if (Iso9660Tree.isRac1Volume(tree.volumeIdentifier())) {
                return refused(IsoValidationStatus.UNSUPPORTED_REVISION,
                        "The selected disc is a Ratchet & Clank disc (" + productCode + details
                                + "), but that revision is not supported. Supported revisions: "
                                + catalog.supportedLabels() + ".", normalized);
            }
            return refused(IsoValidationStatus.UNSUPPORTED_IMAGE,
                    "The selected disc is not a supported Ratchet & Clank disc (boot executable '"
                            + productCode + "'" + details + ").", normalized);
        }

        DiscProfile supported = profile.get();
        if (!supported.matchesImageSize(size)) {
            return refused(IsoValidationStatus.CORRUPT_IMAGE,
                    "The selected disc is " + size + " bytes long, but the " + supported.revision().label()
                            + " release is " + supported.imageSize() + " bytes long.", normalized);
        }
        Iso9660File executable = tree.find(supported.executableName()).orElse(null);
        if (executable == null) {
            return refused(IsoValidationStatus.CORRUPT_IMAGE,
                    "The selected disc does not contain the boot executable '" + supported.executableName()
                            + "' that identifies the " + supported.revision().label() + " release.",
                    normalized);
        }
        if (executable.size() != supported.executableSize()) {
            return refused(IsoValidationStatus.CORRUPT_IMAGE,
                    "The boot executable of the selected disc is " + executable.size()
                            + " bytes long, but the " + supported.revision().label()
                            + " release has a boot executable of " + supported.executableSize()
                            + " bytes.", normalized);
        }
        String digest = digest(handle, size, executable);
        if (!digest.equals(supported.executableSha256())) {
            return refused(IsoValidationStatus.CORRUPT_IMAGE,
                    "The boot executable of the selected disc does not match the digest of the "
                            + supported.revision().label() + " release. The image is damaged or was modified.",
                    normalized);
        }
        String imageDigest = imageDigest(handle, size, progress);
        if (!imageDigest.equals(supported.imageSha1())) {
            return refused(IsoValidationStatus.CORRUPT_IMAGE,
                    "The selected disc does not match the digest of the whole " + supported.revision().label()
                            + " image. The image is damaged or was modified.", normalized);
        }

        Rac1DiscImage opened = new Iso9660DiscImage(normalized, supported.revision(), handle, size, tree);
        return new Rac1IsoSelectionResult(
                IsoValidationResult.supported(supported.revision(), supported.productCode(), normalized,
                        "The selected disc is a Ratchet & Clank " + supported.revision().label()
                                + " disc and passed every check."),
                opened);
    }

    /**
     * Reads the {@code SYSTEM.CNF} file of the image, bounded by its own size.
     */
    private static byte[] readSystemCnf(RandomAccessFile handle, long imageSize, Iso9660File file)
            throws IOException, IsoFormatException {
        long length = Math.min(file.size(), MAX_SYSTEM_CNF_BYTES);
        if (length <= 0) {
            throw new IsoFormatException(IsoValidationStatus.CORRUPT_IMAGE,
                    "The SYSTEM.CNF file of the selected disc is empty.");
        }
        long offset = file.offset();
        if (offset < 0 || offset > imageSize - length) {
            throw new IsoFormatException(IsoValidationStatus.CORRUPT_IMAGE,
                    "The SYSTEM.CNF file of the selected disc points outside of the image.");
        }
        byte[] content = new byte[(int) length];
        handle.seek(offset);
        handle.readFully(content);
        return content;
    }

    /**
     * Finds the boot executable of a disc that does not name one in its {@code SYSTEM.CNF} file.
     *
     * <p>The boot executable of a Ratchet &amp; Clank disc is a plain ELF file, so the first file
     * of the root directory that starts with the ELF magic is the boot executable.
     */
    private static String findBootExecutableByMagic(RandomAccessFile handle, long imageSize, Iso9660Tree tree)
            throws IOException {
        for (Iso9660File file : tree.files().values()) {
            if (file.path().indexOf('/') >= 0 || file.size() < 4) {
                continue;
            }
            long offset = file.offset();
            if (offset < 0 || offset > imageSize - 4) {
                continue;
            }
            byte[] magic = new byte[4];
            handle.seek(offset);
            handle.readFully(magic);
            if (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') {
                return file.path();
            }
        }
        return null;
    }

    /**
     * Computes the SHA-256 digest of a file of the image without loading it into memory.
     */
    private static String digest(RandomAccessFile handle, long imageSize, Iso9660File file)
            throws IOException, IsoFormatException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime", exception);
        }
        long offset = file.offset();
        long remaining = file.size();
        if (offset < 0 || remaining < 0 || offset > imageSize - remaining) {
            throw new IsoFormatException(IsoValidationStatus.CORRUPT_IMAGE,
                    "The boot executable of the selected disc points outside of the image.");
        }
        byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
        handle.seek(offset);
        while (remaining > 0) {
            int chunk = (int) Math.min(buffer.length, remaining);
            handle.readFully(buffer, 0, chunk);
            digest.update(buffer, 0, chunk);
            remaining -= chunk;
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Computes the SHA-1 digest of the whole image without loading it into memory.
     *
     * <p>The image is read in chunks of a fixed size, so the memory that is used does not depend
     * on the size of the image. The progress is reported after every chunk.
     *
     * @param handle the image to read
     * @param imageSize the size of the image in bytes
     * @param progress the listener that is told how far the reading has come
     * @return the digest of the image, in lower case hexadecimal
     * @throws IOException when the image cannot be read
     */
    private static String imageDigest(RandomAccessFile handle, long imageSize,
                                       IsoVerificationProgress progress) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available in this Java runtime", exception);
        }
        byte[] buffer = new byte[IMAGE_DIGEST_BUFFER_SIZE];
        handle.seek(0);
        long remaining = imageSize;
        while (remaining > 0) {
            int chunk = (int) Math.min(buffer.length, remaining);
            handle.readFully(buffer, 0, chunk);
            digest.update(buffer, 0, chunk);
            remaining -= chunk;
            progress.onProgress(imageSize - remaining, imageSize);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Builds a description of the revision a disc reports, for the messages shown to the player.
     */
    private static String describe(SystemCnf configuration) {
        StringBuilder details = new StringBuilder();
        if (configuration.version() != null) {
            details.append(", version ").append(configuration.version());
        }
        if (configuration.videoMode() != null) {
            details.append(", video mode ").append(configuration.videoMode());
        }
        return details.toString();
    }

    /**
     * Whether the path contains a {@code ..} segment.
     *
     * <p>A path that walks up out of its own directory is refused, so that a path can never point
     * at a file the player did not select.
     */
    private static boolean containsRelativeSegment(Path path) {
        for (int index = 0; index < path.getNameCount(); index++) {
            if ("..".equals(path.getName(index).toString())) {
                return true;
            }
        }
        return false;
    }

    private static Rac1IsoSelectionResult refused(IsoValidationStatus status, String message, Path path) {
        return new Rac1IsoSelectionResult(IsoValidationResult.failed(status, message, path));
    }
}
