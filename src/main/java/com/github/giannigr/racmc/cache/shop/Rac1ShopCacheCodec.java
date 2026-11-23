package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.shop.*;
import com.github.giannigr.racmc.cache.Rac1HudCacheCodec;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** RACSHOP 2D sidecar only. No RACM version change or duplicated model serializer. */
public final class Rac1ShopCacheCodec {
    public static final int SCHEMA = 5, CONVERTER = 5, MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAGIC = 0x52415348;
    private Rac1ShopCacheCodec() {}

    public static byte[] write(Rac1ShopAssets assets) throws IOException {
        if (!assets.missing2d().isEmpty()) throw new IOException("Incomplete original shop 2D resources");
        if (!assets.missingSourceTables().isEmpty()) throw new IOException("Incomplete original shop source tables");
        if (!assets.missingImportedModels().isEmpty()) throw new IOException("Incomplete original shop model resources");
        if (!assets.missingMaterialMetadata().isEmpty()) throw new IOException("Incomplete original shop material metadata");
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeInt(MAGIC); out.writeInt(SCHEMA); out.writeInt(CONVERTER);
        out.writeUTF(assets.discSha1()); out.writeInt(assets.language());
        byte[] hud = Rac1HudCacheCodec.write(assets.graphics());
        if ((long)bytes.size()+4+hud.length > MAX_BYTES-8) throw new IOException("Shop sidecar too large");
        out.writeInt(hud.length); out.write(hud);
        if ((long)bytes.size()+Rac1ShopSceneTableCodec.BYTES > MAX_BYTES-8) throw new IOException("Shop sidecar too large");
        out.write(Rac1ShopSceneTableCodec.write(assets.sceneTables()));
        byte[] models = Rac1ShopModelCodec.write(assets.models());
        if ((long)bytes.size()+4+models.length > MAX_BYTES-8) throw new IOException("Shop sidecar too large");
        out.writeInt(models.length); out.write(models);
        byte[] materials = Rac1ShopMaterialCodec.write(assets.materialMetadata());
        if ((long)bytes.size()+4+materials.length+12 > MAX_BYTES) throw new IOException("Shop sidecar too large");
        out.writeInt(materials.length); out.write(materials);
        out.writeInt(assets.text().ids().size());
        for (int id : assets.text().ids()) {
            byte[] text = assets.text().get(id);
            if ((long)bytes.size()+8+text.length+8 > MAX_BYTES) throw new IOException("Shop sidecar too large");
            out.writeInt(id); out.writeInt(text.length); out.write(text);
        }
        if (bytes.size() > MAX_BYTES - 8) throw new IOException("Shop sidecar too large");
        var crc = new CRC32(); crc.update(bytes.toByteArray());
        out.writeLong(crc.getValue());
        return bytes.toByteArray();
    }

    public static Rac1ShopAssets read(byte[] bytes, String sha1, int language) throws IOException {
        if (bytes.length < 32 || bytes.length > MAX_BYTES) throw new IOException("Invalid shop sidecar size");
        var crc = new CRC32(); crc.update(bytes, 0, bytes.length - 8);
        if (new DataInputStream(new ByteArrayInputStream(bytes,bytes.length-8,8)).readLong() != crc.getValue())
            throw new IOException("Corrupt shop sidecar checksum");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != MAGIC || in.readInt() != SCHEMA || in.readInt() != CONVERTER)
            throw new IOException("Unsupported shop sidecar version");
        if (!in.readUTF().equals(sha1) || in.readInt() != language)
            throw new IOException("Wrong shop sidecar disc/language");
        int length = in.readInt();
        if (length < 0 || length > in.available() - 12) throw new IOException("Invalid shop graphics extent");
        var hud = Rac1HudCacheCodec.read(in.readNBytes(length));
        var tables = Rac1ShopSceneTableCodec.read(in.readNBytes(Rac1ShopSceneTableCodec.BYTES));
        int modelLength = in.readInt();
        if (modelLength < 0 || modelLength > Rac1ShopModelCodec.MAX_BYTES || modelLength > in.available() - 12)
            throw new IOException("Invalid shop models extent");
        var models = Rac1ShopModelCodec.read(in.readNBytes(modelLength), sha1);
        int metadataLength = in.readInt();
        if (metadataLength < 92 || metadataLength > Rac1ShopMaterialCodec.MAX_BYTES || metadataLength > in.available()-12)
            throw new IOException("Invalid shop material metadata extent");
        var materials = Rac1ShopMaterialCodec.read(in.readNBytes(metadataLength));
        int count = in.readInt();
        if (count < 0 || count > 128) throw new IOException("Invalid shop string count");
        Map<Integer, byte[]> strings = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            int id = in.readInt(), size = in.readInt();
            if (size < 0 || size > Rac1ShopText.MAX_STRING || size > in.available() - 8)
                throw new IOException("Invalid shop string extent");
            if (strings.put(id, in.readNBytes(size)) != null) throw new IOException("Duplicate shop text ID");
        }
        if (in.available() != 8 || in.readLong() != crc.getValue())
            throw new IOException("Corrupt shop sidecar checksum/trailing data");
        try {
            var assets = new Rac1ShopAssets(sha1, language, hud, new Rac1ShopText(strings), tables, models, materials);
            if (!assets.missing2d().isEmpty()) throw new IOException("Incomplete shop sidecar: " + assets.missing2d());
            if (!assets.missingSourceTables().isEmpty())
                throw new IOException("Incomplete shop source tables: " + assets.missingSourceTables());
            if (!assets.missingImportedModels().isEmpty())
                throw new IOException("Incomplete shop models: " + assets.missingImportedModels());
            if (!assets.missingMaterialMetadata().isEmpty())
                throw new IOException("Incomplete shop material metadata: " + assets.missingMaterialMetadata());
            return assets;
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid shop sidecar", failure);
        }
    }
}
