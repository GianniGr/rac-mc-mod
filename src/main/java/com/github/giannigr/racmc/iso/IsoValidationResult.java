package com.github.giannigr.racmc.iso;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The result of validating a candidate Ratchet &amp; Clank disc image.
 *
 * @param status the outcome of the validation
 * @param revision the supported revision of the image, or {@code null} when the image was not
 *        accepted
 * @param productCode the product code that was read from the image, for example
 *        {@code SCES_509.16}, or {@code null} when none could be read
 * @param path the image that was validated, or {@code null} when no usable path was given
 * @param message a message that explains the outcome, suitable for showing to the player
 */
public record IsoValidationResult(IsoValidationStatus status,
                                  Rac1DiscRevision revision,
                                  String productCode,
                                  Path path,
                                  String message) {

    /**
     * Creates a result.
     *
     * @throws NullPointerException when the status or the message is {@code null}
     */
    public IsoValidationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Whether the image was accepted.
     *
     * @return {@code true} when the status is {@link IsoValidationStatus#SUPPORTED}
     */
    public boolean isSupported() {
        return status == IsoValidationStatus.SUPPORTED;
    }

    /**
     * Creates a successful result.
     *
     * @param revision the revision that was identified
     * @param productCode the product code of the disc
     * @param path the validated image
     * @param message the message that explains the outcome
     * @return the result
     */
    public static IsoValidationResult supported(Rac1DiscRevision revision,
                                                 String productCode,
                                                 Path path,
                                                 String message) {
        return new IsoValidationResult(IsoValidationStatus.SUPPORTED, revision, productCode, path, message);
    }

    /**
     * Creates a failed result.
     *
     * @param status the reason the image was refused
     * @param message the message that explains the outcome
     * @param path the image that was refused, may be {@code null}
     * @return the result
     */
    public static IsoValidationResult failed(IsoValidationStatus status, String message, Path path) {
        if (status == IsoValidationStatus.SUPPORTED) {
            throw new IllegalArgumentException("A supported result must be created with supported()");
        }
        return new IsoValidationResult(status, null, null, path, message);
    }
}
