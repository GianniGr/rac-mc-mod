package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.MobyClassReader.*;
import com.github.giannigr.racmc.asset.shop.Rac1ShopAssets;
import java.io.*;
import java.util.*;

/** Shop-local big-endian source metadata section; never part of complete-model wire encoding. */
public final class Rac1ShopMaterialCodec {
    public static final int VERSION = 1, SOURCE_LEVEL = 1, MAX_BYTES = 8 * 1024 * 1024;
    private Rac1ShopMaterialCodec() {}

    public static byte[] write(Map<Integer, ClassMaterialMetadata> metadata) throws IOException {
        validate(metadata);
        long size = 12 + Rac1ShopAssets.MATERIAL_CLASSES.size()*16L;
        for (var source : metadata.values())
            for (var packet : source.packets())
                size += 16L + packet.normalMultipliers().length + packet.materials().size()*20L;
        if (size > MAX_BYTES) throw new IOException("Shop material metadata too large");
        var bytes = new ByteArrayOutputStream((int)size);
        var out = new DataOutputStream(bytes);
        out.writeInt(VERSION); out.writeInt(SOURCE_LEVEL); out.writeInt(Rac1ShopAssets.MATERIAL_CLASSES.size());
        for (int id : Rac1ShopAssets.MATERIAL_CLASSES) {
            var source = metadata.get(id);
            out.writeInt(id); out.writeInt(source.glowRgba());
            out.writeShort(source.mode44()); out.writeByte(source.mode47()); out.writeByte(0);
            out.writeInt(source.packets().size());
            for (var packet : source.packets()) {
                out.writeInt(packet.packetIndex()); out.writeByte(packet.metal() ? 1 : 0); out.writeByte(0);
                out.writeShort(packet.vertexCount());
                byte[] multipliers = packet.normalMultipliers();
                out.writeInt(multipliers.length); out.write(multipliers); out.writeInt(packet.materials().size());
                for (var material : packet.materials()) {
                    out.writeInt(material.textureSelector()); out.writeLong(material.tex1()); out.writeLong(material.clamp());
                }
            }
        }
        return bytes.toByteArray();
    }

    public static Map<Integer, ClassMaterialMetadata> read(byte[] bytes) throws IOException {
        if (bytes.length < 92 || bytes.length > MAX_BYTES) throw new IOException("Invalid shop material section extent");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != VERSION || in.readInt() != SOURCE_LEVEL
                || in.readInt() != Rac1ShopAssets.MATERIAL_CLASSES.size())
            throw new IOException("Unsupported shop material section identity");
        Map<Integer, ClassMaterialMetadata> metadata = new LinkedHashMap<>();
        for (int expected : Rac1ShopAssets.MATERIAL_CLASSES) {
            int id = in.readInt(), glow = in.readInt(), mode44 = in.readUnsignedShort(), mode47 = in.readUnsignedByte();
            if (id != expected || in.readUnsignedByte() != 0) throw new IOException("Invalid shop material class order/reserved bits");
            int count = in.readInt();
            if (count < 0 || count > 510 || count*16L > in.available())
                throw new IOException("Invalid shop material packet count");
            List<PacketMetadata> packets = new ArrayList<>(count);
            for (int p = 0; p < count; p++) {
                int index = in.readInt(), metal = in.readUnsignedByte(), reserved = in.readUnsignedByte();
                int vertices = in.readUnsignedShort(), length = in.readInt();
                if (metal > 1 || reserved != 0 || vertices > 255 || length != (metal == 1 ? 0 : vertices*4L)
                        || length < 0 || length > in.available()-4)
                    throw new IOException("Invalid shop material packet extent/flags");
                byte[] multipliers = in.readNBytes(length);
                int materialCount = in.readInt();
                if (materialCount < 0 || materialCount > 64 || materialCount*20L > in.available())
                    throw new IOException("Invalid shop material block count");
                List<PacketMaterial> materials = new ArrayList<>(materialCount);
                for (int m = 0; m < materialCount; m++)
                    materials.add(new PacketMaterial(in.readInt(),in.readLong(),in.readLong()));
                packets.add(new PacketMetadata(index,metal == 1,vertices,multipliers,materials));
            }
            metadata.put(id,new ClassMaterialMetadata(glow,mode44,mode47,packets));
        }
        if (in.available() != 0) throw new IOException("Trailing shop material metadata");
        validate(metadata);
        return Map.copyOf(metadata);
    }

    private static void validate(Map<Integer, ClassMaterialMetadata> metadata) throws IOException {
        try { Rac1ShopAssets.requireMaterialMetadata(metadata); }
        catch (IllegalArgumentException | NullPointerException failure) {
            throw new IOException("Invalid shop material metadata",failure);
        }
    }
}
