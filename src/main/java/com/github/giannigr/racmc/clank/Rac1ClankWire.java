package com.github.giannigr.racmc.clank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

/** Fixed-size semantic-only wire. No model, texture, joint, path or source-disc identity. */
public final class Rac1ClankWire {
    public static final int SNAPSHOT_BYTES = 68;
    public static final int INTENT_BYTES = 19;
    private Rac1ClankWire() {}
    public static byte[] encode(Rac1ClankSession.Snapshot s) {
        return ByteBuffer.allocate(SNAPSHOT_BYTES).putLong(s.player().getMostSignificantBits())
                .putLong(s.player().getLeastSignificantBits()).putLong(s.epoch()).putLong(s.revision())
                .putLong(s.serverTick()).put(s.equipment().save()).put((byte) s.action().ordinal())
                .putLong(s.actionSerial()).putLong(s.actionStartTick()).putInt(s.phaseSourceSteps()).array();
    }
    public static Rac1ClankSession.Snapshot snapshot(byte[] bytes) throws IOException {
        if (bytes.length != SNAPSHOT_BYTES) throw new IOException("Invalid semantic snapshot size");
        try {
            var in = ByteBuffer.wrap(bytes);
            UUID player = new UUID(in.getLong(), in.getLong());
            long epoch = in.getLong(), revision = in.getLong(), tick = in.getLong();
            byte[] equipment = new byte[7];
            in.get(equipment);
            int action = Byte.toUnsignedInt(in.get());
            if (action >= Rac1ClankCapability.Action.values().length)
                throw new IOException("Unknown Clank action");
            return new Rac1ClankSession.Snapshot(player, epoch, revision, tick,
                    Rac1ClankEquipment.load(equipment), Rac1ClankCapability.Action.values()[action],
                    in.getLong(), in.getLong(), in.getInt());
        } catch (IllegalArgumentException failure) {
            throw new IOException("Malformed Clank snapshot", failure);
        }
    }
    public static byte[] encode(Rac1ClankSession.Intent intent) {
        return ByteBuffer.allocate(INTENT_BYTES).putLong(intent.epoch()).putLong(intent.serial())
                .put((byte) intent.kind().ordinal()).put((byte) intent.module())
                .put((byte) (intent.worn() ? 1 : 0)).array();
    }
    public static Rac1ClankSession.Intent intent(byte[] bytes) throws IOException {
        if (bytes.length != INTENT_BYTES) throw new IOException("Invalid semantic intent size");
        try {
            var in = ByteBuffer.wrap(bytes);
            long epoch = in.getLong(), serial = in.getLong();
            int kind = Byte.toUnsignedInt(in.get()), module = Byte.toUnsignedInt(in.get());
            int worn = Byte.toUnsignedInt(in.get());
            if (kind >= Rac1ClankSession.IntentKind.values().length || worn > 1)
                throw new IOException("Invalid semantic intent fields");
            return new Rac1ClankSession.Intent(epoch, serial,
                    Rac1ClankSession.IntentKind.values()[kind], module, worn == 1);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Malformed Clank intent", failure);
        }
    }
}
