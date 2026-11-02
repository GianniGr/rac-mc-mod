package com.github.giannigr.racmc.iso;

import java.util.Locale;
import java.util.Objects;

/**
 * The identity of one supported disc revision: the product code the disc carries, the boot
 * executable it must contain and the digests that the disc and that executable have to match.
 *
 * <p>A profile is the data the validator compares a candidate image against. The two profiles of
 * the shipped catalogue describe the retail PAL v2.00 and NTSC-U v1.00 discs; the tests build
 * additional profiles to exercise the validator with synthetic images.
 *
 * @param revision the revision the profile describes
 * @param productCode the product code of the release, which is also the name of its boot
 *        executable, for example {@code SCES_509.16}
 * @param executableName the name of the boot executable inside the ISO 9660 file system
 * @param executableSize the exact size of that executable in bytes
 * @param executableSha256 the SHA-256 digest of that executable, in lower case hexadecimal
 * @param imageSize the exact size of the whole disc image in bytes
 * @param imageSha1 the SHA-1 digest of the whole disc image, in lower case hexadecimal
 */
public record DiscProfile(Rac1DiscRevision revision,
                          String productCode,
                          String executableName,
                          long executableSize,
                          String executableSha256,
                          long imageSize,
                          String imageSha1) {

    /**
     * Creates a profile and checks that it is complete.
     *
     * @throws NullPointerException when a reference is {@code null}
     * @throws IllegalArgumentException when a value is empty, negative or not a digest
     */
    public DiscProfile {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(productCode, "productCode");
        Objects.requireNonNull(executableName, "executableName");
        Objects.requireNonNull(executableSha256, "executableSha256");
        Objects.requireNonNull(imageSha1, "imageSha1");
        if (productCode.isBlank()) {
            throw new IllegalArgumentException("The product code must not be blank");
        }
        if (executableName.isBlank()) {
            throw new IllegalArgumentException("The executable name must not be blank");
        }
        if (executableSize <= 0) {
            throw new IllegalArgumentException("The executable size must be positive");
        }
        if (imageSize <= 0) {
            throw new IllegalArgumentException("The image size must be positive");
        }
        if (executableSize > imageSize) {
            throw new IllegalArgumentException("The executable cannot be larger than the image");
        }
        if (!executableSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("The executable digest must be a SHA-256 digest");
        }
        if (!imageSha1.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("The image digest must be a SHA-1 digest");
        }
        productCode = productCode.toUpperCase(Locale.ROOT);
        executableName = executableName.toUpperCase(Locale.ROOT);
        executableSha256 = executableSha256.toLowerCase(Locale.ROOT);
        imageSha1 = imageSha1.toLowerCase(Locale.ROOT);
    }

    /**
     * Whether this profile describes the given product code, ignoring case.
     *
     * @param candidate the product code read from a candidate image
     * @return {@code true} when the candidate is the product code of this profile
     */
    public boolean matchesProductCode(String candidate) {
        return candidate != null && productCode.equalsIgnoreCase(candidate);
    }

    /**
     * Whether this profile describes an image of the given size.
     *
     * @param size the size of a candidate image in bytes
     * @return {@code true} when the candidate has the size of this profile
     */
    public boolean matchesImageSize(long size) {
        return imageSize == size;
    }
}
