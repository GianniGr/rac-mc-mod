package com.github.giannigr.racmc.serverassets;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import java.util.Optional;

/** Read-only by contract: original local source data, not action state or client-supplied poses. */
public record Rac1WrenchData(String discKey, Rac1AnimatedAsset hero, Rac1AnimatedAsset wrench) {
    public Rac1WrenchData {
        if (hero == null || hero.classNumber() != 0 || wrench == null || wrench.classNumber() != 71
                || discKey == null || discKey.isBlank()
                || hero.attachmentChain(0).length == 0 || wrench.attachmentChain(1).length == 0) {
            throw new IllegalArgumentException("Missing original hero/wrench attachment data.");
        }
        for (int sequence : new int[] { 26, 43 }) {
            require(hero, "ratchet_sequence_" + sequence, sequence == 26 ? 27 : 5);
        }
        for (int sequence : new int[] { 1, 6, 10 }) {
            require(wrench, "sequence_" + sequence, 1);
        }
        for (var asset : new Rac1AnimatedAsset[] { hero, wrench }) {
            finite(asset.skeleton().restTranslations());
            finite(asset.skeleton().inverseBind());
            int[] parents = asset.skeleton().parents();
            for (int joint = 0; joint < parents.length; joint++) {
                if (parents[joint] < -1 || parents[joint] >= joint) {
                    throw new IllegalArgumentException("Invalid original skeleton hierarchy.");
                }
            }
        }
    }

    public static Rac1WrenchData from(Rac1CacheContent content) {
        return new Rac1WrenchData(content.discKey(), content.animatedAsset(), content.wrench());
    }

    public Optional<Rac1Animation> heroSequence(int number) {
        return sequence(hero, "ratchet_sequence_" + number);
    }

    public Optional<Rac1Animation> wrenchSequence(int number) {
        return sequence(wrench, "sequence_" + number);
    }

    private static Optional<Rac1Animation> sequence(Rac1AnimatedAsset asset, String name) {
        for (var sequence : asset.animations()) {
            if (sequence.name().equals(name)) {
                return Optional.of(sequence);
            }
        }
        return Optional.empty();
    }

    private static void require(Rac1AnimatedAsset asset, String name, int minimumSourceKeys) {
        var clip = sequence(asset, name).orElseThrow(
                () -> new IllegalArgumentException("Missing original clip " + name));
        if (!clip.sourceTiming().available()
                || clip.sourceTiming().headerTimes16().length < minimumSourceKeys
                || clip.sourceTiming().sampledSourceKeys().length != clip.keyCount()) {
            throw new IllegalArgumentException("Missing original clock/key mapping for " + name);
        }
        finite(clip.rotations());
        finite(clip.translationValues());
        finite(clip.scaleValues());
        finite(clip.times());
    }

    private static void finite(float[] values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Nonfinite original pose data.");
            }
        }
    }
}
