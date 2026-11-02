package com.github.giannigr.racmc.iso;

/**
 * The progress of a running verification.
 *
 * <p>The verification of a disc image reads the whole image, which takes a moment. The listener
 * is called from the worker thread that does the work, so an implementation has to be thread safe
 * and should not do any expensive work.
 */
@FunctionalInterface
public interface IsoVerificationProgress {

    /**
     * Reports how much of the work is done.
     *
     * @param bytesRead the number of bytes that were read so far
     * @param totalBytes the total number of bytes to read
     */
    void onProgress(long bytesRead, long totalBytes);

    /**
     * A listener that ignores the progress.
     */
    IsoVerificationProgress NONE = (bytesRead, totalBytes) -> {
    };
}
