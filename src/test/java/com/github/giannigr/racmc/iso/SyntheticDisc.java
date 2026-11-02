package com.github.giannigr.racmc.iso;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * A synthetic disc image with the profile that describes it.
 *
 * <p>The helper builds an ISO 9660 image that holds a {@code SYSTEM.CNF} file and a boot
 * executable, and the profile that such an image has to match to be accepted. No game data is
 * used anywhere.
 */
final class SyntheticDisc {

    private final Rac1DiscRevision revision;
    private final String productCode;
    private final byte[] executable;
    private final byte[] image;

    private SyntheticDisc(Rac1DiscRevision revision, String productCode, byte[] executable, byte[] image) {
        this.revision = revision;
        this.productCode = productCode;
        this.executable = executable;
        this.image = image;
    }

    /**
     * Builds a synthetic disc of the given revision.
     *
     * @param revision the revision the image pretends to be
     * @param productCode the product code the image carries
     * @param executable the content of the boot executable
     * @return the synthetic disc
     */
    static SyntheticDisc create(Rac1DiscRevision revision, String productCode, byte[] executable) {
        return create(revision, productCode, executable, true);
    }

    /**
     * Builds a synthetic disc whose {@code SYSTEM.CNF} file does not name the boot executable.
     *
     * @param revision the revision the image pretends to be
     * @param productCode the product code the image carries
     * @param executable the content of the boot executable
     * @return the synthetic disc
     */
    static SyntheticDisc createWithoutBootLine(Rac1DiscRevision revision, String productCode,
                                               byte[] executable) {
        return create(revision, productCode, executable, false);
    }

    private static SyntheticDisc create(Rac1DiscRevision revision, String productCode, byte[] executable,
                                        boolean withBootLine) {
        byte[] image = SyntheticIso.create()
                .file("SYSTEM.CNF", SyntheticIso.systemCnf(withBootLine ? productCode : null,
                        version(revision), videoMode(revision)))
                .file(productCode, executable)
                .build();
        return new SyntheticDisc(revision, productCode, executable, image);
    }

    private static String version(Rac1DiscRevision revision) {
        return revision.version();
    }

    private static String videoMode(Rac1DiscRevision revision) {
        return revision == Rac1DiscRevision.PAL_V2_00 ? "PAL" : "NTSC";
    }

    /**
     * The content of the boot executable.
     *
     * @return the bytes of the executable
     */
    byte[] executable() {
        return executable;
    }

    /**
     * The content of the image.
     *
     * @return the bytes of the image
     */
    byte[] image() {
        return image;
    }

    /**
     * The product code the image carries.
     *
     * @return the product code
     */
    String productCode() {
        return productCode;
    }

    /**
     * The profile that the image has to match.
     *
     * @return the profile of the image
     */
    DiscProfile profile() {
        return new DiscProfile(revision, productCode, productCode,
                executable.length, sha256(executable),
                image.length, sha1(image));
    }

    /**
     * A catalogue that holds only the profile of this image.
     *
     * @return the catalogue
     */
    Rac1DiscCatalog catalog() {
        return Rac1DiscCatalog.of(List.of(profile()));
    }

    /**
     * A validator that accepts this image and nothing else.
     *
     * @return the validator
     */
    Rac1DiscValidator validator() {
        return new Rac1DiscValidator(catalog());
    }

    static String sha256(byte[] content) {
        return digest("SHA-256", content);
    }

    static String sha1(byte[] content) {
        return digest("SHA-1", content);
    }

    private static String digest(String algorithm, byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(algorithm + " is not available", exception);
        }
    }
}
