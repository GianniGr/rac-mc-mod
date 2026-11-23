package com.github.giannigr.racmc.shop;

import net.minecraft.world.entity.player.Inventory;
import java.util.function.BooleanSupplier;

/**
 * Server-thread commit sequence used by the real ledger adapter and unit tests.
 * Debit is the existing ledger contract: false mutates nothing; notification follows debit.
 * No network simulation, compensating credit, item insertion callback or dropped remainder.
 */
final class Rac1ShopAtomicCommit {
    interface Debit {
        long balance();
        long revision();
        boolean tryDebit(long amount);
    }
    record Result(boolean committed,RuntimeException failure) {}
    private Rac1ShopAtomicCommit() {}
    /** No ledger debit/notification for free starters. Claim is a synchronous player attachment update. */
    static boolean claim(Inventory inventory,Rac1ShopDelivery delivery,BooleanSupplier claim) {
        if (delivery.kind()!=Rac1ShopDelivery.Kind.WEAPON || delivery.price()!=0 || !delivery.unchanged(inventory))
            return false;
        var originals=delivery.originals();
        Rac1ShopDelivery.install(inventory,delivery.replacements());
        boolean committed=false;
        try {
            committed=claim.getAsBoolean();
            return committed;
        } finally {
            if (committed) inventory.setChanged();
            else Rac1ShopDelivery.install(inventory,originals);
        }
    }

    static Result run(Inventory inventory,Rac1ShopDelivery delivery,long balance,long revision,Debit debit) {
        if (!delivery.unchanged(inventory) || debit.balance()!=balance || debit.revision()!=revision || balance<delivery.price())
            return new Result(false,null);
        var originals=delivery.originals(); var replacements=delivery.replacements();
        Rac1ShopDelivery.install(inventory,replacements);
        boolean committed;
        RuntimeException failure=null;
        try { committed=debit.tryDebit(delivery.price()); }
        catch (RuntimeException error) {
            failure=error;
            // A send failure AFTER the canonical ledger mutation is still a committed purchase.
            committed=debit.balance()==balance-delivery.price() && debit.revision()==revision+1;
        }
        if (!committed) Rac1ShopDelivery.install(inventory,originals);
        else inventory.setChanged();
        return new Result(committed,failure);
    }
}
