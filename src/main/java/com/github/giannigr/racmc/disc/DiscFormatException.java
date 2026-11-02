package com.github.giannigr.racmc.disc;

/**
 * The failure of the reader of the tables of a disc.
 *
 * <p>The exception is thrown when a table of the disc is damaged or does not follow the expected
 * layout. It carries a message that says what is wrong, so that the player can be told what
 * happened.
 */
public class DiscFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the description of the failure
     */
    public DiscFormatException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message the description of the failure
     * @param cause the failure that caused this one
     */
    public DiscFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
