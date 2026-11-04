package com.github.giannigr.racmc.wad;

/**
 * The failure of the decoder of a compressed stream.
 *
 * <p>The exception is thrown when a stream of the disc is damaged or does not follow the format.
 * It carries a message that says what is wrong, so that the player can be told what happened.
 */
public class WadFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the description of the failure
     */
    public WadFormatException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message the description of the failure
     * @param cause the failure that caused this one
     */
    public WadFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
