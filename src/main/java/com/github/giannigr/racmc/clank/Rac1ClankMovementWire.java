package com.github.giannigr.racmc.clank;

import java.io.*;
import java.util.UUID;
import com.github.giannigr.racmc.clank.Rac1ClankMotion.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;

/** Bounded semantic input C2S; only authoritative correction S2C contains world coordinates. */
public final class Rac1ClankMovementWire {
    public static final int MAX_BYTES=256;
    public record Input(long epoch,long serial,Rac1ClankInput frame) {
        public Input {
            if(epoch<0||serial<0||frame==null) throw new IllegalArgumentException("Invalid input");
        }
    }
    public record Correction(UUID player,long epoch,long acknowledged,long serverTick,
                             Vector position,State state) {
        public Correction {
            if(player==null||epoch<0||acknowledged< -1||serverTick<0||position==null||state==null
                    ||Math.abs(position.x())>30_000_000||Math.abs(position.z())>30_000_000
                    ||Math.abs(position.y())>20_000_000)
                throw new IllegalArgumentException("Invalid authoritative correction");
        }
    }
    private Rac1ClankMovementWire() {}
    public static byte[] encode(Input sample) {
        return write(out->{out.writeLong(sample.epoch());out.writeLong(sample.serial());frame(out,sample.frame());});
    }
    public static Input input(byte[] bytes) throws IOException {
        return read(bytes,in->new Input(in.readLong(),in.readLong(),frame(in)));
    }
    public static byte[] encode(Correction correction) {
        return write(out->{
            out.writeLong(correction.player().getMostSignificantBits());
            out.writeLong(correction.player().getLeastSignificantBits());
            out.writeLong(correction.epoch());out.writeLong(correction.acknowledged());
            out.writeLong(correction.serverTick());vector(out,correction.position());
            State s=correction.state();
            out.writeLong(s.clock());out.writeLong(s.jumpPressed());
            out.writeInt(s.crouch());out.writeInt(s.phase());out.writeByte(s.action().ordinal());
            out.writeByte(s.module().sourceId());vector(out,s.velocity());
            out.writeByte((s.previousJump()?1:0)|(s.spentAir()?2:0)|(s.airborne()?4:0));
            for(double value:new double[]{s.hopHeight(),s.appliedHop(),s.windupSpeed(),s.gravity(),
                    s.facingX(),s.facingZ()})out.writeDouble(value);
            frame(out,s.input());
        });
    }
    public static Correction correction(byte[] bytes) throws IOException {
        return read(bytes,in->{
            UUID id=new UUID(in.readLong(),in.readLong());
            long epoch=in.readLong(),ack=in.readLong(),tick=in.readLong();
            Vector position=vector(in);long clock=in.readLong(),pressed=in.readLong();
            int crouch=in.readInt(),phase=in.readInt(),action=in.readUnsignedByte();
            Module module=Module.fromSource(in.readUnsignedByte());Vector velocity=vector(in);
            int flags=in.readUnsignedByte();
            if(action>=Action.values().length||(flags&~7)!=0)throw new IOException("Unknown motion fields");
            return new Correction(id,epoch,ack,tick,position,new State(clock,pressed,crouch,phase,
                    Action.values()[action],module,velocity,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,
                    in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),
                    in.readDouble(),in.readDouble(),frame(in)));
        });
    }
    private static void frame(DataOutputStream out,Rac1ClankInput f)throws IOException {
        out.writeByte((f.jumpA()?1:0)|(f.jumpB()?2:0)|(f.crouch()?4:0));
        out.writeDouble(f.left());out.writeDouble(f.forward());out.writeDouble(f.yawDegrees());
    }
    private static Rac1ClankInput frame(DataInputStream in)throws IOException {
        int flags=in.readUnsignedByte();if((flags&~7)!=0)throw new IOException("Unknown input flags");
        return new Rac1ClankInput((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,
                in.readDouble(),in.readDouble(),in.readDouble());
    }
    private static void vector(DataOutputStream out,Vector v)throws IOException {
        out.writeDouble(v.x());out.writeDouble(v.y());out.writeDouble(v.z());
    }
    private static Vector vector(DataInputStream in)throws IOException {
        return new Vector(in.readDouble(),in.readDouble(),in.readDouble());
    }
    private interface Writer {void apply(DataOutputStream out)throws IOException;}
    private interface Reader<T>{T apply(DataInputStream in)throws IOException;}
    private static byte[] write(Writer writer) {
        try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)) {
            writer.apply(out);byte[] result=bytes.toByteArray();
            if(result.length>MAX_BYTES)throw new IllegalArgumentException("Oversized motion payload");
            return result;
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    private static <T>T read(byte[] bytes,Reader<T> reader)throws IOException {
        if(bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("Invalid motion payload size");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            T result=reader.apply(in);if(in.available()!=0)throw new IOException("Trailing motion payload");
            return result;
        }catch(IllegalArgumentException bad){throw new IOException("Invalid semantic motion state",bad);}
    }
}
