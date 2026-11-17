package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.disc.Rac1CoreIndex;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.range;

/** Generic core bank reader; curated adapters currently request source level 1. */
public final class Rac1EffectBankImporter {
    private Rac1EffectBankImporter() {}

    public static EffectBanks read(Rac1LevelAssets assets, String discSha1)
            throws AssetFormatException {
        return read(assets.coreIndex(), assets.coreData(), discSha1, assets.level());
    }

    public static EffectBanks read(Rac1CoreIndex index, byte[] core, String discSha1, int level)
            throws AssetFormatException {
        if (core.length != index.decompressedSize())
            throw new AssetFormatException("Effect core size mismatch");
        byte[] header = index.bytes();
        int at = word(header, 0x6c);
        range(header, at, 16);
        int count = word(header, at), blob = word(header, at + 8), size = word(header, at + 12);
        if (count < 1 || count > 1024 || blob < 16 + count * 4 || blob > 16 + count * 4 + 15
                || size < 1 || size > 65536)
            throw new AssetFormatException("Invalid particle definition bounds");
        range(header, at, blob + size);
        ParticleFrameLists lists = new ParticleFrameLists(Arrays.copyOfRange(header, at, at + blob + size));
        return new EffectBanks(bank(index, core, discSha1, level, true),
                bank(index, core, discSha1, level, false), lists);
    }

    private static EffectTextureBank bank(Rac1CoreIndex index, byte[] core, String sha1,
                                           int level, boolean particle) throws AssetFormatException {
        byte[] header = index.bytes();
        int field = particle ? 0x50 : 0x58;
        int count = word(header, field), table = word(header, field + 4);
        if (count < 1 || count > 256) throw new AssetFormatException("Invalid effect bank count");
        range(header, table, count * 16);
        int start = word(header, particle ? 0x64 : 0x68);
        if (start <= 0 || start >= core.length)
            throw new AssetFormatException("Effect bank outside core");
        int length = index.blockSize(start);
        if (length < 1 || length > EffectTextureBank.MAX_BYTES)
            throw new AssetFormatException("Invalid effect bank extent");
        range(core, start, length);
        List<EffectTextureBank.Entry> entries = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            int at = table + id * 16;
            int palette = word(header, at), pixels = word(header, at + (particle ? 8 : 4));
            int width = word(header, at + (particle ? 12 : 8));
            entries.add(new EffectTextureBank.Entry(palette, particle ? word(header, at + 4) : 0,
                    pixels, width, particle ? width : word(header, at + 12)));
        }
        return new EffectTextureBank(new EffectAssetRef(sha1, level,
                particle ? EffectAssetRef.Bank.PARTICLE : EffectAssetRef.Bank.FX, 0),
                entries, Arrays.copyOfRange(core, start, start + length));
    }
}
