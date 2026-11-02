package com.github.giannigr.racmc.iso;

/**
 * The outcome of validating a candidate Ratchet &amp; Clank disc image.
 *
 * <p>Only {@link #SUPPORTED} allows an image to be used. Every other value describes why the
 * image was refused, so the player gets an actionable message instead of a silent failure.
 */
public enum IsoValidationStatus {

    /** The image is a supported Ratchet &amp; Clank disc and passed every check. */
    SUPPORTED,

    /** The image is a Ratchet &amp; Clank disc, but of a revision this mod does not support. */
    UNSUPPORTED_REVISION,

    /** The image is a readable ISO 9660 disc, but not a Ratchet &amp; Clank disc. */
    UNSUPPORTED_IMAGE,

    /** The image looks like a supported disc but is damaged, incomplete or modified. */
    CORRUPT_IMAGE,

    /** The file is not an ISO 9660 image at all. */
    NOT_AN_ISO,

    /** The file uses a disc image container that this mod does not support, such as CHD. */
    UNSUPPORTED_CONTAINER,

    /** The path that was handed in is not usable. */
    INVALID_PATH,

    /** The file could not be read. */
    IO_ERROR
}
