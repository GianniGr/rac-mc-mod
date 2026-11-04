package com.github.giannigr.racmc.asset;

/**
 * An animated asset that was converted from the disc of the game.
 *
 * <p>The asset holds the geometry of a character of the game, the skeleton it is animated with,
 * the animation sequences it has and the textures it uses.
 */
public final class Rac1AnimatedAsset {

    /** The number of the class the asset was read from. */
    private final int classNumber;

    /** The name of the asset. */
    private final String name;

    /** The geometry of the asset. */
    private final Rac1SkinnedMesh mesh;

    /** The skeleton of the asset. */
    private final Rac1Skeleton skeleton;

    /** The animation sequences of the asset. */
    private final Rac1Animation[] animations;

    /** The textures of the asset, one per material. */
    private final Rac1Texture[] textures;

    /**
     * Creates an asset.
     *
     * @param classNumber the number of the class
     * @param name the name of the asset
     * @param mesh the geometry of the asset
     * @param skeleton the skeleton of the asset
     * @param animations the animation sequences of the asset
     * @param textures the textures of the asset
     */
    public Rac1AnimatedAsset(int classNumber, String name, Rac1SkinnedMesh mesh,
                              Rac1Skeleton skeleton, Rac1Animation[] animations,
                              Rac1Texture[] textures) {
        this.classNumber = classNumber;
        this.name = name;
        this.mesh = mesh;
        this.skeleton = skeleton;
        this.animations = animations;
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
    public Rac1SkinnedMesh mesh() {
        return mesh;
    }

    /**
     * The skeleton of the asset.
     *
     * @return the skeleton
     */
    public Rac1Skeleton skeleton() {
        return skeleton;
    }

    /**
     * The animation sequences of the asset.
     *
     * @return the sequences
     */
    public Rac1Animation[] animations() {
        return animations;
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
