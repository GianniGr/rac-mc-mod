package com.github.giannigr.racmc.iso;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The catalogue of the Ratchet &amp; Clank disc revisions the validator accepts.
 *
 * <p>The shipped catalogue describes the two retail revisions that were chosen for this mod: the
 * European PAL release v2.00 and the North American NTSC-U release v1.00. Every other revision of
 * the game is absent from the catalogue and is therefore refused.
 *
 * <p>The values are the public facts of the retail discs, taken from the OpenRAC manifest
 * {@code games/rac1/game.json} (the {@code boot} section of each version). No game data is bundled
 * with the mod; the values only describe what a legitimate disc image has to contain.
 */
public final class Rac1DiscCatalog {

    /** The product code of the European PAL release v2.00. */
    public static final String PAL_PRODUCT_CODE = "SCES_509.16";

    /** The product code of the North American NTSC-U release v1.00. */
    public static final String NTSC_U_PRODUCT_CODE = "SCUS_971.99";

    /** The ISO 9660 volume identifier the retail Ratchet &amp; Clank discs carry. */
    public static final String RAC1_VOLUME_IDENTIFIER = "RATCHETANDCLANK";

    private final List<DiscProfile> profiles;

    private Rac1DiscCatalog(List<DiscProfile> profiles) {
        this.profiles = List.copyOf(profiles);
    }

    /**
     * The catalogue of the supported retail revisions.
     *
     * @return the catalogue with the PAL v2.00 and NTSC-U v1.00 profiles
     */
    public static Rac1DiscCatalog supported() {
        return new Rac1DiscCatalog(List.of(
                new DiscProfile(
                        Rac1DiscRevision.PAL_V2_00,
                        PAL_PRODUCT_CODE,
                        PAL_PRODUCT_CODE,
                        1388100L,
                        "17f8a846329fd10798cb97847e9610e5eecd1867668420ed627b2292a96b122b"),
                new DiscProfile(
                        Rac1DiscRevision.NTSC_U_V1_00,
                        NTSC_U_PRODUCT_CODE,
                        NTSC_U_PRODUCT_CODE,
                        1383028L,
                        "e050581032e4bb3f20341307da5b69b76f1574910519155380ea771e55c3c0c9")));
    }

    /**
     * A catalogue with the given profiles, used by the tests to describe synthetic discs.
     *
     * @param profiles the profiles of the catalogue
     * @return the catalogue
     */
    public static Rac1DiscCatalog of(List<DiscProfile> profiles) {
        return new Rac1DiscCatalog(profiles);
    }

    /**
     * The profiles of this catalogue.
     *
     * @return the profiles, in the order they were added
     */
    public List<DiscProfile> profiles() {
        return profiles;
    }

    /**
     * Finds the profile that describes the given product code.
     *
     * @param productCode the product code read from a candidate image, may be {@code null}
     * @return the matching profile, or an empty optional when the revision is not supported
     */
    public Optional<DiscProfile> byProductCode(String productCode) {
        if (productCode == null) {
            return Optional.empty();
        }
        String normalized = productCode.trim().toUpperCase(Locale.ROOT);
        return profiles.stream().filter(profile -> profile.productCode().equals(normalized)).findFirst();
    }

    /**
     * A human readable list of the supported revisions, for messages shown to the player.
     *
     * @return the labels of the supported revisions, separated by {@code , }
     */
    public String supportedLabels() {
        return profiles.stream().map(profile -> profile.revision().label())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Rac1DiscCatalog catalog && Objects.equals(profiles, catalog.profiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profiles);
    }
}
