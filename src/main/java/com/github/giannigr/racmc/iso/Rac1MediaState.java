package com.github.giannigr.racmc.iso;

/**
 * The state of the disc image the mod is configured to use.
 *
 * <p>The state decides whether the game may be entered. Only {@link #VALID} allows it.
 */
public enum Rac1MediaState {

    /** No disc image is configured. */
    UNCONFIGURED,

    /** The configured disc image is being verified. The game may not be entered yet. */
    CHECKING,

    /** The configured disc image was verified and may be used. */
    VALID,

    /** The configured disc image is missing, changed or not a supported disc image. */
    INVALID
}
