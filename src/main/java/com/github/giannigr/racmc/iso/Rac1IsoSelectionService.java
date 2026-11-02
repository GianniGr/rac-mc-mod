package com.github.giannigr.racmc.iso;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps the disc image the player selected.
 *
 * <p>The service is the single place where a chosen image is validated and remembered. It is
 * deliberately free of any game specific code, so that it can be used by a screen, by a command or
 * by a test without a running game.
 *
 * <p>Selecting a new image closes the previous one, so that at most one image is open at a time.
 */
public final class Rac1IsoSelectionService implements Closeable {

    private final Rac1DiscValidator validator;

    private Rac1DiscImage selectedImage;
    private Path selectedPath;

    /**
     * Creates a service that uses the supported retail revisions.
     */
    public Rac1IsoSelectionService() {
        this(new Rac1DiscValidator());
    }

    /**
     * Creates a service that uses the given validator.
     *
     * @param validator the validator that checks the candidate images
     */
    public Rac1IsoSelectionService(Rac1DiscValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Validates a candidate image and keeps it when it is supported.
     *
     * @param path the file the player chose, may be {@code null}
     * @return the outcome of the check
     */
    public synchronized IsoValidationResult select(Path path) {
        clear();
        try (Rac1IsoSelectionResult outcome = validator.inspect(path)) {
            if (!outcome.isSupported()) {
                return outcome.result();
            }
            selectedImage = outcome.image();
            selectedPath = outcome.result().path();
            return outcome.result();
        } catch (IOException exception) {
            return IsoValidationResult.failed(IsoValidationStatus.IO_ERROR,
                    "The selected file could not be closed: " + exception.getMessage(), path);
        }
    }

    /**
     * The image that was selected last.
     *
     * @return the image, or an empty optional when no supported image is selected
     */
    public synchronized Optional<Rac1DiscImage> selectedImage() {
        return Optional.ofNullable(selectedImage);
    }

    /**
     * The revision of the image that was selected last.
     *
     * @return the revision, or an empty optional when no supported image is selected
     */
    public synchronized Optional<Rac1DiscRevision> selectedRevision() {
        return Optional.ofNullable(selectedImage).map(Rac1DiscImage::revision);
    }

    /**
     * The file that was selected last.
     *
     * @return the path, or an empty optional when no supported image is selected
     */
    public synchronized Optional<Path> selectedPath() {
        return Optional.ofNullable(selectedPath);
    }

    /**
     * Forgets the selected image and closes it.
     */
    public synchronized void clear() {
        if (selectedImage != null) {
            try {
                selectedImage.close();
            } catch (IOException ignored) {
                // The image is dropped anyway.
            }
            selectedImage = null;
        }
        selectedPath = null;
    }

    @Override
    public synchronized void close() {
        clear();
    }
}
