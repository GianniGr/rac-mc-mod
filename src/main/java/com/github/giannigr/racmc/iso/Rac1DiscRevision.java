package com.github.giannigr.racmc.iso;

/**
 * The Ratchet &amp; Clank (2002, PlayStation 2) disc revisions this mod can import.
 *
 * <p>Only the two revisions listed here are accepted. Every other revision of the game - the
 * PAL v1.00 release, the Japanese release, the demo discs and every later sequel - is rejected
 * with a clear message instead of being imported.
 *
 * <p>The product code of a revision is the file name of its boot executable, which is what a
 * PlayStation 2 disc stores in {@code SYSTEM.CNF} and what identifies the release of the game.
 */
public enum Rac1DiscRevision {

    /** The European PAL release, revision v2.00 (product code {@code SCES_509.16}). */
    PAL_V2_00("PAL", "2.00", "PAL v2.00"),

    /** The North American NTSC-U release, revision v1.00 (product code {@code SCUS_971.99}). */
    NTSC_U_V1_00("NTSC-U", "1.00", "NTSC-U v1.00");

    private final String region;
    private final String version;
    private final String label;

    Rac1DiscRevision(String region, String version, String label) {
        this.region = region;
        this.version = version;
        this.label = label;
    }

    /**
     * The region of the disc, {@code PAL} or {@code NTSC-U}.
     *
     * @return the region name
     */
    public String region() {
        return region;
    }

    /**
     * The revision of the disc as the game reports it in {@code SYSTEM.CNF}, for example
     * {@code 2.00}.
     *
     * @return the revision string
     */
    public String version() {
        return version;
    }

    /**
     * The human readable name of the revision, for example {@code PAL v2.00}.
     *
     * @return the label of the revision
     */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
