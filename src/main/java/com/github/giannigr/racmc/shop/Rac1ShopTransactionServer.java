package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.loot.Rac1BoltLedger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Real server entry point using canonical bolts and vanilla player inventory.
 * Connected through the owner-approved shop-only wire; no client prices or grants.
 * Atomic on the server thread, not fsync/crash-atomic across Minecraft save files.
 */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID)
public final class Rac1ShopTransactionServer {
    private static final Map<MinecraftServer,Rac1ShopTransactionServer> SERVERS=new IdentityHashMap<>();
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(Rac1ShopTransactionServer.class);
    private record Binding(Rac1ShopSessions.Opened opened,Rac1VendorBlockEntity vendor) {}
    private final MinecraftServer server;
    private final Rac1ShopPurchases purchases=new Rac1ShopPurchases();
    private final Map<UUID,Binding> owners=new HashMap<>();
    private final Map<UUID,Long> openTicks=new HashMap<>();
    private final Map<Rac1VendorBlockEntity,UUID> incarnations=new WeakHashMap<>();
    private Rac1ShopTransactionServer(MinecraftServer server) { this.server=server; }
    public static synchronized Rac1ShopTransactionServer get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Shop server access off-thread");
        return SERVERS.computeIfAbsent(server,Rac1ShopTransactionServer::new);
    }
    public Optional<Rac1ShopSessions.Opened> open(ServerPlayer player,BlockPos pos) {
        check(); tick();
        if (player.server!=server || !player.serverLevel().hasChunkAt(pos)
                || !(player.serverLevel().getBlockEntity(pos) instanceof Rac1VendorBlockEntity vendor)
                || !available(player,vendor)) return Optional.empty();
        if (!owners.containsKey(player.getUUID()) && owners.size()>=Rac1ShopSessions.MAX_SESSIONS) return Optional.empty();
        Long last=openTicks.get(player.getUUID());
        if (last!=null && now()>=last && now()-last<10) return Optional.empty();
        openTicks.put(player.getUUID(),now());
        var identity=new Rac1ShopSessions.Vendor(player.level().dimension().location().toString(),pos.asLong(),
                incarnations.computeIfAbsent(vendor,key -> UUID.randomUUID()));
        var opened=purchases.open(player.getUUID(),identity,now());
        owners.put(player.getUUID(),new Binding(opened,vendor));
        projectPhysical();
        player.stopUsingItem(); // capture an already-held use without releasing/firing a charged item
        return Optional.of(opened);
    }
    public Rac1ShopPurchases.Result quote(ServerPlayer player,UUID token,long sequence,int quantity) {
        return quote(player,token,sequence,Rac1ShopOffer.BLASTER,quantity);
    }
    public Rac1ShopPurchases.Result quote(ServerPlayer player,UUID token,long sequence,Rac1ShopOffer offer,int quantity) {
        check();
        Binding binding=binding(player,token);
        if (binding==null) return invalid();
        return purchases.quote(player.getUUID(),token,binding.opened.vendor(),sequence,offer,quantity,now(),
                player.getInventory(),RacMcItems.BLASTER.get(),account(player));
    }
    public Rac1ShopPurchases.Result buy(ServerPlayer player,UUID token,long sequence,UUID quote) {
        check();
        Binding binding=binding(player,token);
        if (binding==null) return invalid();
        var result=purchases.buy(player.getUUID(),token,binding.opened.vendor(),sequence,quote,now(),
                player.getInventory(),RacMcItems.BLASTER.get(),account(player));
        if (result.status()==Rac1ShopPurchases.Status.PURCHASED && !result.replay()) {
            try { player.inventoryMenu.broadcastChanges(); }
            catch (RuntimeException sync) { LOGGER.warn("Shop committed; inventory sync failed, never regranting",sync); }
        }
        return result;
    }
    public void close(ServerPlayer player,UUID token) {
        check();
        var b=owners.get(player.getUUID());
        if (b!=null && b.opened.token().equals(token)) {
            owners.remove(player.getUUID()); purchases.close(player.getUUID(),token); projectPhysical();
        }
    }
    private Rac1ShopPurchases.Account account(ServerPlayer player) {
        var ledger=Rac1BoltLedger.get(player.serverLevel());
        return new Rac1ShopPurchases.Account() {
            public long balance() { return ledger.balance(player.getUUID()); }
            public long revision() { return ledger.revision(player.getUUID()); }
            public boolean commit(Rac1ShopDelivery delivery,long balance,long revision) {
                check();
                var result=Rac1ShopAtomicCommit.run(player.getInventory(),delivery,balance,revision,new Rac1ShopAtomicCommit.Debit() {
                    public long balance() { return ledger.balance(player.getUUID()); }
                    public long revision() { return ledger.revision(player.getUUID()); }
                    public boolean tryDebit(long amount) { return ledger.tryDebit(player,amount); }
                });
                if (result.failure()!=null)
                    LOGGER.warn(result.committed() ? "Shop committed; balance sync failed, never regranting":"Shop commit rejected before debit",result.failure());
                return result.committed();
            }
        };
    }
    private Binding binding(ServerPlayer player,UUID token) {
        var b=owners.get(player.getUUID());
        if (b==null || !b.opened.token().equals(token)) return null;
        if (player.server!=server || !available(player,b.vendor)) {
            owners.remove(player.getUUID()); purchases.disconnect(player.getUUID()); return null;
        }
        return b;
    }
    private static boolean available(ServerPlayer player,Rac1VendorBlockEntity vendor) {
        var level=player.serverLevel(); var pos=vendor.getBlockPos();
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.containerMenu==player.inventoryMenu && vendor.getLevel()==level && !vendor.isRemoved()
                && level.hasChunkAt(pos) && level.getBlockEntity(pos)==vendor && player.canInteractWithBlock(pos,0);
    }
    private void tick() {
        check(); purchases.expire(now());
        openTicks.entrySet().removeIf(e -> server.getPlayerList().getPlayer(e.getKey())==null || now()<e.getValue() || now()-e.getValue()>=Rac1ShopSessions.IDLE_TICKS);
        owners.entrySet().removeIf(entry -> {
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if (player!=null && purchases.active(entry.getKey(),entry.getValue().opened.token())
                    && available(player,entry.getValue().vendor)) return false;
            purchases.disconnect(entry.getKey());
            if (player!=null) Rac1ShopNetwork.closed(player,entry.getValue().opened.token());
            return true;
        });
        projectPhysical();
    }
    private void projectPhysical() {
        // Owner changed the audience: private scene only. No occupancy/phase broadcast or late snapshot.
        Rac1ShopObserverServer.clear(server);
    }
    private long now() { return server.overworld().getGameTime(); }
    private void check() { if (!server.isSameThread()) throw new IllegalStateException("Shop mutation off server thread"); }
    private static Rac1ShopPurchases.Result invalid() { return new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.INVALID_SESSION,null,false); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Rac1ShopTransactionServer service;
        synchronized (Rac1ShopTransactionServer.class) { service=SERVERS.get(player.server); }
        if (service!=null) {
            service.check(); service.owners.remove(player.getUUID()); service.openTicks.remove(player.getUUID());
            service.purchases.disconnect(player.getUUID()); service.projectPhysical();
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Rac1ShopTransactionServer service;
        synchronized (Rac1ShopTransactionServer.class) { service=SERVERS.get(event.getServer()); }
        if (service!=null) service.tick();
    }
    @SubscribeEvent public static synchronized void stopped(ServerStoppedEvent event) {
        var service=SERVERS.remove(event.getServer());
        if (service!=null) { service.purchases.clear(); service.owners.clear(); service.openTicks.clear(); service.incarnations.clear(); }
        Rac1ShopObserverServer.clear(event.getServer());
    }
}
