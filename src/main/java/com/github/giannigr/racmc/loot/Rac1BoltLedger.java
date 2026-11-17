package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.item.RacMcEntities;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.State;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.V;

/** Canonical world accounts and unclaimed value. Entities are disposable views of this ledger.
 * Normal save/restart consistency, NOT fsync/crash atomicity. No expiry or death balance loss. */
public final class Rac1BoltLedger extends SavedData {
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Long> revisions = new HashMap<>();
    private final Map<UUID, Pickup> pickups = new LinkedHashMap<>();
    private final Set<UUID> rewarded = new HashSet<>();

    public record Pickup(UUID id, ResourceKey<Level> dimension, double x, double y, double z,
                         int value, State motion) {}

    public static Rac1BoltLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Rac1BoltLedger::new, Rac1BoltLedger::load), "racmc_bolts");
    }

    public long balance(UUID player) { return balances.getOrDefault(player, 0L); }
    public long revision(UUID player) { return revisions.getOrDefault(player, 0L); }
    public Pickup pickup(UUID id) { return pickups.get(id); }

    public void reward(UUID incarnation, ServerLevel level, BlockPos pos, int total) {
        if (!rewarded.add(incarnation)) return;
        for (int value : Rac1BoltPayout.split(total)) {
            UUID id = UUID.randomUUID();
            // Source is crate bottom-center, not block center plus another half-block.
            State motion = Rac1BoltMotion.launch(new V(pos.getX() + .5, pos.getY(), pos.getZ() + .5),
                    new Random(level.random.nextLong()));
            V p = motion.position();
            pickups.put(id, new Pickup(id, level.dimension(), p.x(), p.y(), p.z(), value, motion));
        }
        setDirty();
    }

    /** Main server thread only. Reject overflow without consuming the uncredited pickup. */
    public boolean collect(UUID id, ServerPlayer player) {
        Pickup pickup = pickups.get(id);
        if (pickup == null || !player.isAlive() || player.isSpectator()
                || player.level().dimension() != pickup.dimension()) return false;
        long before = balance(player.getUUID());
        if (before > Long.MAX_VALUE - pickup.value()) return false;
        pickups.remove(id);
        balances.put(player.getUUID(), before + pickup.value());
        revisions.merge(player.getUUID(), 1L, Long::sum);
        setDirty();
        Rac1BoltNetwork.send(player, this, true);
        return true;
    }

    /** Future vendor integration uses this account, never inventory cosmetic items. */
    public boolean tryDebit(ServerPlayer player, long amount) {
        if (amount < 0 || balance(player.getUUID()) < amount) return false;
        balances.put(player.getUUID(), balance(player.getUUID()) - amount);
        revisions.merge(player.getUUID(), 1L, Long::sum);
        setDirty();
        Rac1BoltNetwork.send(player, this, false);
        return true;
    }

    public void move(UUID id, double x, double y, double z) {
        Pickup old = pickups.get(id);
        if (old == null) return;
        State motion = old.motion() == null ? null : Rac1BoltMotion.relocated(old.motion(), new V(x, y, z));
        pickups.put(id, new Pickup(id, old.dimension(), x, y, z, old.value(), motion));
        setDirty();
    }

    public void move(UUID id, State motion) {
        Pickup old = pickups.get(id);
        if (old == null) return;
        V p = motion.position();
        pickups.put(id, new Pickup(id, old.dimension(), p.x(), p.y(), p.z(), old.value(), motion));
        setDirty();
    }

    public void emit(ServerLevel level) {
        emitPending(level.dimension(),
                p -> level.hasChunkAt(BlockPos.containing(p.x(), p.y(), p.z())),
                id -> level.getEntity(id) != null, pickup -> {
            Rac1BoltEntity entity = new Rac1BoltEntity(RacMcEntities.BOLT.get(), level);
            entity.setUUID(pickup.id());
            entity.setValue(pickup.value());
            entity.restoreMotion(pickup.motion() == null
                    ? Rac1BoltMotion.legacy(new V(pickup.x(), pickup.y(), pickup.z())) : pickup.motion());
            return level.addFreshEntity(entity);
        });
    }

    /** Admission boundary, also exercised without a running world. Views never consume value. */
    void emitPending(ResourceKey<Level> dimension, Predicate<Pickup> chunkLoaded,
                     Predicate<UUID> entityPresent, Predicate<Pickup> admission) {
        for (Pickup pickup : List.copyOf(pickups.values())) {
            if (pickup.dimension() != dimension || !chunkLoaded.test(pickup)
                    || entityPresent.test(pickup.id())) continue;
            // Both success and failure retain the exact record. Only collect consumes currency.
            admission.test(pickup);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag accounts = new ListTag();
        balances.forEach((id, value) -> {
            CompoundTag account = new CompoundTag();
            account.putUUID("id", id); account.putLong("balance", value);
            account.putLong("revision", revision(id)); accounts.add(account);
        });
        tag.put("accounts", accounts);
        ListTag pending = new ListTag();
        pickups.values().forEach(p -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", p.id()); value.putString("dimension", p.dimension().location().toString());
            value.putDouble("x", p.x()); value.putDouble("y", p.y()); value.putDouble("z", p.z());
            value.putInt("value", p.value());
            if (p.motion() != null) value.put("motion", Rac1BoltMotionTag.save(p.motion()));
            pending.add(value);
        });
        tag.put("pickups", pending);
        ListTag consumed = new ListTag();
        rewarded.forEach(id -> { CompoundTag entry = new CompoundTag(); entry.putUUID("id", id); consumed.add(entry); });
        tag.put("rewarded", consumed);
        return tag;
    }

    static Rac1BoltLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        Rac1BoltLedger ledger = new Rac1BoltLedger();
        for (Tag entry : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag account = (CompoundTag) entry;
            UUID id = account.getUUID("id");
            ledger.balances.put(id, Math.max(0, account.getLong("balance")));
            ledger.revisions.put(id, account.getLong("revision"));
        }
        for (Tag entry : tag.getList("pickups", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) entry;
            UUID id = value.getUUID("id");
            int amount = value.getInt("value");
            if (amount != 1 && amount != 5 && amount != 20 && amount != 50)
                throw new IllegalArgumentException("Invalid saved bolt denomination");
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(value.getString("dimension")));
            V position = new V(value.getDouble("x"), value.getDouble("y"), value.getDouble("z"));
            State motion = value.contains("motion", Tag.TAG_COMPOUND)
                    ? Rac1BoltMotionTag.load(value.getCompound("motion"), position) : null;
            ledger.pickups.put(id, new Pickup(id, dimension, position.x(), position.y(), position.z(), amount, motion));
        }
        for (Tag entry : tag.getList("rewarded", Tag.TAG_COMPOUND))
            ledger.rewarded.add(((CompoundTag) entry).getUUID("id"));
        return ledger;
    }
}
