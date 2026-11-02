package com.github.giannigr.racmc.iso;

import java.io.Closeable;
import java.io.IOException;

/**
 * The outcome of inspecting a candidate disc image.
 *
 * <p>When the image is supported, the opened image is part of the result and has to be closed by
 * the caller. When the image was refused, no image is opened and the result only explains why.
 *
 * @param result the validation result
 * @param image the opened image, or {@code null} when the image was refused
 */
public record Rac1IsoSelectionResult(IsoValidationResult result, Rac1DiscImage image) implements Closeable {

    /**
     * Creates a result for a refused image.
     *
     * @param result the validation result
     */
    public Rac1IsoSelectionResult(IsoValidationResult result) {
        this(result, null);
    }

    /**
     * Whether the image was accepted.
     *
     * @return {@code true} when the status is {@link IsoValidationStatus#SUPPORTED}
     */
    public boolean isSupported() {
        return result.isSupported();
    }

    @Override
    public void close() throws IOException {
        if (image != null) {
            image.close();
        }
    }
}
