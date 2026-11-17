package com.github.giannigr.racmc.cache.effect;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.effect.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Independently versioned EFFECTSIDECAR; never reads/writes RACM or SOUND sidecars.
 * SHA-256 detects corruption, not authenticity. No ISO paths are stored. */
public final class Rac1EffectCacheCodec {
    public static final int SCHEMA = 1, CONVERTER = 1, PROGRAM_VERSION = 1;
    public static final int MAX_FILE_BYTES = 2 * EffectTextureBank.MAX_BYTES + 128 * 1024;
    private static final byte[] MAGIC = {'R', 'A', 'C', 'E', 'F', 'X', 0, 1};
    private Rac1EffectCacheCodec() {}

    public static byte[] write(EffectBanks content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(MAGIC);
            out.writeInt(SCHEMA);
            out.writeInt(CONVERTER);
            out.writeInt(PROGRAM_VERSION);
            out.writeUTF(content.particles().source().discSha1());
            out.writeInt(content.particles().source().sourceLevel());
            bank(out, content.particles());
            bank(out, content.fx());
            array(out, content.definitions().bytes());
        }
        byte[] body = bytes.toByteArray();
        if (body.length > MAX_FILE_BYTES - 32) throw new IOException("Effects sidecar too large");
        bytes.write(digest(body));
        return bytes.toByteArray();
    }

    public static EffectBanks read(byte[] file, String expectedSha1, int expectedLevel)
            throws IOException {
        if (file.length < 64 || file.length > MAX_FILE_BYTES)
            throw new IOException("Invalid effects sidecar length");
        byte[] body = Arrays.copyOf(file, file.length - 32);
        if (!MessageDigest.isEqual(digest(body), Arrays.copyOfRange(file, body.length, file.length)))
            throw new IOException("Effects sidecar checksum mismatch");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (!Arrays.equals(in.readNBytes(MAGIC.length), MAGIC) || in.readInt() != SCHEMA
                    || in.readInt() != CONVERTER || in.readInt() != PROGRAM_VERSION)
                throw new IOException("Unsupported effects sidecar kind/version");
            String sha1 = in.readUTF();
            int level = in.readInt();
            if (!sha1.equals(expectedSha1) || level != expectedLevel)
                throw new IOException("Effects sidecar source mismatch");
            EffectTextureBank particles = bank(in, sha1, level, EffectAssetRef.Bank.PARTICLE);
            EffectTextureBank fx = bank(in, sha1, level, EffectAssetRef.Bank.FX);
            ParticleFrameLists definitions = new ParticleFrameLists(array(in, 128 * 1024));
            if (in.available() != 0) throw new IOException("Trailing effects sidecar data");
            return new EffectBanks(particles, fx, definitions);
        } catch (IllegalArgumentException | AssetFormatException exception) {
            throw new IOException("Invalid effects sidecar content", exception);
        }
    }

    private static void bank(DataOutputStream out, EffectTextureBank bank) throws IOException {
        out.writeUTF(bank.source().bank().name());
        out.writeInt(bank.entries().size());
        for (EffectTextureBank.Entry entry : bank.entries()) {
            out.writeInt(entry.paletteOffset());
            out.writeInt(entry.clutSlot());
            out.writeInt(entry.pixelOffset());
            out.writeInt(entry.width());
            out.writeInt(entry.height());
        }
        array(out, bank.bytes());
    }

    private static EffectTextureBank bank(DataInputStream in, String sha1, int level,
                                           EffectAssetRef.Bank kind) throws IOException, AssetFormatException {
        if (!in.readUTF().equals(kind.name())) throw new IOException("Wrong effect bank namespace");
        int count = in.readInt();
        if (count < 1 || count > 256) throw new IOException("Invalid effect texture count");
        List<EffectTextureBank.Entry> entries = new ArrayList<>();
        for (int id = 0; id < count; id++)
            entries.add(new EffectTextureBank.Entry(in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt()));
        return new EffectTextureBank(new EffectAssetRef(sha1, level, kind, 0), entries,
                array(in, EffectTextureBank.MAX_BYTES));
    }

    private static void array(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }
    private static byte[] array(DataInputStream in, int maximum) throws IOException {
        int length = in.readInt();
        if (length < 1 || length > maximum || length > in.available())
            throw new IOException("Invalid effect byte extent");
        return in.readNBytes(length);
    }
    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
