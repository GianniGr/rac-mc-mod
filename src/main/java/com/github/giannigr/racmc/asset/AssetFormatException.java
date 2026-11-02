package com.github.giannigr.racmc.asset;

/**
 * The failure of the reader of an asset of the game.
 *
 * <p>The exception is thrown when the data of an asset is damaged or does not follow the format of
 * the game. It carries a message that says what is wrong, so that the player can be told what
 * happened.
 */
public class AssetFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the description of the failure
     */
    public AssetFormatException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message the description of the failure
     * @param cause the failure that caused this one
     */
    public AssetFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
