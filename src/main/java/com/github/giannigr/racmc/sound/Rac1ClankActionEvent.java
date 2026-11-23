package com.github.giannigr.racmc.sound;

import java.io.*;
import java.util.UUID;

/** Small server-to-client presentation fact, never input/motion/grant authority or media bytes. */
public record Rac1ClankActionEvent(UUID actor,long epoch,long serial,long tick,int sourceStep,int local) {
    public static final int BYTES=48;
    public Rac1ClankActionEvent {
        if(actor==null||epoch<0||serial<0||tick<0||sourceStep<0||sourceStep>36000
                ||local!=0&&local!=1&&local!=12&&local!=30&&local!=31&&local!=43&&local!=46&&local!=62)
            throw new IllegalArgumentException("Invalid reached Clank sound");
    }
    public byte[] encode() {
        try {
            var bytes=new ByteArrayOutputStream();
            var out=new DataOutputStream(bytes);out.writeLong(actor.getMostSignificantBits());
            out.writeLong(actor.getLeastSignificantBits());out.writeLong(epoch);out.writeLong(serial);
            out.writeLong(tick);out.writeInt(sourceStep);out.writeInt(local);return bytes.toByteArray();
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    public static Rac1ClankActionEvent decode(byte[] bytes)throws IOException {
        if(bytes.length!=BYTES)throw new IOException("Wrong reached sound size");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(bytes));
            return new Rac1ClankActionEvent(new UUID(in.readLong(),in.readLong()),in.readLong(),in.readLong(),
                    in.readLong(),in.readInt(),in.readInt());
        }catch(IllegalArgumentException bad){throw new IOException(bad);}
    }
}
