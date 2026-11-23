package com.github.giannigr.racmc.shop;

import static com.github.giannigr.racmc.shop.Rac1ShopNetwork.Action.*;
import static com.github.giannigr.racmc.shop.Rac1ShopOffer.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.Status.*;
import static com.github.giannigr.racmc.shop.Rac1ShopTransactionGameTests.*;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.RacMcBlocks;
import com.github.giannigr.racmc.item.RacMcEntities;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.RangedCrateHits;
import com.github.giannigr.racmc.item.weapon.bomb.Rac1Bomb;
import com.github.giannigr.racmc.loot.Rac1BoltLedger;
import com.github.giannigr.racmc.loot.Rac1CrateRewards;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real registry, inventory, PlayerList, PDA dispatcher and world; EmbeddedChannel, not a socket login. */
@GameTestHolder("p35shop")
@PrefixGameTestTemplate(false)
public final class Rac1BombCommerceGameTests {
    private static final UUID RESTART_PLAYER=UUID.fromString("f30c0828-6775-4113-93a1-2b380e08f766");
    private static final GameProfile PROFILE=new GameProfile(RESTART_PLAYER,"r19-restart");
    private Rac1BombCommerceGameTests() {}

    @GameTest(template="empty",batch="r19_claim")
    public static void nativeClaimOnceAndPlayerDataSurvivesProcessRestart(GameTestHelper h) {
        String stage=System.getProperty("racmc.r19NativeStage","");
        h.assertTrue(stage.equals("write") || stage.equals("read"),"Explicit private run stage required");
        var save=h.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(RESTART_PLAYER+".dat");
        h.assertTrue(Files.isRegularFile(save)==stage.equals("read"),"Writer needs fresh player data; reader needs actual saved file");
        var p=player(h,PROFILE);
        try {
            var inv=p.getInventory();
            var level=h.getLevel();
            var shop=Rac1ShopTransactionServer.get(p.server);
            var ledger=Rac1BoltLedger.get(level);
            if (stage.equals("read")) {
                h.assertTrue(gloves(p)==1 && bombs(p)==10 && !Rac1StarterClaim.available(p),
                        "PlayerList login after process restart lost saved inventory/claim");
                var token=shop.open(p,vendor(h,p)).orElseThrow().token(); // vendor fixture clears inventory
                h.assertTrue(kiosk(p,token,1,BOMB_STARTER,1).status()==STALE_OFFER,"Lost glove reset persisted claim");
                h.assertTrue(gloves(p)==0 && bombs(p)==0,"Denied starter granted on restart");
                // Leave the evidence save in the committed configuration after closing the read probe.
                inv.setItem(0,new ItemStack(RacMcItems.BOMB_GLOVE.get()));
                inv.setItem(1,new ItemStack(RacMcItems.BOMB_GLOVE_AMMO.get(),10));
            } else {
                var pos=vendor(h,p); var token=shop.open(p,pos).orElseThrow().token();
                long balance=ledger.balance(p.getUUID()), revision=ledger.revision(p.getUUID());
                for(int i=1;i<36;i++) inv.setItem(i,new ItemStack(Items.STONE,64));
                h.assertTrue(kiosk(p,token,1,BOMB_STARTER,1).status()==FULL,"One empty slot accepted both items");
                h.assertTrue(inv.getItem(0).isEmpty() && Rac1StarterClaim.available(p),"Failed fit mutated inventory/claim");
                inv.setItem(1,ItemStack.EMPTY);
                var q=kiosk(p,token,2,BOMB_STARTER,1).quote();
                h.assertTrue(q!=null && q.offer()==BOMB_STARTER && q.deliveredAmmo()==10 && q.price()==0,"Wrong starter facts");
                inv.setItem(1,new ItemStack(Items.STONE,64));
                h.assertTrue(confirm(p,token,3,q.id()).status()==STALE_OFFER && Rac1StarterClaim.available(p),
                        "Stale full inventory consumed claim");
                inv.setItem(1,ItemStack.EMPTY);
                q=kiosk(p,token,4,BOMB_STARTER,1).quote();
                h.assertTrue(confirm(p,token,5,q.id()).status()==PURCHASED && gloves(p)==1 && bombs(p)==10,
                        "Real free grant wasn’t one item plus exactly ten bombs");
                h.assertTrue(confirm(p,token,5,q.id()).replay() && confirm(p,token,6,q.id()).status()==STALE_QUOTE
                                && kiosk(p,token,7,BOMB_STARTER,1).status()==STALE_OFFER,
                        "Repeat got grant authority");
                h.assertTrue(gloves(p)==1 && bombs(p)==10 && !Rac1StarterClaim.available(p)
                                && ledger.balance(p.getUUID())==balance && ledger.revision(p.getUUID())==revision,
                        "Repeat, inventory or ledger changed on the free commit");
                for(int i=2;i<36;i++) inv.setItem(i,ItemStack.EMPTY);
                shop.close(p,token);
            }
        } finally { p.server.getPlayerList().remove(p); }
        h.assertTrue(Files.isRegularFile(save),"PlayerList did not save actual player file");
        System.out.println("R19 NATIVE CLAIM "+stage.toUpperCase(Locale.ROOT)+" PASS");
        h.succeed();
    }

