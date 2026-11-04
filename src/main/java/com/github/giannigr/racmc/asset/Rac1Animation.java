package com.github.giannigr.racmc.asset;

import java.util.Arrays;

/**
 * An animation sequence of the game.
 *
 * <p>A sequence is a list of keys. Every key holds the rotation of every joint of the skeleton.
 * The translation and the scale of a joint are only stored when they differ from the rest pose,
 * so they are kept in three sparse lists.
 *
 * <p>The rotations are the quaternions of the disc: four components in the order x, y, z and w.
 */
public final class Rac1Animation {

    /** The name of the sequence. */
    private final String name;

    /** The times of the keys in seconds. */
    private final float[] times;

    /** Whether the sequence repeats itself. */
    private final boolean loop;

    /** The rotation of every joint of every key, four floats each. */
    private final float[] rotations;

    /** The key of every stored translation. */
    private final int[] translationKeys;

    /** The joint of every stored translation. */
    private final int[] translationJoints;

    /** The value of every stored translation, three floats each. */
    private final float[] translationValues;

    /** The key of every stored scale. */
    private final int[] scaleKeys;

    /** The joint of every stored scale. */
    private final int[] scaleJoints;

    /** The value of every stored scale, three floats each. */
    private final float[] scaleValues;

    /** Whether the scale of a stored scale reaches the children of its joint. */
    private final boolean[] scaleInherited;

    /**
     * Creates a sequence.
     *
     * @param name the name of the sequence
     * @param times the times of the keys in seconds
     * @param loop whether the sequence repeats itself
     * @param rotations the rotation of every joint of every key
     * @param translationKeys the key of every stored translation
     * @param translationJoints the joint of every stored translation
     * @param translationValues the value of every stored translation
     * @param scaleKeys the key of every stored scale
     * @param scaleJoints the joint of every stored scale
     * @param scaleValues the value of every stored scale
     * @param scaleInherited whether the scale of a stored scale reaches the children of its joint
     */
    public Rac1Animation(String name, float[] times, boolean loop, float[] rotations,
                          int[] translationKeys, int[] translationJoints, float[] translationValues,
                          int[] scaleKeys, int[] scaleJoints, float[] scaleValues,
                          boolean[] scaleInherited) {
        this.name = name;
        this.times = times;
        this.loop = loop;
        this.rotations = rotations;
        this.translationKeys = translationKeys;
        this.translationJoints = translationJoints;
        this.translationValues = translationValues;
        this.scaleKeys = scaleKeys;
        this.scaleJoints = scaleJoints;
        this.scaleValues = scaleValues;
        this.scaleInherited = scaleInherited;
    }

    /**
     * The name of the sequence.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * The number of keys of the sequence.
     *
     * @return the number of keys
     */
    public int keyCount() {
        return times.length;
    }

    /**
     * The times of the keys.
     *
     * @return the times in seconds
     */
    public float[] times() {
        return times;
    }

    /**
     * Whether the sequence repeats itself.
     *
     * @return {@code true} when the sequence loops
     */
    public boolean loop() {
        return loop;
    }

    /**
     * The rotations of the joints of the keys.
     *
     * @return the rotations, four floats per joint and key
     */
    public float[] rotations() {
        return rotations;
    }

    /**
     * The keys of the stored translations.
     *
     * @return the keys
     */
    public int[] translationKeys() {
        return translationKeys;
    }

    /**
     * The joints of the stored translations.
     *
     * @return the joints
     */
    public int[] translationJoints() {
        return translationJoints;
    }

    /**
     * The values of the stored translations.
     *
     * @return the values, three floats each
     */
    public float[] translationValues() {
        return translationValues;
    }

    /**
     * The keys of the stored scales.
     *
     * @return the keys
     */
    public int[] scaleKeys() {
        return scaleKeys;
    }

    /**
     * The joints of the stored scales.
     *
     * @return the joints
     */
    public int[] scaleJoints() {
        return scaleJoints;
    }

    /**
     * The values of the stored scales.
     *
     * @return the values, three floats each
     */
    public float[] scaleValues() {
        return scaleValues;
    }

    /**
     * Whether the stored scales reach the children of their joint.
     *
     * @return one flag per stored scale
     */
    public boolean[] scaleInherited() {
        return scaleInherited;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Rac1Animation animation && name.equals(animation.name)
                && Arrays.equals(times, animation.times) && loop == animation.loop
                && Arrays.equals(rotations, animation.rotations)
                && Arrays.equals(translationKeys, animation.translationKeys)
                && Arrays.equals(translationJoints, animation.translationJoints)
                && Arrays.equals(translationValues, animation.translationValues)
                && Arrays.equals(scaleKeys, animation.scaleKeys)
                && Arrays.equals(scaleJoints, animation.scaleJoints)
                && Arrays.equals(scaleValues, animation.scaleValues)
                && Arrays.equals(scaleInherited, animation.scaleInherited);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + Arrays.hashCode(times);
        result = 31 * result + Arrays.hashCode(rotations);
        result = 31 * result + Arrays.hashCode(translationValues);
        result = 31 * result + Arrays.hashCode(scaleValues);
        return result;
    }
}
