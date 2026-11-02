package com.github.giannigr.racmc.asset;

import java.util.ArrayList;
import java.util.List;

/**
 * The reader of the animation sequences of the game.
 *
 * <p>A sequence is a header of 0x1C bytes followed by the pointers of its keys. A key has a header
 * of 16 bytes followed by the rotation of every joint of the skeleton and the records of the
 * joints whose translation or scale differ from the rest pose.
 *
 * <p>The layout was measured by the ReRAC project (ISC); see
 * {@code docs/formats/moby_rac1.md} and {@code docs/plan/moby_animation.md} of that project.
 */
public final class MobySequenceReader {

    /** The size of the header of a sequence in bytes. */
    public static final int SEQUENCE_HEADER_SIZE = 0x1C;

    /** The size of the header of a key in bytes. */
    public static final int FRAME_HEADER_SIZE = 0x10;

    /** The number of ticks of the game in a second. */
    public static final int TICKS_PER_SECOND = 60;

    /**
     * The keys of a sequence.
     */
    public static final class Keys {

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

        Keys(float[] times, boolean loop, float[] rotations, int[] translationKeys,
             int[] translationJoints, float[] translationValues, int[] scaleKeys, int[] scaleJoints,
             float[] scaleValues, boolean[] scaleInherited) {
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
         * The times of the keys in seconds.
         *
         * @return the times
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
         * The rotation of every joint of every key.
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
    }

    private MobySequenceReader() {
    }

