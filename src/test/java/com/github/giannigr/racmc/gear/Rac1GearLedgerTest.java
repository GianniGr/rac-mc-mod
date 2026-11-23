package com.github.giannigr.racmc.gear;

import static com.github.giannigr.racmc.gear.Rac1GearLedger.EquipResult.*;
import static com.github.giannigr.racmc.gear.Rac1GearServer.RescueResult.ALREADY_UNLOCKED;
import static com.github.giannigr.racmc.gear.Rac1GearServer.RescueResult.NEWLY_UNLOCKED;
import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import com.github.giannigr.racmc.item.Rac1ClankItem;
import com.github.giannigr.racmc.item.Rac1ClankItem.Stamp;
import com.github.giannigr.racmc.item.RacMcItems;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.Test;

class Rac1GearLedgerTest {
    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(1, 2);
    private static final UUID C = new UUID(1, 3);

    @Test void naturalRescueUnlocksOnceAndWaitsAtFullInventory() {
        var ledger = new Rac1GearLedger();
        var inventory = fullInventory();
        assertEquals(NEWLY_UNLOCKED, ledger.rescue(A));
        assertFalse(ledger.record(A).back().worn());
        assertTrue(ledger.record(A).back().heliOwned());
        assertFalse(ledger.record(A).back().thrusterOwned());
        var stamp = ledger.pendingRescue(A);
        Rac1GearServer.deliverRescue(inventory, A, ledger);
        assertEquals(stamp, ledger.pendingRescue(A));
        assertFalse(ledger.looseAuthentic(stamp));
        assertEquals(ALREADY_UNLOCKED, ledger.rescue(A));
        inventory.setItem(11, ItemStack.EMPTY);
        Rac1GearServer.deliverRescue(inventory, A, ledger);
        assertEquals(stamp, Rac1ClankItem.rewardId(inventory.getItem(11)));
        assertNull(ledger.pendingRescue(A));
        assertTrue(ledger.looseAuthentic(stamp));
        inventory.setItem(12, ItemStack.EMPTY);
        Rac1GearServer.deliverRescue(inventory, A, ledger);
        assertTrue(inventory.getItem(12).isEmpty());
    }

    @Test void aUnequipsAndTransfersOneRealStackToBWhoUnlocksAndWearsAtomically() throws Exception {
        var ledger = new Rac1GearLedger();
        var a = new Inventory(null);
        var b = new Inventory(null);
        ledger.rescue(A);
        Rac1GearServer.deliverRescue(a, A, ledger);
        var first = Rac1ClankItem.rewardId(a.getItem(0));
        assertEquals(EQUIPPED, Rac1GearServer.takeIntoEscrow(a, A, ledger, 0, false));
        assertTrue(a.getItem(0).isEmpty());
        assertTrue(ledger.record(A).effectiveBack().worn());
        assertFalse(ledger.looseAuthentic(first));
        assertTrue(ledger.beginReturn(A));
        assertTrue(Rac1GearServer.deliverReturn(a, A, ledger, stamp -> fail("Inventory has room")));
        ItemStack gift = a.removeItemNoUpdate(0);
        var sameToken = Rac1ClankItem.rewardId(gift);
        assertEquals(first.token(), sameToken.token());
        assertNotEquals(first.seal(), sameToken.seal());
        b.setItem(Inventory.SLOT_OFFHAND, gift);
        assertEquals(-1, Rac1GearServer.looseSlot(a, ledger));
        assertTrue(ledger.record(A).back().acquired());
        assertFalse(ledger.record(A).back().worn());
        assertEquals(DENIED, Rac1GearServer.takeIntoEscrow(b, B, ledger, Inventory.SLOT_OFFHAND, false));
        assertEquals(EQUIPPED, Rac1GearServer.takeIntoEscrow(b, B, ledger, Inventory.SLOT_OFFHAND, true));
        assertTrue(b.getItem(Inventory.SLOT_OFFHAND).isEmpty());
        assertTrue(ledger.record(B).back().acquired());
        assertTrue(ledger.record(B).effectiveBack().worn());
        assertNull(ledger.pendingRescue(B));
        assertEquals(ALREADY_UNLOCKED, ledger.rescue(B));
        assertEquals(DENIED, ledger.equip(A, sameToken, true));

        var reopened = reopen(ledger);
        assertTrue(reopened.record(B).effectiveBack().worn());
        assertEquals(first.token(), reopened.record(B).escrow());
        assertFalse(reopened.looseAuthentic(sameToken));
        assertTrue(reopened.beginReturn(B));
        assertTrue(Rac1GearServer.deliverReturn(b, B, reopened, stamp -> fail("Inventory has room")));
        var returned = Rac1ClankItem.rewardId(b.getItem(0));
        assertEquals(first.token(), returned.token());
        assertEquals(DENIED, reopened.equip(C, returned, true)); // No chain of new unlocks from one reward.
        assertEquals(EQUIPPED, reopened.equip(A, returned, true)); // Legitimate return to the prior owner.
    }

