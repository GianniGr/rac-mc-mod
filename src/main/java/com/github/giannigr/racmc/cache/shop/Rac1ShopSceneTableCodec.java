package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.shop.*;
import java.io.*;

/** Fixed bounded converted numeric tables, not source overlay containers. */
public final class Rac1ShopSceneTableCodec {
    public static final int BYTES = 4 + (64 + 64 + 24 + 24 + 8 + 20 + 1 + 4 + 1) * 4;
    private Rac1ShopSceneTableCodec() {}
    public static byte[] write(Rac1ShopSceneTables tables) throws IOException {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeInt(4);
        for (int v : tables.led().cells()) out.writeInt(v);
        for (int v : tables.led().advances()) out.writeInt(v);
        for (int v : tables.tickerIds()) out.writeInt(v);
        for (float v : tables.margins()) out.writeFloat(v);
        for (float v : tables.blasterPanel()) out.writeFloat(v);
        for (float v : tables.blasterHolograms()) out.writeFloat(v);
        out.writeInt(tables.ammoClass());
        for (int v : tables.monitorTargets()) out.writeInt(v);
        out.writeFloat(tables.vendorPackedScale());
        return bytes.toByteArray();
    }
    public static Rac1ShopSceneTables read(byte[] bytes) throws IOException {
        if (bytes.length != BYTES) throw new IOException("Invalid shop scene table extent");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 4) throw new IOException("Unsupported shop scene table version");
        int[] cells = new int[64], advances = new int[64], refs = new int[24];
        float[] margins = new float[24], panel = new float[8], holograms = new float[20];
        for (int i = 0; i < 64; i++) cells[i] = in.readInt();
        for (int i = 0; i < 64; i++) advances[i] = in.readInt();
        for (int i = 0; i < 24; i++) refs[i] = in.readInt();
        for (int i = 0; i < 24; i++) margins[i] = in.readFloat();
        for (int i = 0; i < 8; i++) panel[i] = in.readFloat();
        for (int i = 0; i < 20; i++) holograms[i] = in.readFloat();
        int ammoClass = in.readInt();
        int[] targets = new int[4]; for (int i = 0; i < 4; i++) targets[i] = in.readInt();
        float packedScale = in.readFloat();
        try { return new Rac1ShopSceneTables(new Rac1ShopLedFont(cells, advances), refs, margins, panel,
                holograms, ammoClass,targets,packedScale); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid original shop scene tables", invalid); }
    }
}
