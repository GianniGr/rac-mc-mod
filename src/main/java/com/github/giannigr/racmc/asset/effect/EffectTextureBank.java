package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder;
import java.util.ArrayList;
import java.util.List;

/** Owns bounded original indexed bytes; decoding uses linear texels and CSM1 palettes. */
public final class EffectTextureBank {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public record Entry(int paletteOffset, int clutSlot, int pixelOffset, int width, int height) {}
    private final EffectAssetRef source;
    private final List<Entry> entries;
    private final byte[] bytes;

    public EffectTextureBank(EffectAssetRef source, List<Entry> entries, byte[] bytes)
            throws AssetFormatException {
        if ((source.bank() != EffectAssetRef.Bank.PARTICLE && source.bank() != EffectAssetRef.Bank.FX)
                || source.sourceId() != 0 || entries.isEmpty() || entries.size() > 256
                || bytes.length > MAX_BYTES)
            throw new AssetFormatException("Invalid effect texture bank");
        long decodedPixels = 0;
        for (Entry entry : entries) {
            if (entry.width() < 1 || entry.width() > 1024 || entry.height() < 1
                    || entry.height() > 1024 || entry.clutSlot() < 0 || entry.clutSlot() > 65535)
                throw new AssetFormatException("Invalid effect texture dimensions/CLUT slot");
            decodedPixels += (long) entry.width() * entry.height();
            if (decodedPixels > 16 * 1024 * 1024)
                throw new AssetFormatException("Effect decoded texture budget exceeded");
            Rac1HudBankDecoder.range(bytes, entry.paletteOffset(), 1024);
            Rac1HudBankDecoder.range(bytes, entry.pixelOffset(), entry.width() * entry.height());
        }
        this.source = source;
        this.entries = List.copyOf(entries);
        this.bytes = bytes.clone();
    }

    public EffectAssetRef source() { return source; }
    public List<Entry> entries() { return entries; }
    public byte[] bytes() { return bytes.clone(); }
    public Rac1Texture decode(int id) throws AssetFormatException {
        Entry entry = entries.get(id);
        return Rac1HudBankDecoder.indexed(bytes, entry.pixelOffset(), entry.width(), entry.height(),
                bytes, entry.paletteOffset());
    }
    public List<Rac1Texture> decodeAll() throws AssetFormatException {
        List<Rac1Texture> result = new ArrayList<>();
        for (int id = 0; id < entries.size(); id++) result.add(decode(id));
        return List.copyOf(result);
    }
}
