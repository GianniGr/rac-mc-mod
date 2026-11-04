package com.github.giannigr.racmc.asset;

/**
 * A static asset that was converted from the disc of the game.
 *
 * <p>The asset holds the geometry of a piece of scenery of the game, the textures it uses and the
 * number of the class it was read from.
 */
public final class Rac1StaticAsset {

    /** The number of the class the asset was read from. */
    private final int classNumber;

    /** The name of the asset. */
    private final String name;

    /** The geometry of the asset. */
    private final Rac1Mesh mesh;

    /** The textures of the asset, one per material. */
    private final Rac1Texture[] textures;

    /**
     * Creates an asset.
     *
     * @param classNumber the number of the class
     * @param name the name of the asset
     * @param mesh the geometry of the asset
     * @param textures the textures of the asset
     */
    public Rac1StaticAsset(int classNumber, String name, Rac1Mesh mesh, Rac1Texture[] textures) {
        this.classNumber = classNumber;
        this.name = name;
        this.mesh = mesh;
        this.textures = textures;
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
     * The name of the asset.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * The geometry of the asset.
     *
     * @return the mesh
     */
    public Rac1Mesh mesh() {
        return mesh;
    }

    /**
     * The textures of the asset.
     *
     * @return the textures, one per material
     */
    public Rac1Texture[] textures() {
        return textures;
    }
}
