package com.github.giannigr.racmc.clank;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Durable facts only. Construction is data validation, not a grant/equip authorization. */
public record Rac1ClankEquipment(boolean acquired, boolean heliOwned, boolean thrusterOwned,
                                 boolean worn, Module selected) {
    public enum Module {
        NONE(0), HELI(2), THRUSTER(3);
        private final int sourceId;
        Module(int sourceId) { this.sourceId = sourceId; }
        public int sourceId() { return sourceId; }
        public static Module fromSource(int id) {
            for (var module : values()) if (module.sourceId == id) return module;
            throw new IllegalArgumentException("Unknown wearable module");
        }
    }
    public static final Rac1ClankEquipment EMPTY =
            new Rac1ClankEquipment(false, false, false, false, Module.NONE);

    public Rac1ClankEquipment {
        if (selected == null || (!acquired && (heliOwned || thrusterOwned || worn
                || selected != Module.NONE)) || (selected == Module.THRUSTER && !thrusterOwned))
            throw new IllegalArgumentException("Inconsistent Clank equipment");
        // Folded Heli can be selected/visible before its upgrade is owned in the original.
    }

    /** Own fixed seven-byte save scope; never serializes action, media or a client grant. */
    public byte[] save() {
        int flags = (acquired ? 1 : 0) | (heliOwned ? 2 : 0) | (thrusterOwned ? 4 : 0)
                | (worn ? 8 : 0);
        return ByteBuffer.allocate(7).putInt(0x434c4551).put((byte) 1)
                .put((byte) flags).put((byte) selected.sourceId).array();
    }

    public static Rac1ClankEquipment load(byte[] bytes) throws IOException {
        if (bytes.length != 7) throw new IOException("Invalid Clank equipment size");
        var in = ByteBuffer.wrap(bytes);
        if (in.getInt() != 0x434c4551 || in.get() != 1)
            throw new IOException("Unknown Clank equipment version");
        int flags = Byte.toUnsignedInt(in.get());
        if ((flags & ~15) != 0) throw new IOException("Unknown equipment flags");
        try {
            return new Rac1ClankEquipment((flags & 1) != 0, (flags & 2) != 0,
                    (flags & 4) != 0, (flags & 8) != 0,
                    Module.fromSource(Byte.toUnsignedInt(in.get())));
        } catch (IllegalArgumentException bad) {
            throw new IOException("Malformed Clank equipment", bad);
        }
    }
}
