package com.github.giannigr.racmc.clank;

import java.util.UUID;

/** Server-owned semantic session. Legacy client grant/action requests remain untrusted/denied. */
public final class Rac1ClankSession {
    public enum IntentKind { EQUIPMENT, GLIDE, LONG_JUMP, HIGH_JUMP }
    public record Intent(long epoch, long serial, IntentKind kind, int module, boolean worn) {
        public Intent {
            if (epoch < 0 || serial < 0 || kind == null) throw new IllegalArgumentException("Invalid intent");
            Rac1ClankEquipment.Module.fromSource(module);
        }
    }
    public record Snapshot(UUID player, long epoch, long revision, long serverTick,
            Rac1ClankEquipment equipment, Rac1ClankCapability.Action action,
            long actionSerial, long actionStartTick, int phaseSourceSteps) {
        public Snapshot {
            if (player == null || equipment == null || action == null || epoch < 0 || revision < 0
                    || serverTick < 0 || actionSerial < 0 || actionStartTick < 0
                    || actionStartTick > serverTick || phaseSourceSteps < 0 || phaseSourceSteps > 36000
                    || (action == Rac1ClankCapability.Action.IDLE && phaseSourceSteps != 0)
                    || (action != Rac1ClankCapability.Action.IDLE && !equipment.worn()))
                throw new IllegalArgumentException("Invalid Clank snapshot");
        }
    }
    private final UUID player;
    private long epoch, revision, lastSerial = -1;
    private Rac1ClankEquipment equipment;
    private final Rac1ClankMotion motion = new Rac1ClankMotion(Rac1ClankClock.Profile.ORIGINAL_60);
    public Rac1ClankSession(UUID player, long epoch, Rac1ClankEquipment saved) {
        if (player == null || epoch < 0 || saved == null) throw new IllegalArgumentException("Invalid session");
        this.player = player; this.epoch = epoch; equipment = saved;
    }
    public boolean intent(Intent intent) {
        if (intent.epoch() != epoch || intent.serial() <= lastSerial) return false;
        lastSerial = intent.serial();
        // Legacy direct equip/action-kind requests are not grant or movement authority.
        // The approved operator fixture and reviewed semantic-input travel path are separate.
        return false;
    }
    /** Dimension/death/logout invalidate transients and replay namespace, not a death save rule. */
    public void invalidate() {
        invalidate(Math.incrementExact(epoch));
    }
    public void invalidate(long nextEpoch) {
        if (nextEpoch <= epoch) throw new IllegalArgumentException("Epoch must advance");
        epoch = nextEpoch;
        revision = Math.incrementExact(revision);
        lastSerial = -1;
        motion.cancel();
    }
    public Snapshot snapshot(long serverTick) {
        return new Snapshot(player, epoch, revision, serverTick, equipment,
                Rac1ClankCapability.Action.IDLE, 0, 0, 0);
    }
    public long lastSerial() { return lastSerial; }

    /** Package-local explicit operator fixture only; never called from a client equip packet. */
    void testEquipment(Rac1ClankEquipment.Module module) {
        motion.cancel(); // Preserve this controller's airborne jump budget across equipment changes.
        equipment = switch (module) {
            case NONE -> Rac1ClankEquipment.EMPTY;
            case HELI -> new Rac1ClankEquipment(true, true, false, true, module);
            // Original Thruster glide also requires the separately owned Heli upgrade.
            case THRUSTER -> new Rac1ClankEquipment(true, true, true, true, module);
        };
        revision = Math.incrementExact(revision);
    }
}