    /**
     * Reads the keys of a sequence.
     *
     * @param base the bytes the pointers of the keys are relative to
     * @param offset the offset of the sequence in the bytes
     * @param jointCount the number of joints of the skeleton
     * @return the keys of the sequence
     * @throws AssetFormatException when the sequence does not follow the expected layout
     */
    public static Keys read(byte[] base, int offset, int jointCount) throws AssetFormatException {
        if (offset < 0 || offset + SEQUENCE_HEADER_SIZE > base.length) {
            throw new AssetFormatException("An animation sequence of the game lies outside of its "
                    + "data.");
        }
        int frameCount = base[offset + 0x10] & 0xFF;
        float rateOverride = readFloat(base, offset + 0x18);
        if (frameCount == 0) {
            throw new AssetFormatException("An animation sequence of the game has no keys.");
        }
        if (offset + SEQUENCE_HEADER_SIZE + frameCount * 4 > base.length) {
            throw new AssetFormatException("The keys of an animation sequence of the game lie "
                    + "outside of its data.");
        }

        List<Float> times = new ArrayList<>();
        List<float[]> rotations = new ArrayList<>();
        List<Integer> translationKeys = new ArrayList<>();
        List<Integer> translationJoints = new ArrayList<>();
        List<Float> translationValues = new ArrayList<>();
        List<Integer> scaleKeys = new ArrayList<>();
        List<Integer> scaleJoints = new ArrayList<>();
        List<Float> scaleValues = new ArrayList<>();
        List<Boolean> scaleInherited = new ArrayList<>();

        float tick = 0.0f;
        boolean loop = false;
        float[][] rotationArray = new float[frameCount][];
        for (int key = 0; key < frameCount; key++) {
            int frame = readInt(base, offset + SEQUENCE_HEADER_SIZE + key * 4);
            if (frame < 0 || frame + FRAME_HEADER_SIZE > base.length) {
                throw new AssetFormatException("A key of an animation sequence of the game lies "
                        + "outside of its data.");
            }
            if ((frame & 0xF0000000) != 0) {
                throw new AssetFormatException("A key of an animation sequence of the game uses "
                        + "the special format, which the game of the disc does not have.");
            }
            float rate = readFloat(base, frame);
            int quadwords = readUnsignedShort(base, frame + 6);
            int quaternionBytes = readUnsignedShort(base, frame + 8);
            int scales = readUnsignedShort(base, frame + 0x0A);
            int translationAt = readUnsignedShort(base, frame + 0x0C);
            int translations = readUnsignedShort(base, frame + 0x0E);
            if (quaternionBytes != jointCount * 8
                    || translationAt != quaternionBytes + scales * 8
                    || quadwords != (translationAt + translations * 8 + 15) / 16) {
                throw new AssetFormatException("A key of an animation sequence of the game does not "
                        + "follow the expected layout.");
            }
            if (frame + FRAME_HEADER_SIZE + quadwords * 16 > base.length) {
                throw new AssetFormatException("The data of a key of an animation sequence of the "
                        + "game lies outside of its data.");
            }
            int payload = frame + FRAME_HEADER_SIZE;
            float[] rotation = new float[jointCount * 4];
            for (int joint = 0; joint < jointCount; joint++) {
                rotation[joint * 4] = readShort(base, payload + joint * 8) / 32768.0f;
                rotation[joint * 4 + 1] = readShort(base, payload + joint * 8 + 2) / 32768.0f;
                rotation[joint * 4 + 2] = readShort(base, payload + joint * 8 + 4) / 32768.0f;
                rotation[joint * 4 + 3] = readShort(base, payload + joint * 8 + 6) / 32768.0f;
            }
            for (int index = 0; index < scales; index++) {
                int at = payload + quaternionBytes + index * 8;
                int joint = base[at + 6] & 0xFF;
                int flags = base[at + 7] & 0xFF;
                if (joint >= jointCount || (flags & 0x7F) != 0) {
                    throw new AssetFormatException("A key of an animation sequence of the game "
                            + "holds a scale of a joint that does not exist.");
                }
                scaleKeys.add(key);
                scaleJoints.add(joint);
                scaleValues.add(readUnsignedShort(base, at) / 4096.0f);
                scaleValues.add(readUnsignedShort(base, at + 2) / 4096.0f);
                scaleValues.add(readUnsignedShort(base, at + 4) / 4096.0f);
                scaleInherited.add((flags & 0x80) != 0);
            }
            for (int index = 0; index < translations; index++) {
                int at = payload + translationAt + index * 8;
                int joint = base[at + 6] & 0xFF;
                if (joint >= jointCount) {
                    throw new AssetFormatException("A key of an animation sequence of the game "
                            + "holds a translation of a joint that does not exist.");
                }
                translationKeys.add(key);
                translationJoints.add(joint);
                translationValues.add(readShort(base, at) / 1024.0f);
                translationValues.add(readShort(base, at + 2) / 1024.0f);
                translationValues.add(readShort(base, at + 4) / 1024.0f);
            }

            if (!times.isEmpty() && times.get(times.size() - 1) == tick) {
                // The key has the same time as the key before it, so it is dropped. The records
                // of the channels belong to the keys, so the records of the dropped key are
                // dropped as well and the numbers of the keys of the records that follow are
                // made smaller by one.
                times.remove(times.size() - 1);
                rotations.remove(rotations.size() - 1);
                dropLast(translationKeys, translations);
                dropLast(translationJoints, translations);
                dropLast(translationValues, translations * 3);
                dropLast(scaleKeys, scales);
                dropLast(scaleJoints, scales);
                dropLast(scaleValues, scales * 3);
                dropLast(scaleInherited, scales);
                shiftKeys(translationKeys, key);
                shiftKeys(scaleKeys, key);
            }
            rotationArray[key] = rotation;
            times.add(tick);
            rotations.add(rotation);

            float step = rateOverride != 0.0f ? rateOverride : rate;
            if (key + 1 == frameCount) {
                if (frameCount > 1 && step > 0.0f && Float.isFinite(step)) {
                    times.add(tick + 1.0f / step);
                    rotations.add(rotationArray[0]);
                    loop = true;
                }
            } else if (!(step > 0.0f) || Float.isNaN(step)) {
                throw new AssetFormatException("A key of an animation sequence of the game has a "
                        + "rate that cannot be used.");
            } else if (Float.isFinite(step)) {
                tick += 1.0f / step;
            }
        }

        float[] allTimes = new float[times.size()];
        for (int index = 0; index < allTimes.length; index++) {
            allTimes[index] = times.get(index) / TICKS_PER_SECOND;
        }
        float[] allRotations = new float[rotations.size() * jointCount * 4];
        for (int index = 0; index < rotations.size(); index++) {
            System.arraycopy(rotations.get(index), 0, allRotations, index * jointCount * 4,
                    jointCount * 4);
        }
        int[] allTranslationKeys = new int[translationKeys.size()];
        int[] allTranslationJoints = new int[translationJoints.size()];
        float[] allTranslationValues = new float[translationValues.size()];
        for (int index = 0; index < allTranslationKeys.length; index++) {
            allTranslationKeys[index] = translationKeys.get(index);
            allTranslationJoints[index] = translationJoints.get(index);
        }
        allTranslationValues = new float[translationValues.size()];
        for (int index = 0; index < allTranslationValues.length; index++) {
            allTranslationValues[index] = translationValues.get(index);
        }
        int[] allScaleKeys = new int[scaleKeys.size()];
        int[] allScaleJoints = new int[scaleJoints.size()];
        float[] allScaleValues = new float[scaleValues.size()];
        boolean[] allScaleInherited = new boolean[scaleInherited.size()];
        for (int index = 0; index < allScaleKeys.length; index++) {
            allScaleKeys[index] = scaleKeys.get(index);
            allScaleJoints[index] = scaleJoints.get(index);
            allScaleInherited[index] = scaleInherited.get(index);
        }
        for (int index = 0; index < allScaleValues.length; index++) {
            allScaleValues[index] = scaleValues.get(index);
        }

        return new Keys(allTimes, loop, allRotations, allTranslationKeys, allTranslationJoints,
                allTranslationValues, allScaleKeys, allScaleJoints, allScaleValues,
                allScaleInherited);
    }

    /**
     * Drops the last records of a list of the records of a channel.
     *
     * @param records the records
     * @param count the number of the records that are dropped
     */
    private static void dropLast(List<?> records, int count) {
        for (int index = 0; index < count && !records.isEmpty(); index++) {
            records.remove(records.size() - 1);
        }
    }

    /**
     * Makes the numbers of the keys of the records that follow a dropped key smaller by one.
     *
     * @param records the records with their key
     * @param key the number of the dropped key
     */
    private static void shiftKeys(List<Integer> records, int key) {
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index) > key) {
                records.set(index, records.get(index) - 1);
            }
        }
    }

    private static int readShort(byte[] data, int offset) {
        return (short) ((data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8));
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static float readFloat(byte[] data, int offset) {
        return Float.intBitsToFloat(readInt(data, offset));
    }
}
