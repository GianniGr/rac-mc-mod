package com.github.giannigr.racmc.loot;

import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltLedgerTest {
    @Test void accountsAndUnspawnedRewardsRoundtripExactlyWithoutPlayerEntity() {
        UUID player = UUID.randomUUID(), pickup = UUID.randomUUID(), incarnation = UUID.randomUUID();
        CompoundTag input = new CompoundTag();
        ListTag accounts = new ListTag(), pickups = new ListTag(), rewarded = new ListTag();
        CompoundTag account = new CompoundTag();
        account.putUUID("id", player);
        account.putLong("balance", 9876543210L);
        account.putLong("revision", 19);
        accounts.add(account);
        CompoundTag pending = new CompoundTag();
        pending.putUUID("id", pickup);
        pending.putString("dimension", "minecraft:the_nether");
        pending.putDouble("x", 2.5); pending.putDouble("y", 70); pending.putDouble("z", -4.5);
        pending.putInt("value", 50);
        pickups.add(pending);
        CompoundTag consumed = new CompoundTag();
        consumed.putUUID("id", incarnation); rewarded.add(consumed);
        input.put("accounts", accounts); input.put("pickups", pickups); input.put("rewarded", rewarded);
        var ledger = Rac1BoltLedger.load(input, null);
        assertEquals(9876543210L, ledger.balance(player));
        assertEquals(19, ledger.revision(player));
        assertEquals(50, ledger.pickup(pickup).value());
        assertEquals(input, ledger.save(new CompoundTag(), null));
        ledger.move(pickup, 10, 30, 40);
        var reloaded = Rac1BoltLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(10, reloaded.pickup(pickup).x());
        assertEquals(9876543210L, reloaded.balance(player));
    }
}
