package com.github.giannigr.racmc.gear;

import static com.github.giannigr.racmc.gear.Rac1BeltGadget.BOLT_GRABBER;
import static com.github.giannigr.racmc.gear.Rac1BeltState.Availability.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.item.RacMcItems;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class Rac1BeltTest {
    private final UUID a = new UUID(1, 1);
    private final UUID b = new UUID(1, 2);

    @Test void typedEscrowConsumesOneActualItemAndRestoresItsComponents() {
        var ledger = new Rac1BeltLedger();
        var inventory = new Inventory(null);
        inventory.setItem(0, new ItemStack(RacMcItems.CLANK_HELI_PACK.get()));
        assertFalse(ledger.equip(a, BOLT_GRABBER, inventory));
        assertFalse(ledger.equipped(a, BOLT_GRABBER));
        var gadget = namedGadget();
        inventory.setItem(Inventory.SLOT_OFFHAND, gadget.copy());
        assertTrue(ledger.equip(a, BOLT_GRABBER, inventory));
        assertTrue(inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty());
        assertTrue(ledger.equipped(a, BOLT_GRABBER));
        assertFalse(ledger.equipped(b, BOLT_GRABBER));
        inventory.setItem(2, new ItemStack(RacMcItems.BOLT_GRABBER.get()));
        assertFalse(ledger.equip(a, BOLT_GRABBER, inventory));
        assertFalse(inventory.getItem(2).isEmpty());

        ledger = reopen(ledger);
        assertTrue(ledger.equipped(a, BOLT_GRABBER));
        assertTrue(ledger.beginReturn(a, BOLT_GRABBER));
        assertFalse(ledger.equipped(a, BOLT_GRABBER));
        assertFalse(ledger.equip(a, BOLT_GRABBER, inventory));
        var never = delivery((id, stack) -> fail("Room in inventory"), (id, stack) -> false);
        assertTrue(ledger.deliver(a, BOLT_GRABBER, inventory, never));
        assertTrue(ledger.deliver(a, BOLT_GRABBER, inventory, never));
        assertTrue(ItemStack.matches(gadget, inventory.getItem(1)));
        assertTrue(inventory.getItem(3).isEmpty());
        assertFalse(ledger.returning(a, BOLT_GRABBER));
    }

    @Test void fullInventoryDropFailureIsDurableAndReentrantRetryCannotDuplicate() {
        var ledger = new Rac1BeltLedger();
        var inventory = new Inventory(null);
        inventory.setItem(0, namedGadget());
        assertTrue(ledger.equip(a, BOLT_GRABBER, inventory));
        for (int i = 0; i < inventory.items.size(); i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
        assertTrue(ledger.beginReturn(a, BOLT_GRABBER));
        var receipts = new ArrayList<UUID>();
        assertFalse(ledger.deliver(a, BOLT_GRABBER, inventory, delivery((id, stack) -> {
            receipts.add(id);
            return false;
        }, (id, stack) -> false)));
        var reopened = reopen(ledger);
        assertTrue(reopened.returning(a, BOLT_GRABBER));
        assertFalse(reopened.equipped(a, BOLT_GRABBER));
        var success = delivery((id, stack) -> {
            assertEquals(receipts.getFirst(), id);
            assertTrue(ItemStack.matches(namedGadget(), stack));
            assertFalse(reopened.deliver(a, BOLT_GRABBER, inventory, delivery(
                    (rid, rs) -> fail("Reentrant drop"), (rid, rs) -> false)));
            return receipts.add(id);
        }, (id, stack) -> false);
        assertTrue(reopened.deliver(a, BOLT_GRABBER, inventory, success));
        assertTrue(reopened.deliver(a, BOLT_GRABBER, inventory, success));
        assertEquals(2, receipts.size());
    }

    @Test void confirmedExistingDropDoesNotDuplicateIntoNewFreeSlot() {
        var ledger = new Rac1BeltLedger();
        var inventory = new Inventory(null);
        inventory.setItem(0, namedGadget());
        assertTrue(ledger.equip(a, BOLT_GRABBER, inventory));
        assertTrue(ledger.beginReturn(a, BOLT_GRABBER));
        var confirmed = delivery((id, stack) -> fail("Already dropped"), (id, stack) -> true);
        assertTrue(ledger.deliver(a, BOLT_GRABBER, inventory, confirmed));
        assertTrue(inventory.getItem(0).isEmpty());
        assertFalse(ledger.returning(a, BOLT_GRABBER));
    }

    @Test void privateWireAndServerRevisionRejectStaleAndWrongSlotRequests() {
        var state = new Rac1BeltState(2, new Rac1BeltState.Slot(8, READY));
        var request = new Rac1BeltNetwork.Equip(BOLT_GRABBER, 2, 8, true);
        var packet = new Rac1BeltNetwork.Snapshot(state);
        assertTrue(Rac1BeltServer.accepts(state, request));
        assertFalse(Rac1BeltServer.accepts(new Rac1BeltState(3, state.boltGrabber()), request));
        for (var availability : java.util.List.of(MISSING, EQUIPPED, RETURNING))
            assertFalse(Rac1BeltServer.accepts(new Rac1BeltState(2, new Rac1BeltState.Slot(8, availability)), request));
        assertFalse(Rac1BeltServer.accepts(new Rac1BeltState(2, new Rac1BeltState.Slot(9, READY)), request));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            Rac1BeltNetwork.Snapshot.CODEC.encode(buf, packet);
            assertEquals(17, buf.readableBytes());
            assertEquals(packet, Rac1BeltNetwork.Snapshot.CODEC.decode(buf));
            Rac1BeltNetwork.Equip.CODEC.encode(buf, request);
            assertEquals(request, Rac1BeltNetwork.Equip.CODEC.decode(buf));
            buf.clear().writeByte(0).writeLong(2).writeLong(8).writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> Rac1BeltNetwork.Equip.CODEC.decode(buf));
            buf.clear().writeByte(1).writeLong(2).writeLong(8).writeByte(2);
            assertThrows(IllegalArgumentException.class, () -> Rac1BeltNetwork.Equip.CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test void unsupportedBeltSaveFailsClosedIndependentlyOfGear() {
        var raw = new Rac1BeltLedger().save(new CompoundTag(), RegistryAccess.EMPTY);
        raw.putInt("version", 255);
        var ledger = Rac1BeltLedger.load(raw, RegistryAccess.EMPTY);
        assertFalse(ledger.available());
        assertFalse(ledger.equipped(a, BOLT_GRABBER));
        assertThrows(IllegalStateException.class, () -> ledger.save(new CompoundTag(), RegistryAccess.EMPTY));
    }

    private static ItemStack namedGadget() {
        var stack = new ItemStack(RacMcItems.BOLT_GRABBER.get());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Same physical gadget"));
        return stack;
    }

    private static Rac1BeltLedger reopen(Rac1BeltLedger ledger) {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var reopened = Rac1BeltLedger.load(ledger.save(new CompoundTag(), registries), registries);
        assertTrue(reopened.available());
        return reopened;
    }

    private static Rac1BeltLedger.Delivery delivery(BiPredicate<UUID, ItemStack> drop,
                                                   BiPredicate<UUID, ItemStack> existing) {
        return new Rac1BeltLedger.Delivery() {
            @Override public boolean alreadyDropped(UUID id, ItemStack stack) { return existing.test(id, stack); }
            @Override public boolean drop(UUID id, ItemStack stack) { return drop.test(id, stack); }
        };
    }
}
