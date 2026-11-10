package com.github.giannigr.racmc.asset;

import java.util.Objects;

/**
 * A crate of the game that was converted from the disc.
 *
 * <p>The asset holds the geometry of the crate of the game, the textures it uses, the key the
 * crate is known by inside the mod and the number of the class it was read from.
 *
 * <p>The crate is a static object: it has no skeleton and no animation of its own, so only the
 * geometry and the textures are needed to draw it.
 */
public final class Rac1CrateAsset {

    /** The key of the crate inside the mod. */
    private final String key;

    /** The number of the class the asset was read from. */
    private final int classNumber;

    /** The geometry of the crate. */
    private final Rac1Mesh mesh;

    /** The textures of the crate, one per material. */
    private final Rac1Texture[] textures;

    /**
     * Creates an asset of a crate.
     *
     * @param key the key of the crate inside the mod
     * @param classNumber the number of the class the asset was read from
     * @param mesh the geometry of the crate
     * @param textures the textures of the crate
     */
    public Rac1CrateAsset(String key, int classNumber, Rac1Mesh mesh, Rac1Texture[] textures) {
        this.key = Objects.requireNonNull(key, "key");
        this.classNumber = classNumber;
        this.mesh = Objects.requireNonNull(mesh, "mesh");
        this.textures = Objects.requireNonNull(textures, "textures");
    }

    /**
     * The key of the crate inside the mod.
     *
     * @return the key
     */
    public String key() {
        return key;
    }

    /**
     * The number of the class the asset was read from.
     *
     * @return the number
     */
    public int classNumber() {
        return classNumber;
    }

    /**
     * The geometry of the crate.
     *
     * @return the mesh
     */
    public Rac1Mesh mesh() {
        return mesh;
    }

    /**
     * The textures of the crate, one per material.
     *
     * @return the textures
     */
    public Rac1Texture[] textures() {
        return textures;
    }
}
