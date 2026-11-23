package com.github.giannigr.racmc.shop;

import java.util.*;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;

/** Complete, staged vanilla inventory delivery; no live mutation until the server commit. */
public final class Rac1ShopDelivery {
    public enum Kind { WEAPON, AMMO }
    public enum Failure { INVALID_QUANTITY, STALE_OFFER, STOCK_CAP, FULL }
    public record Staged(Rac1ShopDelivery delivery,Failure failure) {}
    private final List<ItemStack> before,after;
    private final Kind kind;
    private final int arrows,quantity,grantedArrows;
    private final long price;
    private Rac1ShopDelivery(List<ItemStack> before,List<ItemStack> after,Kind kind,int arrows,int quantity,int grant,long price) {
        this.before=before; this.after=after; this.kind=kind; this.arrows=arrows;
        this.quantity=quantity; this.grantedArrows=grant; this.price=price;
    }
    public Kind kind() { return kind; }
    public int arrows() { return arrows; }
    public int quantity() { return quantity; }
    public int grantedArrows() { return grantedArrows; }
    public long price() { return price; }
    public static Kind offer(Inventory inventory,Item weapon) {
        for (int i=0;i<inventory.getContainerSize();i++) if (inventory.getItem(i).is(weapon)) return Kind.AMMO;
        return Kind.WEAPON;
    }
    /** Legacy wire/accessor name only; authoritative dedicated ammo, main inventory plus offhand. */
    public static int arrows(Inventory inventory) {
        return Rac1AmmoPolicy.count(inventory,RacMcItems.BLASTER_AMMO.get());
    }
    public static Staged stage(Inventory inventory,Item weapon,Kind expected,int quantity) {
        return stageWithAmmo(inventory,weapon,expected,quantity,RacMcItems.BLASTER_AMMO.get(),arrows(inventory));
    }
    /** Package-local deterministic inventory fixture seam; production always uses the P34 player count. */
    static Staged stageWithAmmo(Inventory inventory,Item weapon,Kind expected,int quantity,Item ammo,int count) {
        return stageBlaster(inventory,weapon,expected,quantity,ammo,count,false);
    }
    /** Portable catalog is ammo-only; cannot turn an absent weapon into a weapon sale. */
    public static Staged stagePdaAmmo(Inventory inventory,Item weapon,int quantity) {
        var ammo=RacMcItems.BLASTER_AMMO.get();
        return stagePdaWithAmmo(inventory,weapon,quantity,ammo,Rac1AmmoPolicy.count(inventory,ammo));
    }
    static Staged stagePdaWithAmmo(Inventory inventory,Item weapon,int quantity,Item ammo,int count) {
        return stageBlaster(inventory,weapon,Kind.AMMO,quantity,ammo,count,true);
    }
    private static Staged stageBlaster(Inventory inventory,Item weapon,Kind expected,int quantity,Item ammo,int count,
                                      boolean portable) {
        return stageStock(inventory,weapon,ammo,Rac1ShopOffer.BLASTER,expected,quantity,count,portable);
    }
    static Staged stageSelected(Inventory inventory,Rac1ShopOffer offer,int quantity,boolean portable) {
        var weapon=offer.stock().weapon().orElse(null);
        var ammo=offer.stock().ammo().orElse(null);
        if (weapon==null || ammo==null) return new Staged(null,Failure.STALE_OFFER);
        return stageStock(inventory,weapon,ammo,offer,portable ? Kind.AMMO:offer(inventory,weapon),
                quantity,Rac1AmmoPolicy.count(inventory,ammo),portable);
    }
    /** Item parameters also admit synthetic vanilla fixtures without registering placeholder mod items. */
    static Staged stageStock(Inventory inventory,Item weapon,Item ammo,Rac1ShopOffer offer,Kind expected,int quantity,
                             int count,boolean portable) {
        var stock=offer.stock();
        if (quantity<1 || quantity>Rac1AmmoPolicy.MAX_ORDER || expected==Kind.WEAPON && quantity!=1)
            return new Staged(null,Failure.INVALID_QUANTITY);
        if (!offer.permitted(portable) || offer(inventory,weapon)!=expected || portable && expected!=Kind.AMMO)
            return new Staged(null,Failure.STALE_OFFER);
        if (expected==Kind.AMMO && quantity>Math.max(0,stock.cap()-count)) return new Staged(null,Failure.STOCK_CAP);
        var before=snapshot(inventory); var after=copy(before);
        int grant=expected==Kind.WEAPON ? Math.max(0,stock.weaponFill()-count):quantity;
        // Stage weapon FIRST. A spare slot that fits only the weapon is not complete capacity.
        if (expected==Kind.WEAPON && !insert(after,new ItemStack(weapon),inventory))
            return new Staged(null,Failure.FULL);
        if (grant>0 && !insert(after,new ItemStack(ammo,grant),inventory))
            return new Staged(null,Failure.FULL);
        return new Staged(new Rac1ShopDelivery(before,after,expected,count,quantity,grant,
                expected==Kind.WEAPON ? stock.weaponPrice():Math.multiplyExact((long)quantity,stock.unitPrice(portable))),null);
    }
    private static boolean insert(List<ItemStack> slots,ItemStack delivery,Inventory inventory) {
        int remaining=delivery.getCount();
        // Merge compatible main/offhand stacks, but never auto-equip into armor or an empty offhand.
        int[] order=new int[37]; for (int i=0;i<36;i++) order[i]=i; order[36]=40;
        for (int slot:order) {
            var stack=slots.get(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack,delivery)) continue;
            int room=Math.max(0,Math.min(stack.getMaxStackSize(),inventory.getMaxStackSize(stack))-stack.getCount());
            int n=Math.min(remaining,room); if (n>0) { stack.grow(n); remaining-=n; }
            if (remaining==0) return true;
        }
        for (int slot=0;slot<36;slot++) if (slots.get(slot).isEmpty()) {
            int n=Math.min(remaining,Math.min(delivery.getMaxStackSize(),inventory.getMaxStackSize(delivery)));
            if (n<=0) continue;
            slots.set(slot,delivery.copyWithCount(n)); remaining-=n;
            if (remaining==0) return true;
        }
        return false;
    }
    public boolean unchanged(Inventory inventory) {
        if (inventory.getContainerSize()!=before.size()) return false;
        for (int i=0;i<before.size();i++) if (!ItemStack.matches(before.get(i),inventory.getItem(i))) return false;
        return true;
    }
    /** Preallocate all replacement stacks before any live mutation or debit. */
    List<ItemStack> replacements() { return copy(after); }
    List<ItemStack> originals() { return copy(before); }
    static void install(Inventory inventory,List<ItemStack> slots) {
        // Vanilla Inventory.setItem only assigns a compartment slot; no insert/drop/event callbacks.
        for (int i=0;i<slots.size();i++)
            if (!ItemStack.matches(inventory.getItem(i),slots.get(i))) inventory.setItem(i,slots.get(i));
    }
    private static List<ItemStack> snapshot(Inventory inventory) {
        if (inventory.items.size()!=36 || inventory.offhand.size()!=1 || inventory.getContainerSize()!=41)
            throw new IllegalArgumentException("Unsupported player inventory layout");
        var out=new ArrayList<ItemStack>(41);
        for (int i=0;i<41;i++) out.add(inventory.getItem(i).copy());
        return out;
    }
    private static List<ItemStack> copy(List<ItemStack> source) {
        var out=new ArrayList<ItemStack>(source.size()); for (var item:source) out.add(item.copy()); return out;
    }
}