    @GameTest(template="empty",batch="r19_commerce")
    public static void nativePdaAndKioskPricingReplayAndCaps(GameTestHelper h) {
        var p=player(h);
        try {
            var shop=Rac1ShopTransactionServer.get(p.server);
            var pos=vendor(h,p); fund(h,p,100);
            var inv=p.getInventory();
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RacMcItems.PDA.get()));
            var pda=Rac1PdaServer.get(p.server);
            h.assertTrue(pda.open(p,InteractionHand.MAIN_HAND),"Real PDA did not open");
            var portable=outbound(p,Rac1PdaNetwork.Opened.class,o -> true).token();
            var kiosk=shop.open(p,pos).orElseThrow().token();
            h.assertTrue(portable(p,portable,1,QUOTE,1,null,BOMB_STARTER).status()==STALE_OFFER,"PDA quoted starter");
            h.assertTrue(portable(p,portable,2,QUOTE,1,null,BOMB_AMMO).status()==STALE_OFFER,"PDA quoted unowned stock");
            inv.setItem(4,new ItemStack(RacMcItems.BOMB_GLOVE.get())); // not held, actual PDA remains selected
            inv.setItem(5,new ItemStack(RacMcItems.BLASTER.get()));
            inv.setItem(6,new ItemStack(RacMcItems.BOMB_GLOVE_AMMO.get(),19));
            inv.setItem(40,new ItemStack(RacMcItems.BOMB_GLOVE_AMMO.get(),20));
            inv.setItem(7,new ItemStack(Items.ARROW,64));
            for(int i=8;i<12;i++) inv.setItem(i,new ItemStack(RacMcItems.BLASTER_AMMO.get(),i==11 ? 7:64));
            h.assertTrue(kiosk(p,kiosk,1,BOMB_AMMO,2).status()==STOCK_CAP
                    && portable(p,portable,3,QUOTE,2,null,BOMB_AMMO).status()==STOCK_CAP,"One-over cap clipped/accepted");
            var k=kiosk(p,kiosk,2,BOMB_AMMO,1).quote();
            var q=portable(p,portable,4,QUOTE,1,null,BOMB_AMMO).quote();
            h.assertTrue(k!=null && q!=null && k.price()==5 && q.price()==25 && q.currentAmmo()==39,
                    "Context price or exact offhand/main count wrong");
            h.assertTrue(confirm(p,kiosk,3,q.id()).status()==STALE_QUOTE,"Kiosk accepted PDA quote");
            h.assertTrue(portable(p,portable,5,BUY,0,q.id(),BLASTER).status()==PURCHASED
                    && portable(p,portable,5,BUY,0,q.id(),BLASTER).replay(),"PDA commit or replay failed");
            h.assertTrue(confirm(p,kiosk,4,k.id()).status()==STALE_OFFER,"Stale inventory snapshot still committed");
            h.assertTrue(bombs(p)==40 && inv.getItem(7).getCount()==64 && Rac1StarterClaim.available(p)
                    && Rac1BoltLedger.get(h.getLevel()).balance(p.getUUID())==75,"PDA changed unrelated inventory/claim/value");
            h.assertTrue(portable(p,portable,6,QUOTE,1,null,BOMB_AMMO).status()==STOCK_CAP,"Full cap accepted");
            inv.getItem(6).grow(1);
            h.assertTrue(kiosk(p,kiosk,5,BOMB_AMMO,1).status()==STOCK_CAP && bombs(p)==41,"Over cap reduced or accepted");
            q=portable(p,portable,7,QUOTE,1,null,BLASTER).quote();
            h.assertTrue(q!=null && q.price()==10 && q.currentAmmo()==199,"Legacy Blaster PDA quote changed");
            k=kiosk(p,kiosk,6,BLASTER,1).quote();
            h.assertTrue(k!=null && k.price()==1 && confirm(p,kiosk,7,k.id()).status()==PURCHASED
                    && kiosk(p,kiosk,8,BLASTER,1).status()==STOCK_CAP,"Legacy Blaster kiosk price/cap changed");
        } finally { p.server.getPlayerList().remove(p); }
        h.succeed();
    }

    @GameTest(template="empty",batch="r19_crates")
    public static void nativeGroundedAmmoCratesRouteActualBombPickupAndBlasterFallback(GameTestHelper h) {
        var p=player(h);
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(2,2,2));
        p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);
        p.getInventory().setItem(4,new ItemStack(RacMcItems.BOMB_GLOVE.get()));
        p.getInventory().setItem(5,new ItemStack(RacMcItems.BLASTER.get()));
        p.getInventory().setItem(6,new ItemStack(RacMcItems.BOMB_GLOVE_AMMO.get(),39));
        for(int i=8;i<12;i++) p.getInventory().setItem(i,new ItemStack(RacMcItems.BLASTER_AMMO.get(),i==11 ? 8:64));
        var bomb=new Rac1Bomb(RacMcEntities.BOMB.get(),level);
        bomb.setOwner(p);
        var state=RacMcBlocks.crate(Rac1Crates.AMMO).get().defaultBlockState();
        List<ItemStack> spawned=new ArrayList<>();
        Vec3 origin=Vec3.atBottomCenterOf(pos);
        Consumer<EntityJoinLevelEvent> probe=e -> {
            if (e.getLevel()==level && e.getEntity() instanceof ItemEntity item && item.position().equals(origin))
                spawned.add(item.getItem().copy());
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,probe);
        Set<Integer> bombTotals=new TreeSet<>(), fallbackTotals=new TreeSet<>();
        try {
            for (int i=0;i<64;i++) {
                boolean routed=i%2==0;
                // Synthetic world only; fixed seeds exercise both count branches repeatably.
                level.random.setSeed(9117L+i);
                level.setBlock(pos,state,3);
                if (routed) h.assertTrue(RangedCrateHits.grounded(bomb,pos),"Real P34 projectile crate seam rejected");
                else Rac1CrateRewards.destroy(level,pos,state,null);
                h.assertTrue(level.getBlockState(pos).isAir(),"Crate not consumed");
                int n=spawned.size();
                var ammo=routed ? RacMcItems.BOMB_GLOVE_AMMO.get():RacMcItems.BLASTER_AMMO.get();
                int units=routed ? 3:25;
                h.assertTrue((n==1 || n==2) && spawned.stream().allMatch(s -> s.is(ammo) && s.getCount()==units),
                        "Wrong one/two actual item spawn outcome: "+spawned);
                Rac1CrateRewards.destroy(level,pos,state,bomb);
                h.assertTrue(spawned.size()==n,"Repeat callback respawned ammo");
                (routed ? bombTotals:fallbackTotals).add(n*units);
                spawned.clear();
            }
            h.assertTrue(bombTotals.equals(Set.of(3,6)) && fallbackTotals.equals(Set.of(25,50)) && bombs(p)==39,
                    "Missing native draw branch or crate delivered directly: "+bombTotals+" vs "+fallbackTotals);
            System.out.println("R19 NATIVE CRATES PASS Bomb="+bombTotals+" fallback Blaster="+fallbackTotals);
        } finally {
            NeoForge.EVENT_BUS.unregister(probe);
            p.server.getPlayerList().remove(p);
        }
        h.succeed();
    }

    private static int gloves(ServerPlayer p) { return p.getInventory().countItem(RacMcItems.BOMB_GLOVE.get()); }
    private static int bombs(ServerPlayer p) { return BOMB_AMMO.stock().count(p.getInventory()); }
    private static Rac1ShopPurchases.Result kiosk(ServerPlayer p,UUID token,int seq,Rac1ShopOffer offer,int amount) {
        return wire(p,new Rac1ShopNetwork.Command(token,seq,QUOTE,amount,null,offer)).result();
    }
    private static Rac1ShopPurchases.Result confirm(ServerPlayer p,UUID token,int seq,UUID quote) {
        return wire(p,new Rac1ShopNetwork.Command(token,seq,BUY,0,quote)).result();
    }
    private static Rac1ShopPurchases.Result portable(ServerPlayer p,UUID token,int seq,Rac1ShopNetwork.Action action,
                                                     int qty,UUID id,Rac1ShopOffer offer) {
        var intent=new Rac1PdaNetwork.Command(new Rac1ShopNetwork.Command(token,seq,action,qty,id,offer));
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            Rac1PdaNetwork.Command.CODEC.encode(buf,intent);
            NetworkRegistry.handleModdedPayload(p.connection,
                    new ServerboundCustomPayloadPacket(Rac1PdaNetwork.Command.CODEC.decode(buf)));
        } finally { buf.release(); }
        return outbound(p,Rac1PdaNetwork.Reply.class,r -> r.token().equals(token) && r.sequence()==seq).result();
    }
    private static <T> T outbound(ServerPlayer p,Class<T> type,Predicate<T> accept) {
        var channel=(EmbeddedChannel)p.connection.getConnection().channel();
        channel.runPendingTasks();
        for(Object next;(next=channel.readOutbound())!=null;)
            if (next instanceof ClientboundCustomPayloadPacket packet && type.isInstance(packet.payload())) {
                T result=type.cast(packet.payload());
                if (accept.test(result)) return result;
            }
        throw new IllegalStateException("Missing native reply "+type.getName());
    }
}
