package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.disc.Rac1CoreIndex;
import com.github.giannigr.racmc.disc.Rac1DiscTables;
import com.github.giannigr.racmc.disc.Rac1LevelData;
import com.github.giannigr.racmc.disc.Rac1LevelHeader;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import com.github.giannigr.racmc.wad.WadDecoder;
import java.io.IOException;
import java.util.Arrays;

/** Optional wrench clips, independent of the shared RACS cache/schema and weapon loop state. */
public record Rac1WrenchAudioCatalog(Rac1AudioCatalog.Clip slam, Rac1AudioCatalog.Clip comet,
        Rac1AudioCatalog.Clip defaultHit, Rac1AudioCatalog.Clip alternateHit) {
    private static final int NOVALIS = 1;

    // Both releases have the same cue/ADPCM and loop bounds. The full comet grain differs.
    static final Pin HIT = new Pin(80, 314, -1, 8932,
            "23e0ea97e3575c1e5a879c5d6a71573d847a1a7b03626a94344a5d5c1e899fb2",
            "f6c8ee0fd9fbc06a76919fd770e32bda073b9da18c8551a0b46d27ea53a38d57",
            "18a0dd27b16335d01b9d1270dc237c41ebb03be8f32d6b20a89f72dc223f5f47",
            "18a0dd27b16335d01b9d1270dc237c41ebb03be8f32d6b20a89f72dc223f5f47",
            "e73f19714b116e55199ddde24a74713f49d2015f580a2a6ee7302fb023066c02");
    static final Pin HIT_ALT = new Pin(81, 315, -1, 6916,
            "19ca02c89464d2524538fd4e7c4699556b5fc6e1f072549e2161805a53d8fd29",
            "b952403ac5509c4c1425f505751dbe06015f8b0964583063730cdb45ca0715d9",
            "75e9f33dc33e8442799009e35c146b5b3494b918edb81f9b96f9092f13e3ffbb",
            "75e9f33dc33e8442799009e35c146b5b3494b918edb81f9b96f9092f13e3ffbb",
            "9049dee17845794b00ab0f1dad10ae67704338f6de1dd25c9ddd245b5185b4b2");
    static final Pin SLAM = new Pin(82, 316, -1, 5180,
            "997783bfe54a4c569cf0a49147db90297fc4f647850ed77143ac0bde0155f647",
            "76815e4b4c84a2c5c5cc7609d2df6076a15a308cb38dd83c9e6b966397a797c4",
            "494290735086dede3cebdbd3dae81cf653966dfe6e7126eaecbd7ffa72e81859",
            "494290735086dede3cebdbd3dae81cf653966dfe6e7126eaecbd7ffa72e81859",
            "d1173abe94666b5dcfc0a5cf5a4cd3f8eb2f407fdd2e9e1ba0f0d4e5e5e8295d");
    static final Pin COMET = new Pin(33, 219, 28, 2352,
            "68214502af6e944124d4a18b5014b3473318ccfed304755d2763a56d10256e41",
            "65ab4c42313f4b9d1bbd412c5d346ddc632f8a907c6c7d4438424fcbad173b8a",
            "aac4dc715a63e77d024819cfb978925b1e74edb636e6b12479e6d9f0bc816044",
            "1b161dfaafeba66a03f384abd6ffb34a3ce559895f6e896d20bcdb364ef16128",
            "848c48579fb263b2a06bf24a36c027ff9ee1d5b8c82f0a833386bad8494dc4a2");

    /** Caller owns the complete validator's already-open handle; no independent path or ISO lookup. */
    public static Rac1WrenchAudioCatalog load(Rac1DiscImage image) throws Exception {
        var tables = Rac1DiscTables.read(image);
        var level = Rac1LevelHeader.read(image, tables.levelSector(NOVALIS));
        var container = Rac1LevelData.read(image, level);
        var bank = new Rac1SoundBank(container.soundBank(image));
        // Read only the required compressed core carrier, not graphics RAM, meshes or animation.
        var index = image.readBytes(container.base() + container.coreIndexOffset(), container.coreIndexSize());
        var coreIndex = Rac1CoreIndex.parse(index);
        var core = WadDecoder.decode(image.readBytes(container.base() + container.coreDataOffset(),
                container.coreDataSize()), coreIndex.decompressedSize());
        var gadget = coreIndex.gadget(71);
        var ratchet = coreIndex.mobyClass(0);
        if (gadget == null || ratchet == null) throw new IOException("Missing Novalis wrench or Ratchet class");
        byte[] wrench = WadDecoder.decode(slice(core, gadget.offset(), gadget.compressedSize()), -1);
        byte[] hero = slice(core, ratchet.offset(), coreIndex.blockSize(ratchet.offset()));
        String bankHash = bank.digest();
        return new Rac1WrenchAudioCatalog(
                clip(image.revision(), bank, bankHash, Rac1SoundMap.resolve(index, wrench, 71, 2), SLAM),
                clip(image.revision(), bank, bankHash, Rac1SoundMap.resolve(index, hero, 0, 14), COMET),
                hit(image.revision(), bank, bankHash, index, wrench, 0, HIT),
                hit(image.revision(), bank, bankHash, index, wrench, 1, HIT_ALT));
    }

    public Rac1AudioCatalog.Clip melee(WrenchMeleeSignal.Variant variant) {
        return variant == WrenchMeleeSignal.Variant.ALTERNATE ? alternateHit : defaultHit;
    }

    private static Rac1AudioCatalog.Clip hit(Rac1DiscRevision revision, Rac1SoundBank bank,
            String bankHash, byte[] index, byte[] wrench, int slot, Pin pin) {
        try {
            return clip(revision, bank, bankHash, Rac1SoundMap.resolve(index, wrench, 71, slot), pin);
        } catch (IOException | IllegalArgumentException absent) {
            // Missing hit media cannot invalidate the previously supported slam and Comet.
            return null;
        }
    }

    private static byte[] slice(byte[] blob, int offset, int size) throws IOException {
        if (offset <= 0 || size <= 0 || offset > blob.length - size)
            throw new IOException("Invalid Novalis audio definition carrier");
        return Arrays.copyOfRange(blob, offset, offset + size);
    }

    private static Rac1AudioCatalog.Clip clip(Rac1DiscRevision revision, Rac1SoundBank bank, String bankHash,
            Rac1SoundMap.Definition definition, Pin pin) throws IOException {
        if (definition.cue() != pin.cue()) throw new IOException("Wrong Novalis wrench remap");
        var tone = bank.tone(definition.cue());
        var decoded = tone.decoded();
        if (!pin.matches(revision, definition, tone))
            throw new IOException("Novalis wrench definition, program or span did not match");
        return new Rac1AudioCatalog.Clip(decoded.pcm(), tone.sampleRate(), definition.metadata(),
                tone.rawCue(), tone.rawGrain(), bankHash, tone.adpcmHash(), tone.cue(),
                decoded.loopStart(), decoded.loopEnd(), decoded.repeat());
    }

    record Pin(int cue, int grain, int loopStart, int end, String definitionHash, String cueHash,
            String palGrainHash, String ntscGrainHash, String spanHash) {
        String grainHash(Rac1DiscRevision revision) {
            return switch (revision) {
                case PAL_V2_00 -> palGrainHash;
                case NTSC_U_V1_00 -> ntscGrainHash;
            };
        }

        boolean matches(Rac1DiscRevision revision, Rac1SoundMap.Definition d, Rac1SoundBank.Tone t) {
            var decoded = t.decoded();
            return d.cue() == cue && t.cue() == cue && t.grain() == grain
                    && t.centerNote() == 72 && t.fine() == 0
                    && decoded.loopStart() == loopStart && decoded.loopEnd() == end
                    && decoded.repeat() == (loopStart >= 0)
                    && spanHash.equals(t.adpcmHash())
                    && definitionHash.equals(SoundBytes.hash(d.metadata()))
                    && cueHash.equals(SoundBytes.hash(t.rawCue()))
                    && grainHash(revision).equals(SoundBytes.hash(t.rawGrain()));
        }
    }
}
