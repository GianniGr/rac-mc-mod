package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.effect.*;
import com.github.giannigr.racmc.asset.shop.Rac1ShopModels;
import com.github.giannigr.racmc.cache.Rac1CacheCodec;
import java.io.*;
import java.util.List;

/** Shop model identities/envelope only; geometry/skeleton/animation serialization is shared. */
public final class Rac1ShopModelCodec {
    public static final int MAX_BYTES = 24 * 1024 * 1024;
    private Rac1ShopModelCodec() {}
    public static byte[] write(Rac1ShopModels models) throws IOException {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeInt(1);
        append(out, bytes, Rac1CacheCodec.encodeAnimatedModel(models.salesman()));
        append(out, bytes, Rac1CacheCodec.encodeAnimatedModel(models.backdrop()));
        append(out, bytes, Rac1CacheCodec.encodeAnimatedModel(models.popup()));
        append(out, bytes, Rac1CacheCodec.encodeStaticModel(new Rac1StaticAsset(1006, "moby_1006",
                models.ammo().mesh(), models.ammo().textures().toArray(Rac1Texture[]::new))));
        return bytes.toByteArray();
    }
    private static void append(DataOutputStream out, ByteArrayOutputStream bytes, byte[] model) throws IOException {
        if ((long) bytes.size() + 4 + model.length > MAX_BYTES) throw new IOException("Shop models exceed bounded section");
        out.writeInt(model.length); out.write(model);
    }
    private static byte[] model(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > in.available() || length > Rac1CacheCodec.MAX_MODEL_BYTES)
            throw new IOException("Invalid shop model extent");
        return in.readNBytes(length);
    }
    public static Rac1ShopModels read(byte[] bytes, String sha1) throws IOException {
        if (bytes.length < 20 || bytes.length > MAX_BYTES) throw new IOException("Invalid shop model section size");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 1) throw new IOException("Unsupported shop model section version");
        var salesman = Rac1CacheCodec.decodeAnimatedModel(model(in));
        var backdrop = Rac1CacheCodec.decodeAnimatedModel(model(in));
        var popup = Rac1CacheCodec.decodeAnimatedModel(model(in));
        var ammo = Rac1CacheCodec.decodeStaticModel(model(in));
        if (ammo.classNumber() != 1006 || in.available() != 0) throw new IOException("Invalid shop ammo identity/trailing data");
        try {
            return new Rac1ShopModels(salesman, backdrop, popup,
                    new EffectMeshAsset(new EffectAssetRef(sha1, 1, EffectAssetRef.Bank.MOBY, 1006),
                            ammo.mesh(), List.of(ammo.textures())));
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid original shop model identities", invalid); }
    }
}
