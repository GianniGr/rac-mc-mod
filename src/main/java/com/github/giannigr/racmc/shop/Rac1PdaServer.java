package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.loot.Rac1BoltLedger;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-local held-stack lifetime. No position, kiosk incarnation or persistent item NBT. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID)
public final class Rac1PdaServer {
    private static final Map<MinecraftServer,Rac1PdaServer> SERVERS=new IdentityHashMap<>();
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(Rac1PdaServer.class);
    private record Binding(UUID token,ServerPlayer player,InteractionHand hand,int slot,
                           ItemStack stack,net.minecraft.server.level.ServerLevel level) {}
    private final MinecraftServer server;
    private final Rac1PdaPurchases purchases=new Rac1PdaPurchases();
    private final Map<UUID,Binding> owners=new HashMap<>();
    private final Map<UUID,Long> openTicks=new HashMap<>();
    private Rac1PdaServer(MinecraftServer server) { this.server=server; }
    public static synchronized Rac1PdaServer get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("PDA access off server thread");
        return SERVERS.computeIfAbsent(server,Rac1PdaServer::new);
    }
    public boolean open(ServerPlayer player,InteractionHand hand) {
        check(); tick();
        if (!eligible(player) || player.server!=server
                || !player.getItemInHand(hand).is(RacMcItems.PDA.get())
                || player.getItemInHand(hand).getCount()!=1) return false;
        if (!owners.containsKey(player.getUUID()) && owners.size()>=Rac1ShopSessions.MAX_SESSIONS) return false;
        Long last=openTicks.get(player.getUUID());
        if (last!=null && now()>=last && now()-last<10) return false;
        openTicks.put(player.getUUID(),now());
        var token=purchases.open(player.getUUID(),now());
        owners.put(player.getUUID(),new Binding(token,player,hand,player.getInventory().selected,
                player.getItemInHand(hand),player.serverLevel()));
        player.stopUsingItem();
        Rac1PdaNetwork.opened(player,token,hand);
        return true;
    }
    public Rac1ShopPurchases.Result quote(ServerPlayer player,UUID token,long sequence,int quantity) {
        return quote(player,token,sequence,Rac1ShopOffer.BLASTER,quantity);
    }
    public Rac1ShopPurchases.Result quote(ServerPlayer player,UUID token,long sequence,Rac1ShopOffer offer,int quantity) {
        check(); if (binding(player,token)==null) return invalid();
        return purchases.quote(player.getUUID(),token,sequence,offer,quantity,now(),player.getInventory(),
                RacMcItems.BLASTER.get(),account(player));
    }
    public Rac1ShopPurchases.Result buy(ServerPlayer player,UUID token,long sequence,UUID quote) {
        check(); if (binding(player,token)==null) return invalid();
        var result=purchases.buy(player.getUUID(),token,sequence,quote,now(),player.getInventory(),
                RacMcItems.BLASTER.get(),account(player));
        if (result.status()==Rac1ShopPurchases.Status.PURCHASED && !result.replay()) {
            try { player.inventoryMenu.broadcastChanges(); }
            catch (RuntimeException failure) { LOGGER.warn("PDA committed; inventory sync failed, never regranting",failure); }
        }
        return result;
    }
    public void close(ServerPlayer player,UUID token) {
        check(); var b=owners.get(player.getUUID());
        if (b!=null && b.player==player && b.token.equals(token)) {
            owners.remove(player.getUUID()); purchases.close(player.getUUID(),token);
        }
    }
    private Binding binding(ServerPlayer player,UUID token) {
        var b=owners.get(player.getUUID());
        if (b==null || !b.token.equals(token)) return null;
        if (!valid(b,player)) { owners.remove(player.getUUID()); purchases.disconnect(player.getUUID()); return null; }
        return b;
    }
    private boolean valid(Binding b,ServerPlayer player) {
        // Exact object, not same item/components: swapping an identical PDA cannot inherit a session.
        return b.player==player && player.server==server && eligible(player) && b.level==player.serverLevel()
                && player.getInventory().selected==b.slot && player.getItemInHand(b.hand)==b.stack
                && b.stack.getCount()==1 && b.stack.is(RacMcItems.PDA.get())
                && purchases.active(player.getUUID(),b.token,now());
    }
    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.containerMenu==player.inventoryMenu;
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
                if (result.failure()!=null) LOGGER.warn(result.committed()
                        ? "PDA committed; balance sync failed, never regranting":"PDA commit rejected before debit",result.failure());
                return result.committed();
            }
        };
    }
    private void tick() {
        check(); purchases.expire(now());
        openTicks.entrySet().removeIf(e -> server.getPlayerList().getPlayer(e.getKey())==null
                || now()<e.getValue() || now()-e.getValue()>=Rac1PdaPurchases.LIFETIME_TICKS);
        owners.entrySet().removeIf(entry -> {
            var player=server.getPlayerList().getPlayer(entry.getKey()); var b=entry.getValue();
            if (player!=null && valid(b,player)) return false;
            purchases.disconnect(entry.getKey());
            if (player!=null) Rac1PdaNetwork.closed(player,b.token);
            return true;
        });
    }
    private long now() { return server.overworld().getGameTime(); }
    private void check() { if (!server.isSameThread()) throw new IllegalStateException("PDA mutation off server thread"); }
    private static Rac1ShopPurchases.Result invalid() {
        return new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.INVALID_SESSION,null,false);
    }
    private static void invalidate(ServerPlayer player) {
        Rac1PdaServer service;
        synchronized (Rac1PdaServer.class) { service=SERVERS.get(player.server); }
        if (service!=null) {
            service.check();
            var binding=service.owners.remove(player.getUUID());
            service.purchases.disconnect(player.getUUID());
            if (binding!=null) Rac1PdaNetwork.closed(player,binding.token);
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) invalidate(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) invalidate(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Rac1PdaServer service;
        synchronized (Rac1PdaServer.class) { service=SERVERS.get(player.server); }
        if (service!=null) {
            service.check(); service.owners.remove(player.getUUID()); service.openTicks.remove(player.getUUID());
            service.purchases.disconnect(player.getUUID());
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Rac1PdaServer service;
        synchronized (Rac1PdaServer.class) { service=SERVERS.get(event.getServer()); }
        if (service!=null) service.tick();
    }
    @SubscribeEvent public static synchronized void stopped(ServerStoppedEvent event) {
        var service=SERVERS.remove(event.getServer());
        if (service!=null) { service.purchases.clear(); service.owners.clear(); service.openTicks.clear(); }
    }
}
