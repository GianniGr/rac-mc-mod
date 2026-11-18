package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import net.minecraft.nbt.CompoundTag;

/** Ledger codec; position remains in the existing pickup record for backward compatibility. */
final class Rac1BoltMotionTag {
    private Rac1BoltMotionTag() {}
    static CompoundTag save(State s) {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", s.phase().name());
        vector(tag, "velocity", s.velocity()); vector(tag, "rest", s.rest()); vector(tag, "up", s.up());
        tag.putDouble("startY", s.startY()); tag.putInt("total", s.total()); tag.putInt("remaining", s.remaining());
        tag.putInt("settleFrames", s.settleFrames()); tag.putInt("idleFrames", s.idleFrames());
        tag.putDouble("spin", s.spin()); tag.putDouble("secondary", s.secondary());
        tag.putInt("direction", s.direction()); tag.putDouble("flySpeed", s.flySpeed());
        if (s.target() != null) tag.putUUID("target", s.target());
        return tag;
    }
    static State load(CompoundTag tag, V position) {
        return new State(position, vector(tag, "velocity"), Phase.valueOf(tag.getString("phase")),
                tag.getDouble("startY"), vector(tag, "rest"), vector(tag, "up"),
                tag.getInt("total"), tag.getInt("remaining"), tag.getInt("settleFrames"),
                tag.getInt("idleFrames"), tag.getDouble("spin"), tag.getDouble("secondary"),
                tag.getInt("direction"), tag.getDouble("flySpeed"),
                tag.hasUUID("target") ? tag.getUUID("target") : null);
    }
    private static void vector(CompoundTag tag, String key, V v) {
        CompoundTag xyz = new CompoundTag();
        xyz.putDouble("x", v.x()); xyz.putDouble("y", v.y()); xyz.putDouble("z", v.z());
        tag.put(key, xyz);
    }
    private static V vector(CompoundTag tag, String key) {
        CompoundTag xyz = tag.getCompound(key);
        return new V(xyz.getDouble("x"), xyz.getDouble("y"), xyz.getDouble("z"));
    }
}
