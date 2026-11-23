package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.RacMcMod;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Versioned server-only Belt escrow, independent of the Back save file. */
final class Rac1BeltLedger extends SavedData {
    private static final Factory<Rac1BeltLedger> FACTORY =
            new Factory<>(Rac1BeltLedger::new, Rac1BeltLedger::load);
    private static final int VERSION = 1;
    private final Map<UUID, EnumMap<Rac1BeltGadget, Entry>> players = new HashMap<>();
    private final Set<UUID> delivering = new HashSet<>();
    private boolean available = true;

    private record Entry(ItemStack stack, UUID returnId) {}

    static Rac1BeltLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "racmc_belt");
    }

    boolean available() { return available; }

    private Entry entry(UUID id, Rac1BeltGadget gadget) {
        var slots = players.get(id);
        return slots == null ? null : slots.get(gadget);
    }

    boolean equipped(UUID id, Rac1BeltGadget gadget) {
        Entry entry = entry(id, gadget);
        return available && entry != null && entry.returnId() == null;
    }

    boolean returning(UUID id, Rac1BeltGadget gadget) {
        Entry entry = entry(id, gadget);
        return available && entry != null && entry.returnId() != null;
    }

    static int matchingSlot(Inventory inventory, Rac1BeltGadget gadget) {
        for (int i = 0; i < inventory.items.size(); i++)
            if (gadget.matches(inventory.getItem(i))) return i;
        return gadget.matches(inventory.getItem(Inventory.SLOT_OFFHAND)) ? Inventory.SLOT_OFFHAND : -1;
    }

    boolean equip(UUID id, Rac1BeltGadget gadget, Inventory inventory) {
        if (!available || entry(id, gadget) != null) return false;
        int slot = matchingSlot(inventory, gadget);
        if (slot < 0) return false;
        var stack = inventory.getItem(slot);
        put(id, gadget, new Entry(stack.copy(), null));
        inventory.setItem(slot, ItemStack.EMPTY);
        inventory.setChanged();
        return true;
    }

    boolean beginReturn(UUID id, Rac1BeltGadget gadget) {
        if (!equipped(id, gadget)) return false;
        put(id, gadget, new Entry(entry(id, gadget).stack(), UUID.randomUUID()));
        return true;
    }

    interface Delivery {
        /** True only for a confirmed earlier spawn of this same return. */
        boolean alreadyDropped(UUID returnId, ItemStack stack);
        /** False leaves escrow unchanged. */
        boolean drop(UUID returnId, ItemStack stack);
    }

    boolean deliver(UUID id, Rac1BeltGadget gadget, Inventory inventory, Delivery delivery) {
        if (!returning(id, gadget)) return true;
        var entry = entry(id, gadget);
        UUID receipt = entry.returnId();
        if (!delivering.add(receipt)) return false; // Spawn listeners can call back into the server.
        try {
            if (!delivery.alreadyDropped(receipt, entry.stack().copy())) {
                int free = inventory.getFreeSlot();
                if (free >= 0) {
                    inventory.setItem(free, entry.stack().copy());
                    inventory.setChanged();
                } else if (!delivery.drop(receipt, entry.stack().copy())) {
                    return false;
                }
            }
            var slots = players.get(id);
            slots.remove(gadget);
            if (slots.isEmpty()) players.remove(id);
            setDirty();
            return true;
        } finally {
            delivering.remove(receipt);
        }
    }

    private void put(UUID id, Rac1BeltGadget gadget, Entry entry) {
        players.computeIfAbsent(id, unused -> new EnumMap<>(Rac1BeltGadget.class)).put(gadget, entry);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!available) throw new IllegalStateException("Unavailable Belt data must not be overwritten");
        var rows = new ListTag();
        players.forEach((id, slots) -> slots.forEach((gadget, entry) -> {
            var row = new CompoundTag();
            row.putUUID("player", id);
            row.putString("gadget", gadget.id());
            row.put("stack", entry.stack().save(registries));
            if (entry.returnId() != null) row.putUUID("return", entry.returnId());
            rows.add(row);
        }));
        tag.putInt("version", VERSION);
        tag.put("slots", rows);
        return tag;
    }

    static Rac1BeltLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new Rac1BeltLedger();
        try {
            if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != VERSION
                    || !(tag.get("slots") instanceof ListTag rows)
                    || (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND))
                throw new IOException("Unsupported or malformed Belt data");
            Set<UUID> receipts = new HashSet<>();
            for (var raw : rows) {
                var row = (CompoundTag) raw;
                if (!row.hasUUID("player") || !row.contains("gadget", Tag.TAG_STRING)
                        || (row.contains("return") && !row.hasUUID("return")))
                    throw new IOException("Missing Belt row identity");
                var gadget = Rac1BeltGadget.fromId(row.getString("gadget"));
                var stack = ItemStack.parse(registries, row.getCompound("stack"))
                        .orElseThrow(() -> new IOException("Missing Belt stack"));
                UUID receipt = row.hasUUID("return") ? row.getUUID("return") : null;
                var slots = ledger.players.computeIfAbsent(row.getUUID("player"), unused ->
                        new EnumMap<>(Rac1BeltGadget.class));
                if (!gadget.matches(stack) || (receipt != null && !receipts.add(receipt))
                        || slots.putIfAbsent(gadget, new Entry(stack, receipt)) != null)
                    throw new IOException("Wrong or duplicate Belt escrow row");
            }
        } catch (IOException | IllegalArgumentException invalid) {
            ledger.players.clear();
            ledger.available = false;
            RacMcMod.LOGGER.error("Belt data is unavailable and will not be overwritten", invalid);
        }
        return ledger;
    }
}
