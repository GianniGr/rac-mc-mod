package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.clank.Rac1ClankMotion.Vector;
import java.io.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/** Hydro-only bounded semantic protocol. Never changes the frozen aerial wire or durable equipment codec. */
public final class Rac1HydroWire {
    public static final int MAX_BYTES=256,INPUT_BYTES=41;
    private Rac1HydroWire() {}
    public record Input(long epoch,long serial,Rac1HydroBoost.Controls frame) {
        public Input {if(epoch<0||serial<0||frame==null)throw new IllegalArgumentException("Invalid Hydro input");}
    }
    public record Snapshot(UUID player,long epoch,long clankEpoch,long tick,long acknowledged,boolean access,
            boolean wet,boolean controlled,Rac1ClankEquipment.Module dry,Rac1HydroWater.Mode water,
            Rac1HydroVisual.Phase visual,int visualTime,Vector position,Rac1HydroMotion.State motion,
            Rac1HydroBoost boost) {
        public Snapshot {
            if(player==null||epoch<0||clankEpoch<0||tick<0||acknowledged< -1||dry==null||water==null
                    ||visual==null||visualTime<0||visualTime>14||position==null||motion==null||boost==null
                    ||Math.abs(position.x())>32_000_000||Math.abs(position.y())>32_000_000||Math.abs(position.z())>32_000_000
                    ||wet&&!access||controlled&&(!wet||water!=Rac1HydroWater.Mode.SUBMERGED)
                    ||!access&&visual!=Rac1HydroVisual.Phase.DRY
                    ||boost!=Rac1HydroBoost.BLOCKED&&(!wet||water!=Rac1HydroWater.Mode.SUBMERGED))
                throw new IllegalArgumentException("Invalid Hydro snapshot");
        }
    }
    public static byte[] encode(Input value) {
        return write(out->{out.writeLong(value.epoch());out.writeLong(value.serial());var f=value.frame();
            out.writeByte((f.sprintDown()?1:0)|(f.rise()?2:0)|(f.dive()?4:0)|(f.active()?8:0));
            out.writeDouble(f.left());out.writeDouble(f.forward());out.writeDouble(f.yaw());});
    }
    public static Input input(byte[] bytes)throws IOException {
        if(bytes.length!=INPUT_BYTES)throw new IOException("Wrong Hydro input size");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            long epoch=in.readLong(),serial=in.readLong();int flags=in.readUnsignedByte();
            if((flags&~15)!=0)throw new IOException("Unknown Hydro input flags");
            return new Input(epoch,serial,new Rac1HydroBoost.Controls((flags&8)!=0,(flags&1)!=0,(flags&2)!=0,
                    (flags&4)!=0,in.readDouble(),in.readDouble(),in.readDouble()));
        }catch(IllegalArgumentException bad){throw new IOException(bad);}
    }
    public static byte[] encode(Snapshot v) {
        return write(out->{
            out.writeLong(v.player().getMostSignificantBits());out.writeLong(v.player().getLeastSignificantBits());
            out.writeLong(v.epoch());out.writeLong(v.clankEpoch());out.writeLong(v.tick());out.writeLong(v.acknowledged());
            out.writeByte((v.access()?1:0)|(v.wet()?2:0)|(v.controlled()?4:0));out.writeByte(v.dry().sourceId());
            out.writeByte(v.water().ordinal());out.writeByte(v.visual().ordinal());out.writeInt(v.visualTime());
            vector(out,v.position());var m=v.motion();out.writeByte(m.stage().ordinal());
            out.writeInt(m.phase());out.writeInt(m.stroke());out.writeInt(m.diveIn());
            out.writeDouble(m.speed());out.writeDouble(m.yaw());out.writeDouble(m.yawRate());
            out.writeDouble(m.pitch());out.writeDouble(m.pitchRate());vector(out,m.effective());
            out.writeByte(v.boost().ordinal());
        });
    }
    public static Snapshot snapshot(byte[] bytes)throws IOException {
        if(bytes.length>MAX_BYTES)throw new IOException("Hydro snapshot exceeds bound");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            var id=new UUID(in.readLong(),in.readLong());
            long epoch=in.readLong(),clank=in.readLong(),tick=in.readLong(),ack=in.readLong();
            int flags=in.readUnsignedByte();if((flags&~7)!=0)throw new IOException("Unknown Hydro flags");
            var dry=Rac1ClankEquipment.Module.fromSource(in.readUnsignedByte());
            var water=at(Rac1HydroWater.Mode.values(),in.readUnsignedByte());
            var visual=at(Rac1HydroVisual.Phase.values(),in.readUnsignedByte());int time=in.readInt();
            var position=vector(in);var stage=at(Rac1HydroMotion.Stage.values(),in.readUnsignedByte());
            var state=new Rac1HydroMotion.State(stage,in.readInt(),in.readInt(),in.readInt(),
                    in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),vector(in));
            var boost=at(Rac1HydroBoost.values(),in.readUnsignedByte());
            if(in.available()!=0)throw new IOException("Trailing Hydro snapshot");
            return new Snapshot(id,epoch,clank,tick,ack,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,dry,water,visual,time,
                    position,state,boost);
        }catch(IllegalArgumentException bad){throw new IOException(bad);}
    }
    private static <T>T at(T[] values,int index)throws IOException {
        if(index>=values.length)throw new IOException("Unknown Hydro enum");return values[index];
    }
    private static void vector(DataOutputStream out,Vector v)throws IOException {out.writeDouble(v.x());out.writeDouble(v.y());out.writeDouble(v.z());}
    private static Vector vector(DataInputStream in)throws IOException {return new Vector(in.readDouble(),in.readDouble(),in.readDouble());}
    @FunctionalInterface private interface Writer {void write(DataOutputStream out)throws IOException;}
    private static byte[] write(Writer writer) {
        try {var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){writer.write(out);}
            if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("Hydro packet exceeds bound");
            return bytes.toByteArray();
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    /** One movement frame per tick, but retain both physical edges if packets are coalesced. */
    public static final class Stream {
        private record Sample(long receipt,Rac1HydroBoost.Controls frame) {}
        private final long epoch;
        private long high=-1,accepted=-1,receipt=-1,rateTick=-1,consumed=-1,consumeTick=-1;
        private int count;
        private final ArrayDeque<Sample> pending=new ArrayDeque<>();
        private List<Rac1HydroBoost.Controls> current=List.of();
        public Stream(long epoch){this.epoch=epoch;}
        public boolean offer(Input input,long tick) {
            if(input.epoch()!=epoch||input.serial()<=high||tick<receipt)return false;
            high=input.serial();if(rateTick!=tick){rateTick=tick;count=0;}
            if(++count>2)return false;
            pending.removeIf(sample->tick-sample.receipt()>1);
            pending.addLast(new Sample(tick,input.frame()));
            accepted=input.serial();receipt=tick;return true;
        }
        public List<Rac1HydroBoost.Controls> consume(long tick) {
            if(tick==consumeTick)return current;
            current=pending.stream().filter(sample->sample.receipt()<=tick&&tick-sample.receipt()<=1)
                    .map(Sample::frame).toList();
            pending.clear();
            consumed=accepted;consumeTick=tick;return current;
        }
        public boolean live(long tick){return receipt>=0&&tick>=receipt&&tick-receipt<=4;}
        public long acknowledged(){return consumed;}
    }
}
