package com.github.giannigr.racmc.asset;

import java.util.Arrays;

/**
 * The skeleton of an animated class of the game.
 *
 * <p>The skeleton holds one inverse bind matrix for every joint and the parent of every joint. The
 * matrices are the ones of the disc, read as columns: the game skins a vertex with the pose of
 * its joint and the inverse bind matrix of that joint.
 */
public final class Rac1Skeleton {

    /** The number of joints of the skeleton. */
    private final int jointCount;

    /** The inverse bind matrices, sixteen floats each, in the order of the columns. */
    private final float[] inverseBind;

    /** The parent of every joint, {@code -1} for the root. */
    private final int[] parents;

    /** The rest translation of every joint, three floats each. */
    private final float[] restTranslations;

    /**
     * Creates a skeleton.
     *
     * @param inverseBind the inverse bind matrices, sixteen floats each
     * @param parents the parent of every joint
     * @param restTranslations the rest translation of every joint, three floats each
     */
    public Rac1Skeleton(float[] inverseBind, int[] parents, float[] restTranslations) {
        this.inverseBind = inverseBind;
        this.parents = parents;
        this.restTranslations = restTranslations;
        this.jointCount = parents.length;
    }

    /**
     * The number of joints of the skeleton.
     *
     * @return the number of joints
     */
    public int jointCount() {
        return jointCount;
    }

    /**
     * The inverse bind matrices.
     *
     * @return the matrices, sixteen floats each
     */
    public float[] inverseBind() {
        return inverseBind;
    }

    /**
     * The parent of every joint.
     *
     * @return the parents, {@code -1} for the root
     */
    public int[] parents() {
        return parents;
    }

    /**
     * The rest translation of every joint.
     *
     * @return the translations, three floats each
     */
    public float[] restTranslations() {
        return restTranslations;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Rac1Skeleton skeleton
                && Arrays.equals(inverseBind, skeleton.inverseBind)
                && Arrays.equals(parents, skeleton.parents);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(inverseBind) + Arrays.hashCode(parents);
    }
}
