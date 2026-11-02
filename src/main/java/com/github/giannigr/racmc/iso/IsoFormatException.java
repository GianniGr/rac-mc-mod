package com.github.giannigr.racmc.iso;

/**
 * Thrown when a disc image does not follow the structure the validator expects.
 *
 * <p>The exception carries the validation status that the failure maps to, so the validator can
 * tell a file that is not an ISO 9660 image apart from a file that claims to be one but is
 * damaged.
 */
public final class IsoFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    private final IsoValidationStatus status;

    /**
     * Creates an exception.
     *
     * @param status the validation status the failure maps to
     * @param message the message that explains the failure
     */
    public IsoFormatException(IsoValidationStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Creates an exception for a damaged image.
     *
     * @param message the message that explains the failure
     */
    public IsoFormatException(String message) {
        this(IsoValidationStatus.CORRUPT_IMAGE, message);
    }

    /**
     * The validation status the failure maps to.
     *
     * @return the status
     */
    public IsoValidationStatus status() {
        return status;
    }
}
