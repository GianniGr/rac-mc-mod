package com.github.giannigr.racmc.asset;

import java.util.Arrays;

/**
 * Lossless timing metadata from original sequence/key headers. Not an action clock.
 * Header time is in sixteenths of original gameplay key-time, NOT seconds or key index.
 */
public record Rac1SourceTiming(int sequenceRateBits, int[] headerTimes16,
                               int[] keyRateBits, float[] sourceSeconds,
                               int[] sampledSourceKeys) {
    public Rac1SourceTiming {
        int count = headerTimes16.length;
        if (count > 255 || keyRateBits.length != count || sourceSeconds.length != count
                || (count == 0 && sampledSourceKeys.length != 0)) {
            throw new IllegalArgumentException("Invalid original timing counts.");
        }
        for (int i = 0; i < count; i++) {
            if (headerTimes16[i] < 0 || headerTimes16[i] > 65535
                    || !Float.isFinite(sourceSeconds[i]) || sourceSeconds[i] < 0
                    || (i > 0 && sourceSeconds[i] < sourceSeconds[i - 1])) {
                throw new IllegalArgumentException("Invalid original key metadata.");
            }
        }
        for (int i = 0; i < sampledSourceKeys.length; i++) {
            int key = sampledSourceKeys[i];
            if (key < -1 || key >= count || (key == -1 && i != sampledSourceKeys.length - 1)) {
                throw new IllegalArgumentException("Invalid sampled/source key mapping.");
            }
        }
        headerTimes16 = headerTimes16.clone();
        keyRateBits = keyRateBits.clone();
        sourceSeconds = sourceSeconds.clone();
        sampledSourceKeys = sampledSourceKeys.clone();
    }

    public static Rac1SourceTiming unavailable() {
        return new Rac1SourceTiming(0, new int[0], new int[0], new float[0], new int[0]);
    }

    public boolean available() {
        return headerTimes16.length > 0;
    }

    /** -1 denotes the converter's synthetic loop endpoint, not an original key. */
    @Override public int[] sampledSourceKeys() { return sampledSourceKeys.clone(); }
    @Override public int[] headerTimes16() { return headerTimes16.clone(); }
    @Override public int[] keyRateBits() { return keyRateBits.clone(); }
    @Override public float[] sourceSeconds() { return sourceSeconds.clone(); }

    @Override public boolean equals(Object other) {
        return other instanceof Rac1SourceTiming timing
                && sequenceRateBits == timing.sequenceRateBits
                && Arrays.equals(headerTimes16, timing.headerTimes16)
                && Arrays.equals(keyRateBits, timing.keyRateBits)
                && Arrays.equals(sourceSeconds, timing.sourceSeconds)
                && Arrays.equals(sampledSourceKeys, timing.sampledSourceKeys);
    }

    @Override public int hashCode() {
        int result = sequenceRateBits;
        result = 31 * result + Arrays.hashCode(headerTimes16);
        result = 31 * result + Arrays.hashCode(keyRateBits);
        result = 31 * result + Arrays.hashCode(sourceSeconds);
        return 31 * result + Arrays.hashCode(sampledSourceKeys);
    }
}
