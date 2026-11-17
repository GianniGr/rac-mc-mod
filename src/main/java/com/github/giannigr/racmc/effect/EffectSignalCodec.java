package com.github.giannigr.racmc.effect;

import java.io.*;
import java.util.UUID;

/** Standalone bounded semantic wire contract. Transport registration/authoritative gameplay
 * hooks are a later single-owner integration seam; this codec contains no client or sound links. */
public final class EffectSignalCodec {
    public static final int VERSION = 1, MAX_BYTES = 384;
    private EffectSignalCodec() {}
    public static byte[] write(EffectSignal signal) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeUTF(signal.semantic().name());
            out.writeUTF(signal.dimension());
            out.writeLong(signal.lifecycle().getMostSignificantBits());
            out.writeLong(signal.lifecycle().getLeastSignificantBits());
            out.writeLong(signal.sequence());
            out.writeLong(signal.serverTick());
            out.writeLong(signal.seed());
            out.writeByte(signal.op() == EffectSignal.Op.START ? 0 : 1);
            out.writeDouble(signal.anchor().x());
            out.writeDouble(signal.anchor().y());
            out.writeDouble(signal.anchor().z());
            out.writeBoolean(signal.impact() != null);
            if (signal.impact() != null) {
                for (var point : new EffectSignal.Point[]{signal.impact().tangent(), signal.impact().normal()}) {
                    out.writeDouble(point.x());
                    out.writeDouble(point.y());
                    out.writeDouble(point.z());
                }
            }
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Effect signal too large");
        return bytes.toByteArray();
    }
    public static EffectSignal read(byte[] bytes) throws IOException {
        if (bytes.length < 1 || bytes.length > MAX_BYTES) throw new IOException("Invalid effect signal extent");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != VERSION) throw new IOException("Unsupported semantic effect version");
            var semantic = EffectSignal.Semantic.valueOf(in.readUTF());
            String dimension = in.readUTF();
            UUID lifecycle = new UUID(in.readLong(), in.readLong());
            long sequence = in.readLong(), tick = in.readLong(), seed = in.readLong();
            int op = in.readUnsignedByte();
            if (op > 1) throw new IOException("Unknown effect operation");
            var point = new EffectSignal.Point(in.readDouble(), in.readDouble(), in.readDouble());
            int hasImpact = in.readUnsignedByte();
            if (hasImpact > 1) throw new IOException("Invalid impact presence flag");
            var impact = hasImpact == 0 ? null : new EffectSignal.Impact(
                    new EffectSignal.Point(in.readDouble(), in.readDouble(), in.readDouble()),
                    new EffectSignal.Point(in.readDouble(), in.readDouble(), in.readDouble()));
            if (in.available() != 0) throw new IOException("Trailing effect signal bytes");
            return new EffectSignal(semantic, dimension, lifecycle, sequence, tick, seed,
                    op == 0 ? EffectSignal.Op.START : EffectSignal.Op.STOP, point, impact);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid semantic effect signal", error);
        }
    }
}
