package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import com.github.giannigr.racmc.item.Rac1ClankItem.Stamp;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** UUID-scoped unlocks, issued token identities and persisted Back escrow, on the server thread. */
final class Rac1GearLedger extends SavedData {
    private static final int VERSION = 2;
    private static final UUID ZERO = new UUID(0, 0);
    private static final Record LOCKED = new Record(Rac1ClankEquipment.EMPTY, null, null);
    private final Map<UUID, Record> players = new HashMap<>();
    private final Map<UUID, UUID> issuers = new HashMap<>();
    private final Map<UUID, UUID> escrowOwners = new HashMap<>();
    private boolean available = true;

    record Reward(Stamp stamp, boolean delivered, UUID recipient) {}
    record Record(Rac1ClankEquipment back, Reward reward, UUID escrow) {
        Rac1ClankEquipment effectiveBack() {
            return back.worn() ? back : Rac1ClankEquipment.EMPTY;
        }
    }
    enum EquipResult { EQUIPPED, ALREADY_WORN, DENIED }

    static Rac1GearLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(Rac1GearLedger::new, Rac1GearLedger::load), "racmc_gear");
    }

    boolean available() { return available; }
    Record record(UUID player) { return players.getOrDefault(player, LOCKED); }

    Rac1GearServer.RescueResult rescue(UUID player) {
        if (!available) return Rac1GearServer.RescueResult.DENIED;
        if (record(player).back().acquired()) return Rac1GearServer.RescueResult.ALREADY_UNLOCKED;
        UUID token;
        do token = UUID.randomUUID(); while (issuers.putIfAbsent(token, player) != null);
        put(player, new Record(starter(false), new Reward(new Stamp(token, UUID.randomUUID()), false, null), null));
        return Rac1GearServer.RescueResult.NEWLY_UNLOCKED;
    }

    private static Rac1ClankEquipment starter(boolean worn) {
        return new Rac1ClankEquipment(true, true, false, worn, Rac1ClankEquipment.Module.HELI);
    }

    private static Rac1ClankEquipment withWear(Rac1ClankEquipment back, boolean worn) {
        return new Rac1ClankEquipment(back.acquired(), back.heliOwned(), back.thrusterOwned(), worn, back.selected());
    }

    Stamp pendingRescue(UUID player) {
        var reward = record(player).reward();
        return reward != null && !reward.delivered() ? reward.stamp() : null;
    }

    boolean finishRescueDelivery(UUID player, Stamp stamp) {
        var current = record(player);
        if (!available || !stamp.equals(pendingRescue(player))) return false;
        put(player, new Record(current.back(), new Reward(stamp, true, null), current.escrow()));
        return true;
    }

    private Reward issuance(UUID token) {
        UUID issuer = issuers.get(token);
        return issuer == null ? null : record(issuer).reward();
    }

    boolean looseAuthentic(Stamp stamp) {
        if (!available || stamp == null || escrowOwners.containsKey(stamp.token())) return false;
        Reward issuance = issuance(stamp.token());
        return issuance != null && issuance.delivered() && issuance.stamp().equals(stamp);
    }

    /** The native caller has validated the actual inventory slot. Stale copies cannot enter Back. */
    EquipResult equip(UUID player, Stamp stamp, boolean giftUse) {
        if (!looseAuthentic(stamp)) return EquipResult.DENIED;
        var target = record(player);
        if (target.back().worn()) return EquipResult.ALREADY_WORN;
        if (target.escrow() != null) return EquipResult.DENIED; // Previous return is still pending.
        if (!target.back().acquired()) {
            if (!giftUse) return EquipResult.DENIED;
            var issuance = issuance(stamp.token());
            if (issuance.recipient() != null) return EquipResult.DENIED;
            var issuer = issuers.get(stamp.token());
            var source = record(issuer);
            put(issuer, new Record(source.back(), new Reward(stamp, true, player), source.escrow()));
        }
        var worn = target.back().acquired() ? withWear(target.back(), true) : starter(true);
        escrowOwners.put(stamp.token(), player);
        put(player, new Record(worn, target.reward(), stamp.token()));
        return EquipResult.EQUIPPED;
    }

    /** Clear worn immediately. Until delivery succeeds, the same token remains in escrow. */
    boolean beginReturn(UUID player) {
        var target = record(player);
        if (!available || !target.back().worn()) return false;
        UUID token = target.escrow();
        UUID issuer = issuers.get(token);
        var source = record(issuer);
        // Same token, fresh loose-instance seal. Any old inventory/network copy stays invalid.
        put(issuer, new Record(source.back(),
                new Reward(new Stamp(token, UUID.randomUUID()), true, source.reward().recipient()), source.escrow()));
        target = record(player); // The issuer can also be the wearer.
        put(player, new Record(withWear(target.back(), false), target.reward(), token));
        return true;
    }

    Stamp pendingReturn(UUID player) {
        var record = record(player);
        return record.escrow() != null && !record.back().worn() ? issuance(record.escrow()).stamp() : null;
    }

    boolean finishReturn(UUID player, Stamp stamp) {
        if (!available || !stamp.equals(pendingReturn(player))) return false;
        var current = record(player);
        escrowOwners.remove(stamp.token());
        put(player, new Record(current.back(), current.reward(), null));
        return true;
    }

    private void put(UUID player, Record record) {
        players.put(player, record);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!available) throw new IllegalStateException("Unavailable Gear data must not be overwritten");
        var rows = new ListTag();
        players.forEach((player, record) -> {
            var entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putByteArray("back", record.back().save());
            if (record.escrow() != null) entry.putUUID("escrow", record.escrow());
            var reward = record.reward();
            if (reward != null) {
                entry.putUUID("token", reward.stamp().token());
                entry.putUUID("seal", reward.stamp().seal());
                entry.putBoolean("delivered", reward.delivered());
                if (reward.recipient() != null) entry.putUUID("recipient", reward.recipient());
            }
            rows.add(entry);
        });
        tag.putInt("version", VERSION);
        tag.put("players", rows);
        return tag;
    }

    static Rac1GearLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new Rac1GearLedger();
        try {
            if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != VERSION
                    || !(tag.get("players") instanceof ListTag entries)
                    || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND))
                throw new IOException("Unsupported Gear version or malformed data; pre-token saves require migration");
            for (Tag raw : entries) ledger.readRow((CompoundTag) raw);
            for (var row : ledger.players.entrySet()) {
                var record = row.getValue();
                if (record.escrow() != null) {
                    Reward reward = ledger.issuance(record.escrow());
                    if (reward == null || !reward.delivered()) throw new IOException("Escrow has no live token");
                }
                if (record.reward() != null && record.reward().recipient() != null
                        && !ledger.players.containsKey(record.reward().recipient()))
                    throw new IOException("Unknown Clank gift recipient");
            }
        } catch (IOException invalid) {
            ledger.players.clear();
            ledger.issuers.clear();
            ledger.escrowOwners.clear();
            ledger.available = false;
            RacMcMod.LOGGER.error("Gear data is unavailable and will not be overwritten", invalid);
        }
        return ledger;
    }

    private void readRow(CompoundTag entry) throws IOException {
        var back = Rac1ClankEquipment.load(entry.getByteArray("back"));
        UUID player = readId(entry, "player");
        UUID escrow = entry.contains("escrow") ? readId(entry, "escrow") : null;
        if (!back.acquired() || !back.heliOwned() || back.selected() == Rac1ClankEquipment.Module.NONE
                || (back.worn() && escrow == null)
                || (escrow != null && escrowOwners.putIfAbsent(escrow, player) != null))
            throw new IOException("Invalid Back record or duplicate escrow");
        Reward reward = null;
        if (entry.contains("token") || entry.contains("seal") || entry.contains("delivered")
                || entry.contains("recipient")) {
            UUID token = readId(entry, "token");
            UUID seal = readId(entry, "seal");
            if (!entry.contains("delivered", Tag.TAG_BYTE) || issuers.putIfAbsent(token, player) != null)
                throw new IOException("Malformed or duplicate Clank issuance");
            boolean delivered = entry.getBoolean("delivered");
            UUID recipient = entry.contains("recipient") ? readId(entry, "recipient") : null;
            if (recipient != null && (!delivered || player.equals(recipient)))
                throw new IOException("Invalid Clank gift");
            reward = new Reward(new Stamp(token, seal), delivered, recipient);
        }
        if (players.putIfAbsent(player, new Record(back, reward, escrow)) != null)
            throw new IOException("Duplicate Gear account");
    }

    private static UUID readId(CompoundTag tag, String key) throws IOException {
        if (!tag.hasUUID(key)) throw new IOException("Missing Gear identity");
        UUID id = tag.getUUID(key);
        if (ZERO.equals(id)) throw new IOException("Invalid Gear identity");
        return id;
    }
}
