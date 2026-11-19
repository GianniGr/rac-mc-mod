package com.github.giannigr.racmc.asset.clank;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.cache.Rac1CacheCodec;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.Rac1DiscCatalog;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Immutable local assembly. Generic model classes expose mutable arrays, so this boundary
 * owns validated model encodings and returns detached models, never its internal arrays.
 * Source class scales/axes are already baked in; neither Ratchet nor playable Clank is drawn.
 */
public final class Rac1ClankAssets {
    public enum Role {
        CLANK(601, 33, 18, 7), HELI(607, 36, 9, 0), THRUSTER(608, 15, 9, 4);
        private final int classNumber, joints, clips, chains;
        Role(int classNumber, int joints, int clips, int chains) {
            this.classNumber = classNumber;
            this.joints = joints;
            this.clips = clips;
            this.chains = chains;
        }
        public int classNumber() { return classNumber; }
    }

    private final String discKey;
    private final Map<Role, byte[]> models = new EnumMap<>(Role.class);

    public Rac1ClankAssets(String discKey, Rac1AnimatedAsset clank,
            Rac1AnimatedAsset heli, Rac1AnimatedAsset thruster) throws IOException {
        requireDiscKey(discKey);
        this.discKey = discKey;
        put(Role.CLANK, clank);
        put(Role.HELI, heli);
        put(Role.THRUSTER, thruster);
        long size = models.values().stream().mapToLong(bytes -> bytes.length).sum();
        if (size > Rac1ClankSidecarCodec.MAX_BYTES - 4096L)
            throw new IOException("Clank assembly exceeds aggregate bound");
    }

    public static void requireDiscKey(String key) {
        if (key == null || Rac1DiscCatalog.supported().profiles().stream()
                .noneMatch(p -> Rac1DiscIdentity.key(p.revision(), p).equals(key)))
            throw new IllegalArgumentException("Unsupported Clank source identity");
    }

    private void put(Role role, Rac1AnimatedAsset model) throws IOException {
        validate(role, model);
        byte[] encoded = Rac1CacheCodec.encodeAnimatedModel(model);
        if (models.values().stream().mapToLong(bytes -> bytes.length).sum() + encoded.length
                > Rac1ClankSidecarCodec.MAX_BYTES - 4096L)
            throw new IOException("Clank assembly exceeds aggregate bound");
        validate(role, Rac1CacheCodec.decodeAnimatedModel(encoded));
        models.put(role, encoded);
    }

    private static void validate(Role role, Rac1AnimatedAsset model) throws IOException {
        if (model == null || model.classNumber() != role.classNumber
                || model.skeleton().jointCount() != role.joints
                || model.attachmentChainCount() != role.chains
                || model.mesh().vertexCount() == 0 || model.textures().length == 0)
            throw new IOException("Wrong/incomplete wearable role " + role);
        var names = new HashSet<String>();
        for (Rac1Animation clip : model.animations()) {
            if (clip == null || !names.add(clip.name()) || clip.keyCount() == 0
                    || !clip.sourceTiming().available())
                throw new IOException("Missing/duplicate source animation in " + role);
        }
        for (int i = 0; i < role.clips; i++)
            if (!names.contains("sequence_" + i))
                throw new IOException("Missing required " + role + " sequence_" + i);
        if (role == Role.CLANK) {
            int[] antenna = model.attachmentChain(6);
            if (antenna.length == 0 || antenna[antenna.length - 1] != 10)
                throw new IOException("Missing original Clank antenna attachment");
        }
    }

    public String discKey() { return discKey; }
    public byte[] encodedModel(Role role) { return models.get(role).clone(); }

    /** Copy once into a consumer-owned resource generation, not once per rendered frame. */
    public Rac1AnimatedAsset copyModel(Role role) throws IOException {
        return Rac1CacheCodec.decodeAnimatedModel(models.get(role));
    }
}