    @Test void failedFullInventoryDropRetriesAfterReloadWithoutReturningTwice() throws Exception {
        var ledger = new Rac1GearLedger();
        var inventory = new Inventory(null);
        ledger.rescue(A);
        Rac1GearServer.deliverRescue(inventory, A, ledger);
        var old = Rac1ClankItem.rewardId(inventory.getItem(0));
        assertEquals(EQUIPPED, Rac1GearServer.takeIntoEscrow(inventory, A, ledger, 0, true));
        inventory = fullInventory();
        assertTrue(ledger.beginReturn(A));
        assertFalse(ledger.record(A).back().worn());
        assertFalse(ledger.beginReturn(A));
        var interrupted = ledger.pendingReturn(A);
        assertFalse(Rac1GearServer.deliverReturn(inventory, A, ledger, stamp -> false));
        assertFalse(ledger.looseAuthentic(interrupted));
        var reopened = reopen(ledger);
        assertEquals(interrupted, reopened.pendingReturn(A));
        var drops = new ArrayList<Stamp>();
        assertTrue(Rac1GearServer.deliverReturn(inventory, A, reopened, drops::add));
        assertTrue(Rac1GearServer.deliverReturn(inventory, A, reopened, drops::add));
        assertEquals(java.util.List.of(interrupted), drops);
        assertEquals(old.token(), interrupted.token());
        assertTrue(reopened.looseAuthentic(interrupted));
        assertFalse(reopened.looseAuthentic(old));
    }

    @Test void counterfeitPackAndNetworkCopyCannotGrant() {
        var ledger = new Rac1GearLedger();
        var inventory = new Inventory(null);
        ledger.rescue(A);
        Rac1GearServer.deliverRescue(inventory, A, ledger);
        var exact = inventory.getItem(0);
        var stamp = Rac1ClankItem.rewardId(exact);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, exact);
            var wireCopy = ItemStack.STREAM_CODEC.decode(buffer);
            assertTrue(wireCopy.is(RacMcItems.CLANK.get()));
            assertNull(Rac1ClankItem.rewardId(wireCopy));
            var nested = new ItemStack(Items.SHULKER_BOX);
            nested.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(java.util.List.of(exact)));
            assertTrue(Rac1ClankItem.protectedStack(nested), "Native Creative clone must check contained items");
            assertFalse(Rac1ClankItem.protectedStack(new ItemStack(Items.STONE)));
            assertEquals(stamp, Rac1ClankItem.rewardId(ItemStack.parse(registries, exact.save(registries)).orElseThrow()));
            inventory.setItem(1, wireCopy);
            inventory.setItem(2, new ItemStack(RacMcItems.CLANK.get()));
            inventory.setItem(3, new ItemStack(RacMcItems.CLANK_HELI_PACK.get()));
            for (int i = 1; i <= 3; i++)
                assertEquals(DENIED, Rac1GearServer.takeIntoEscrow(inventory, B, ledger, i, true));
            assertEquals(DENIED, ledger.equip(B, new Stamp(stamp.token(), UUID.randomUUID()), true));
            assertEquals(Rac1ClankEquipment.EMPTY, ledger.record(B).back());
        } finally {
            buffer.release();
        }
    }

    @Test void legacyWithoutTokenFailsClosedRatherThanGuessingMigration() {
        var tag = new Rac1GearLedger().save(new CompoundTag(), RegistryAccess.EMPTY);
        tag.putInt("version", 1);
        var old = Rac1GearLedger.load(tag, RegistryAccess.EMPTY);
        assertFalse(old.available());
        assertEquals(Rac1GearServer.RescueResult.DENIED, old.rescue(A));
        assertFalse(old.isDirty());
        assertThrows(IllegalStateException.class, () -> old.save(new CompoundTag(), RegistryAccess.EMPTY));
    }

    private static Inventory fullInventory() {
        var inventory = new Inventory(null);
        for (int i = 0; i < inventory.items.size(); i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
        return inventory;
    }

    private static Rac1GearLedger reopen(Rac1GearLedger ledger) throws Exception {
        var stream = new ByteArrayOutputStream();
        NbtIo.writeCompressed(ledger.save(new CompoundTag(), RegistryAccess.EMPTY), stream);
        var tag = NbtIo.readCompressed(new ByteArrayInputStream(stream.toByteArray()), NbtAccounter.unlimitedHeap());
        var reopened = Rac1GearLedger.load(tag, RegistryAccess.EMPTY);
        assertTrue(reopened.available());
        return reopened;
    }
}
