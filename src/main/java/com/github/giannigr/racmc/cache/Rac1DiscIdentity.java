package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.iso.DiscProfile;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;

/**
 * The identity of the disc the assets of the game come from.
 *
 * <p>The identity names the release of the game, the product code of the disc and the digest of
 * the whole disc image. All three are public facts of the retail releases, so the identity of a
 * cache says which disc it belongs to without giving away anything about the file of the player.
 */
public final class Rac1DiscIdentity {

    private Rac1DiscIdentity() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The identity of a disc.
     *
     * @param revision the revision of the release
     * @param profile the profile that describes the release
     * @return the identity
     */
    public static String key(Rac1DiscRevision revision, DiscProfile profile) {
        return revision.label() + "|" + profile.productCode() + "|" + profile.imageSha1();
    }
}
