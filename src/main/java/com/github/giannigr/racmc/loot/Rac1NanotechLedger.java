package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.item.RacMcEntities;
import com.github.giannigr.racmc.sound.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;

/** Server-thread ownership. One source UUID -> one cluster -> one heal. Disposable entity views.
 * Normal graceful save consistency only: player HP and this SavedData are not crash-atomic. */
public final class Rac1NanotechLedger extends SavedData {
    static final int BUDGET = 128;
    private final Set<UUID> released = new HashSet<>();
    private final Map<UUID, Cluster> pending = new TreeMap<>();
    private CompoundTag quarantine;
    private int cursor;
    public static final class Cluster {
        final UUID id, source;
        final ResourceKey<Level> dimension;
        final long seed;
        Vec3 position, safe;
        double velocity;
        UUID receiver;
        ServerPlayer receiverInstance; // lifetime identity: never heal a respawn/rejoin replacement with the same UUID
        int age;
        boolean committed;
        Cluster(UUID id, UUID source, ResourceKey<Level> dimension, Vec3 position, long seed) {
            this.id = id; this.source = source; this.dimension = dimension;
            this.position = position; this.safe = position; this.seed = seed;
        }
    }
    public static Rac1NanotechLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Rac1NanotechLedger::new, Rac1NanotechLedger::load), "racmc_nanotech");
    }
    public int pendingCount() { return (int) pending.values().stream().filter(c -> !c.committed).count(); }
    public boolean released(UUID source) { return released.contains(source); }
    Cluster cluster(UUID id) { return pending.get(id); }
    public void release(UUID source, ServerLevel level, Vec3 position) {
        release(source, level.dimension(), position, source.getLeastSignificantBits());
    }
    boolean release(UUID source, ResourceKey<Level> dimension, Vec3 position, long seed) {
        if (quarantine != null || !valid(position) || !released.add(source)) return false;
        // Stable distinct view identity; never regenerate on spawn failure or load.
        UUID id = UUID.nameUUIDFromBytes(("racmc:nanotech:" + source).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        pending.put(id, new Cluster(id, source, dimension, position, seed));
        setDirty();
        return true;
    }
    private static boolean living(ServerPlayer p, ServerLevel level) {
        return p != null && p.isAlive() && !p.isSpectator() && !p.hasDisconnected()
                && p.serverLevel() == level && Float.isFinite(p.getHealth())
                && Float.isFinite(p.getMaxHealth()) && p.getHealth() > 0 && p.getMaxHealth() > 0;
    }
    private static void cancel(Cluster c) {
        c.receiver = null; c.receiverInstance = null; c.age = 0; c.position = c.safe; c.velocity = 0;
    }
    public void tick(ServerLevel level) {
        if (quarantine != null) return;
        // Bounded per-level active work; round-robin pending records, no force loading or global world scan.
        List<Cluster> records = pending.values().stream().filter(c -> c.dimension == level.dimension()).toList();
        if (records.isEmpty()) return;
        Map<UUID, Integer> incoming = new HashMap<>();
        for (var c : pending.values())
            if (!c.committed && c.receiver != null) incoming.merge(c.receiver, 1, Integer::sum);
        int start = Math.floorMod(cursor, records.size()), count = Math.min(BUDGET, records.size());
        int views = 0;
        for (var c : records) if (level.getEntity(c.id) instanceof Rac1NanotechEntity) views++;
        for (int n = 0; n < count; n++) {
            Cluster c = records.get((start + n) % records.size());
            if (!level.hasChunkAt(BlockPos.containing(c.position))) {
                if (c.receiver != null && !c.committed) { unreserve(incoming, c); cancel(c); setDirty(); }
                continue;
            }
            var existing = level.getEntity(c.id);
            Rac1NanotechEntity view = existing instanceof Rac1NanotechEntity e ? e : null;
            if (existing != null && view == null) continue; // UUID conflict: fail closed, no new identity
            if (c.receiver != null && view == null && !c.committed) {
                unreserve(incoming, c); cancel(c); // unload recovery
            }
            if (view == null && views < BUDGET) {
                view = new Rac1NanotechEntity(RacMcEntities.NANOTECH.get(), level);
                view.setUUID(c.id); view.snapshot(c);
                if (!level.addFreshEntity(view)) view = null;
                else views++;
            }
            // Admission failure/cap never consumes a cluster.
            if (view == null) continue;
            if (c.receiver == null) {
                fall(level, c, view);
                ServerPlayer receiver = level.players().stream().filter(p -> living(p, level)
                        && Rac1NanotechRules.eligible(p.getHealth(), p.getMaxHealth(), incoming.getOrDefault(p.getUUID(), 0))
                        && Rac1NanotechRules.inRange(p.position().distanceToSqr(c.position))
                        && level.clip(new ClipContext(c.position.add(0, .5, 0), p.position().add(0, .5, 0),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewForRay(level, c.id)))
                                .getType() == HitResult.Type.MISS)
                        .min(Comparator.comparingDouble((ServerPlayer p) -> p.position().distanceToSqr(c.position))
                                .thenComparing(p -> p.getUUID().toString())).orElse(null);
                if (receiver != null) {
                    c.receiver = receiver.getUUID(); c.age = 0;
                    c.receiverInstance = receiver;
                    incoming.merge(c.receiver, 1, Integer::sum);
                }
            } else {
                ServerPlayer p = level.getPlayerByUUID(c.receiver) instanceof ServerPlayer player ? player : null;
                if (!c.committed && (!living(p, level) || p != c.receiverInstance)) {
                    unreserve(incoming, c); cancel(c);
                }
                else {
                    c.age++;
                    if (!c.committed && c.age >= Rac1NanotechRules.firstArrival(c.seed)) {
                        // Terminal ownership before callbacks: no second heal/cue, even for eight equal arrivals.
                        c.committed = true;
                        c.receiverInstance = null;
                        unreserve(incoming, c);
                        p.setHealth(Rac1NanotechRules.arrivalHealth(p.getHealth(), p.getMaxHealth()));
                        Rac1AudioSignal.emit(level, p.position(), Rac1AudioEvent.NANOTECH_FIRST_ARRIVAL);
                    }
                    if (c.age >= 28) {
                        pending.remove(c.id); view.discard(); setDirty(); continue;
                    }
                }
            }
            view.snapshot(c);
            setDirty();
        }
        cursor = (start + count) % records.size();
    }
    private static void unreserve(Map<UUID, Integer> incoming, Cluster c) {
        if (c.receiver != null) incoming.computeIfPresent(c.receiver, (id, count) -> Math.max(0, count - 1));
    }
    private static net.minecraft.world.entity.Entity viewForRay(ServerLevel level, UUID id) {
        return level.getEntity(id);
    }
    private static void fall(ServerLevel level, Cluster c, Rac1NanotechEntity view) {
        // No radial bolt spill; MC voxel/gravity adapter, capped .3 original units per60Hz update.
        c.velocity = Math.max(-.9, c.velocity - .0245);
        Vec3 next = c.position.add(0, c.velocity, 0);
        var hit = level.clip(new ClipContext(c.position.add(0, .05, 0), next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, view));
        if (hit.getType() != HitResult.Type.MISS) { next = hit.getLocation(); c.velocity = 0; }
        if (next.y < level.getMinBuildHeight()) { next = c.safe; c.velocity = 0; }
        else if (level.hasChunkAt(BlockPos.containing(next)) && hit.getType() != HitResult.Type.MISS) c.safe = next;
        c.position = next;
    }
    static boolean valid(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && Math.abs(p.x) <= 30_000_000 && Math.abs(p.z) <= 30_000_000 && Math.abs(p.y) <= 20_000_000;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (quarantine != null) return quarantine.copy();
        tag.putInt("schema", 1);
        ListTag sources = new ListTag(), clusters = new ListTag();
        released.forEach(id -> { var t = new CompoundTag(); t.putUUID("source", id); sources.add(t); });
        for (Cluster c : pending.values()) {
            if (c.committed) continue; // no committed audio/heal replay
            var t = new CompoundTag();
            t.putUUID("id", c.id); t.putUUID("source", c.source);
            t.putString("dimension", c.dimension.location().toString()); t.putLong("seed", c.seed);
            // Graceful restart explicitly cancels uncommitted flight at its last safe position.
            Vec3 p = c.receiver == null ? c.position : c.safe;
            putPosition(t, "p", p); putPosition(t, "safe", c.safe);
            t.putDouble("velocity", c.receiver == null ? c.velocity : 0);
            clusters.add(t);
        }
        tag.put("released", sources); tag.put("clusters", clusters);
        return tag;
    }
    private static void putPosition(CompoundTag tag, String key, Vec3 p) {
        var t = new CompoundTag(); t.putDouble("x", p.x); t.putDouble("y", p.y); t.putDouble("z", p.z); tag.put(key, t);
    }
    private static Vec3 position(CompoundTag tag, String key) {
        var t = tag.getCompound(key);
        if (!t.contains("x", Tag.TAG_DOUBLE) || !t.contains("y", Tag.TAG_DOUBLE) || !t.contains("z", Tag.TAG_DOUBLE))
            throw new IllegalArgumentException("Missing nanotech position");
        var p = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
        if (!valid(p)) throw new IllegalArgumentException("Invalid nanotech position");
        return p;
    }
    static Rac1NanotechLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        try { return decode(tag); }
        catch (RuntimeException malformed) {
            // DimensionDataStorage may treat a throwing loader as absent data. Return a disabled owner,
            // preserve the input, and refuse new rewards instead of silently creating an empty ledger.
            var ledger = new Rac1NanotechLedger();
            ledger.quarantine = tag.copy();
            com.github.giannigr.racmc.RacMcMod.LOGGER.error(
                    "Nanotech ledger quarantined; releases/healing disabled, original NBT retained: {}", malformed.toString());
            return ledger;
        }
    }
    static Rac1NanotechLedger decode(CompoundTag tag) {
        var ledger = new Rac1NanotechLedger();
        if (tag.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported nanotech ledger");
        if (!tag.contains("released", Tag.TAG_LIST) || !tag.contains("clusters", Tag.TAG_LIST))
            throw new IllegalArgumentException("Missing nanotech ownership lists");
        for (String key : List.of("released", "clusters")) {
            var list = (ListTag) tag.get(key);
            if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                throw new IllegalArgumentException("Invalid nanotech ownership list type");
        }
        if (tag.getList("released", Tag.TAG_COMPOUND).size() > 1_000_000
                || tag.getList("clusters", Tag.TAG_COMPOUND).size() > 1_000_000)
            throw new IllegalArgumentException("Oversized nanotech ledger");
        for (Tag entry : tag.getList("released", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) entry;
            if (!t.hasUUID("source") || !ledger.released.add(t.getUUID("source")))
                throw new IllegalArgumentException("Invalid/duplicate nanotech tombstone");
        }
        Set<UUID> sources = new HashSet<>();
        for (Tag entry : tag.getList("clusters", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) entry;
            if (!t.contains("seed", Tag.TAG_LONG) || !t.contains("velocity", Tag.TAG_DOUBLE))
                throw new IllegalArgumentException("Missing nanotech motion fields");
            // Fail closed rather than minting a replacement identity.
            UUID id = t.getUUID("id"), source = t.getUUID("source");
            if (!ledger.released.contains(source) || !sources.add(source) || ledger.pending.containsKey(id))
                throw new IllegalArgumentException("Conflicting nanotech ownership");
            UUID expected = UUID.nameUUIDFromBytes(("racmc:nanotech:" + source).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!id.equals(expected)) throw new IllegalArgumentException("Invalid nanotech view identity");
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dimension")));
            Cluster c = new Cluster(id, source, dimension, position(t, "p"), t.getLong("seed"));
            c.safe = position(t, "safe"); c.velocity = t.getDouble("velocity");
            if (!Double.isFinite(c.velocity) || c.velocity < -.9 || c.velocity > 0)
                throw new IllegalArgumentException("Invalid nanotech velocity");
            ledger.pending.put(id, c);
        }
        return ledger;
    }
}
