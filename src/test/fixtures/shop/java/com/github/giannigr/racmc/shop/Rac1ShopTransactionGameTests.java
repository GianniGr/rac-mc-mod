package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.block.RacMcBlocks;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.loot.Rac1BoltLedger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Opt-in real server/registry/ledger tests, NOT RUN by unit verification.
 * No fake transport. Calls the real authenticated-player adapter directly.
 */
@GameTestHolder("p35shop")
@PrefixGameTestTemplate(false)
public final class Rac1ShopTransactionGameTests {
    private Rac1ShopTransactionGameTests() {}
    @GameTest(template="empty")
    public static void canonicalLedgerWholeDeliveryAndReplay(GameTestHelper helper) {
        var player=player(helper);
        var pos=vendor(helper,player); fund(helper,player,5000);
        var ledger=Rac1BoltLedger.get(helper.getLevel());
        var shop=Rac1ShopTransactionServer.get(helper.getLevel().getServer());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.SPYGLASS));
        player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        var opened=shop.open(player,pos).orElseThrow(); long before=ledger.balance(player.getUUID());
        helper.assertTrue(!player.isUsingItem(),"Opening retained an already-held use");
        var quote=shop.quote(player,opened.token(),1,1).quote();
        helper.assertTrue(quote!=null && quote.price()==2500 && quote.deliveredArrows()==100,"Wrong original weapon offer");
        var result=shop.buy(player,opened.token(),2,quote.id());
        helper.assertTrue(result.status()==Rac1ShopPurchases.Status.PURCHASED,"Real server purchase rejected");
        helper.assertTrue(ledger.balance(player.getUUID())==before-2500 && Rac1ShopDelivery.arrows(player.getInventory())==100,"Items and bolts diverged");
        helper.assertTrue(shop.buy(player,opened.token(),2,quote.id()).replay(),"Retry did not return receipt");
        helper.assertTrue(ledger.balance(player.getUUID())==before-2500 && Rac1ShopDelivery.arrows(player.getInventory())==100,"Retry debited or granted twice");
        shop.close(player,opened.token()); player.server.getPlayerList().remove(player); helper.succeed();
    }
    @GameTest(template="empty")
    public static void stagedWeaponAmmoCapacityAndOverCapRejectWithoutDebit(GameTestHelper helper) {
        var player=player(helper); var pos=vendor(helper,player); fund(helper,player,5000);
        var ledger=Rac1BoltLedger.get(helper.getLevel()); long before=ledger.balance(player.getUUID());
        var shop=Rac1ShopTransactionServer.get(player.server); var opened=shop.open(player,pos).orElseThrow();
        for (int i=1;i<36;i++) player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        helper.assertTrue(shop.quote(player,opened.token(),1,1).status()==Rac1ShopPurchases.Status.FULL,"Weapon-only space accepted whole offer");
        helper.assertTrue(ledger.balance(player.getUUID())==before && player.getInventory().getItem(0).isEmpty(),"Full quote mutated live state");
        player.getInventory().clearContent(); player.getInventory().setItem(0,new ItemStack(RacMcItems.BLASTER.get()));
        for (int i=1;i<=4;i++) player.getInventory().setItem(i,new ItemStack(RacMcItems.BLASTER_AMMO.get(),i==4 ? 58:64));
        helper.assertTrue(shop.quote(player,opened.token(),2,1).status()==Rac1ShopPurchases.Status.STOCK_CAP,"Over-cap arrows accepted");
        helper.assertTrue(Rac1ShopDelivery.arrows(player.getInventory())==250 && ledger.balance(player.getUUID())==before,"Over-cap quote removed arrows or charged");
        shop.close(player,opened.token()); player.server.getPlayerList().remove(player); helper.succeed();
    }
    @GameTest(template="empty")
    public static void independentViewersLogoutAndReplacedVendorFailClosed(GameTestHelper helper) {
        var first=player(helper); var second=player(helper);
        var pos=vendor(helper,first); second.teleportTo(first.getX(),first.getY(),first.getZ());
        fund(helper,first,5000); fund(helper,second,5000);
        var shop=Rac1ShopTransactionServer.get(first.server);
        var a=shop.open(first,pos).orElseThrow(); var b=shop.open(second,pos).orElseThrow();
        var qa=shop.quote(first,a.token(),1,1).quote(); var qb=shop.quote(second,b.token(),1,1).quote();
        Rac1ShopTransactionServer.logout(new PlayerEvent.PlayerLoggedOutEvent(first));
        helper.assertTrue(shop.buy(first,a.token(),2,qa.id()).status()==Rac1ShopPurchases.Status.INVALID_SESSION,"Logged-out nonce accepted");
        helper.assertTrue(shop.buy(second,b.token(),2,qb.id()).status()==Rac1ShopPurchases.Status.PURCHASED,"Other viewer was closed");
        var ammo=shop.quote(second,b.token(),3,1).quote();
        helper.getLevel().removeBlock(pos,false);
        helper.getLevel().setBlock(pos,RacMcBlocks.VENDOR.get().defaultBlockState(),3);
        helper.assertTrue(shop.buy(second,b.token(),4,ammo.id()).status()==Rac1ShopPurchases.Status.INVALID_SESSION,"Replaced vendor reused old incarnation");
        first.server.getPlayerList().remove(first); second.server.getPlayerList().remove(second); helper.succeed();
    }
    @GameTest(template="empty")
    public static void registeredWireCodecDispatcherReplayForeignOwnerAndClose(GameTestHelper helper) {
        var player=player(helper); var pos=vendor(helper,player); fund(helper,player,5000);
        var shop=Rac1ShopTransactionServer.get(player.server); var opened=shop.open(player,pos).orElseThrow();
        var quoted=wire(player,new Rac1ShopNetwork.Command(opened.token(),1,Rac1ShopNetwork.Action.QUOTE,1,null));
        helper.assertTrue(quoted.result().status()==Rac1ShopPurchases.Status.QUOTED,"Registered dispatcher did not quote");
        var buy=new Rac1ShopNetwork.Command(opened.token(),2,Rac1ShopNetwork.Action.BUY,0,quoted.result().quote().id());
        helper.assertTrue(wire(player,buy).result().status()==Rac1ShopPurchases.Status.PURCHASED,"Registered dispatcher did not buy");
        helper.assertTrue(wire(player,buy).result().replay(),"Wire retry missing receipt");
        var other=player(helper); other.teleportTo(player.getX(),player.getY(),player.getZ());
        helper.assertTrue(wire(other,buy).result().status()==Rac1ShopPurchases.Status.INVALID_SESSION,"Wire trusted token instead of authenticated owner");
        wire(player,new Rac1ShopNetwork.Command(opened.token(),0,Rac1ShopNetwork.Action.CLOSE,0,null));
        helper.assertTrue(wire(player,buy).result().status()==Rac1ShopPurchases.Status.INVALID_SESSION,"Wire close retained spend authority");
        helper.assertTrue(Rac1BoltLedger.get(helper.getLevel()).balance(player.getUUID())==2500
                && Rac1ShopDelivery.arrows(player.getInventory())==100,"Wire replay/foreign/close changed committed value");
        player.server.getPlayerList().remove(player); other.server.getPlayerList().remove(other); helper.succeed();
    }
    @GameTest(template="empty")
    public static void shopperOnlyAudiencePreservesIndependentSessionsWithoutLateTrackerBroadcast(GameTestHelper helper) {
        var first=player(helper); var second=player(helper); var pos=vendor(helper,first);
        second.teleportTo(first.getX(),first.getY(),first.getZ());
        var shop=Rac1ShopTransactionServer.get(first.server);
        var a=shop.open(first,pos).orElseThrow(); var b=shop.open(second,pos).orElseThrow();
        fund(helper,second,5000);
        var vendor=(com.github.giannigr.racmc.block.Rac1VendorBlockEntity)helper.getLevel().getBlockEntity(pos);
        helper.runAfterDelay(20,() -> {
            helper.assertTrue(Rac1ShopObserverServer.snapshot(vendor).isEmpty(),"Shopper phase must not become public state");
            var sent=new net.neoforged.neoforge.event.level.ChunkWatchEvent.Sent(
                    first,helper.getLevel().getChunkAt(pos),helper.getLevel());
            helper.assertTrue(first.serverLevel()==sent.getLevel()
                    && new net.minecraft.world.level.ChunkPos(pos).equals(sent.getPos()),"Invalid fixture chunk-Sent event");
            Rac1ShopObserverServer.sent(sent);
            helper.assertTrue(observer(first,pos)==null && observer(second,pos)==null,
                    "Chunk tracking must not reveal another shopper's opening phase");
            shop.close(first,a.token());
            helper.assertTrue(shop.quote(second,b.token(),1,1).status()==Rac1ShopPurchases.Status.QUOTED,
                    "One shopper closed another shopper's authority");
            shop.close(second,b.token());
            helper.assertTrue(Rac1ShopObserverServer.snapshot(vendor).isEmpty(),"Private exit must not publish folding");
            helper.runAfterDelay(20,() -> {
                helper.assertTrue(Rac1ShopObserverServer.snapshot(vendor).isEmpty()
                        && observer(first,pos)==null && observer(second,pos)==null,"Public animation leaked after exit");
                first.server.getPlayerList().remove(first); second.server.getPlayerList().remove(second); helper.succeed();
            });
        });
    }
    private static Rac1ShopObserverNetwork.State observer(ServerPlayer player,BlockPos position) {
        var channel=(io.netty.channel.embedded.EmbeddedChannel)player.connection.getConnection().channel();
        channel.runPendingTasks();
        channel.flushOutbound(); channel.runPendingTasks(); // mimic end-of-tick flush of buffered server sends
        Rac1ShopObserverNetwork.State latest=null;
        for (Object value;(value=channel.readOutbound())!=null;)
            if (value instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof Rac1ShopObserverNetwork.State state && state.position().equals(position)) latest=state;
        return latest;
    }
    @GameTest(template="empty")
    public static void dedicatedAmmoOffhandCapFullBeforePoorAndArrowsNeverMigrate(GameTestHelper helper) {
        var player=player(helper); var pos=vendor(helper,player); fund(helper,player,1);
        var inventory=player.getInventory();
        inventory.setItem(0,new ItemStack(RacMcItems.BLASTER.get()));
        for(int i=1;i<=3;i++) inventory.setItem(i,new ItemStack(RacMcItems.BLASTER_AMMO.get(),64));
        inventory.setItem(40,new ItemStack(RacMcItems.BLASTER_AMMO.get(),7));
        inventory.setItem(4,new ItemStack(Items.ARROW,64));
        inventory.setItem(5,new ItemStack(Items.TIPPED_ARROW,64));
        inventory.setItem(6,new ItemStack(Items.SPECTRAL_ARROW,64));
        inventory.selected=1;
        var shop=Rac1ShopTransactionServer.get(player.server); var opened=shop.open(player,pos).orElseThrow();
        var q=shop.quote(player,opened.token(),1,1).quote();
        helper.assertTrue(q!=null && q.currentArrows()==199 && q.price()==1,"Dedicated offhand/selected stock counted incorrectly");
        helper.assertTrue(shop.buy(player,opened.token(),2,q.id()).status()==Rac1ShopPurchases.Status.PURCHASED,"199 to200 rejected");
        helper.assertTrue(Rac1ShopDelivery.arrows(inventory)==200 && inventory.getItem(40).getCount()==8,"Offhand merge failed");
        var ledger=Rac1BoltLedger.get(helper.getLevel());
        helper.assertTrue(ledger.balance(player.getUUID())==0,"Wrong per-unit debit");
        helper.assertTrue(shop.quote(player,opened.token(),3,1).status()==Rac1ShopPurchases.Status.STOCK_CAP,"Full must win over poor");
        helper.assertTrue(shop.quote(player,opened.token(),3,1).replay(),"Denied replay not retained");
        helper.assertTrue(inventory.getItem(4).is(Items.ARROW) && inventory.getItem(4).getCount()==64
                && inventory.getItem(5).is(Items.TIPPED_ARROW) && inventory.getItem(6).is(Items.SPECTRAL_ARROW),"Arrows migrated/consumed");
        inventory.getItem(40).grow(1);
        helper.assertTrue(shop.quote(player,opened.token(),4,1).status()==Rac1ShopPurchases.Status.STOCK_CAP,"Over-cap accepted");
        helper.assertTrue(Rac1ShopDelivery.arrows(inventory)==201 && ledger.balance(player.getUUID())==0,"Denied quote granted/charged");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(8)).isEmpty(),"Direct delivery spawned drops");
        shop.close(player,opened.token()); player.server.getPlayerList().remove(player); helper.succeed();
    }
    @GameTest(template="empty")
    public static void dedicatedWeaponTopUpIsMaximumIncludingOffhandNeverRemoval(GameTestHelper helper) {
        for(int count:new int[]{0,20,100,158,225}) {
            var player=player(helper); var pos=vendor(helper,player); fund(helper,player,2500);
            var inventory=player.getInventory();
            int off=Math.min(64,count); if(off>0) inventory.setItem(40,new ItemStack(RacMcItems.BLASTER_AMMO.get(),off));
            for(int i=1,left=count-off;left>0;i++) { int n=Math.min(64,left); inventory.setItem(i,new ItemStack(RacMcItems.BLASTER_AMMO.get(),n)); left-=n; }
            inventory.setItem(8,new ItemStack(Items.ARROW,64));
            var shop=Rac1ShopTransactionServer.get(player.server); var opened=shop.open(player,pos).orElseThrow();
            var q=shop.quote(player,opened.token(),1,1).quote();
            helper.assertTrue(q!=null && q.deliveredArrows()==Math.max(0,100-count),"Wrong weapon top-up");
            helper.assertTrue(shop.buy(player,opened.token(),2,q.id()).status()==Rac1ShopPurchases.Status.PURCHASED,"Top-up purchase rejected");
            helper.assertTrue(Rac1ShopDelivery.arrows(inventory)==Math.max(count,100)
                    && inventory.getItem(8).getCount()==64,"Weapon top-up removed excess or converted arrows");
            shop.close(player,opened.token()); player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void shopDeliveredAmmoPaysRealSurvivalAndAdventureShots(GameTestHelper helper) {
        for(var mode:List.of(net.minecraft.world.level.GameType.SURVIVAL,net.minecraft.world.level.GameType.ADVENTURE)) {
            var player=player(helper); var pos=vendor(helper,player); fund(helper,player,2500);
            player.setGameMode(mode);
            player.getInventory().setItem(8,new ItemStack(Items.ARROW,12));
            var shop=Rac1ShopTransactionServer.get(player.server); var opened=shop.open(player,pos).orElseThrow();
            var quote=shop.quote(player,opened.token(),1,1).quote();
            helper.assertTrue(shop.buy(player,opened.token(),2,quote.id()).status()==Rac1ShopPurchases.Status.PURCHASED,
                    "Real weapon purchase rejected");
            shop.close(player,opened.token());
            // Stay inside the 5-high template, above the vendor but below GameTest's barrier roof.
            player.teleportTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
            player.setYRot(0); player.setXRot(0);
            player.yBodyRot=player.yBodyRotO=player.yHeadRot=player.yHeadRotO=0;
            helper.assertTrue(player.getMainHandItem().is(RacMcItems.BLASTER.get()),"Delivered weapon not in selected slot");
            var muzzle=com.github.giannigr.racmc.item.Rac1BlasterGrip.muzzle(
                    com.github.giannigr.racmc.item.weapon.RangedPlayerPose.of(player,1,
                            com.github.giannigr.racmc.item.weapon.RangedSkin.server(player)));
            var eyes=player.getEyePosition();
            var mouth=new net.minecraft.world.phys.Vec3(muzzle.x(),muzzle.y(),muzzle.z());
            var hit=helper.getLevel().clip(new net.minecraft.world.level.ClipContext(eyes,mouth,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
            helper.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS
                    && helper.getLevel().getBlockState(BlockPos.containing(mouth)).isAir(),"Fixture muzzle/path obstructed");
            boolean fired=com.github.giannigr.racmc.item.Rac1BlasterServer.fireFirstShot(helper.getLevel(),player);
            helper.assertTrue(fired && Rac1ShopDelivery.arrows(player.getInventory())==99,
                    "Shop ammo shot in "+mode+" fired="+fired+" stock="+Rac1ShopDelivery.arrows(player.getInventory()));
            helper.assertTrue(player.getInventory().getItem(8).is(Items.ARROW)
                    && player.getInventory().getItem(8).getCount()==12,"Shop/firing changed ordinary arrows");
            com.github.giannigr.racmc.item.weapon.RangedFireSessions.reject(player);
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void damageDeathAndOutOfReachDoNotProtectOrChargeViewer(GameTestHelper helper) {
        var first=player(helper); var second=player(helper);
        var pos=vendor(helper,first); second.teleportTo(first.getX(),first.getY(),first.getZ());
        fund(helper,first,5000); fund(helper,second,5000);
        var shop=Rac1ShopTransactionServer.get(first.server);
        var a=shop.open(first,pos).orElseThrow(); var b=shop.open(second,pos).orElseThrow();
        var qa=shop.quote(first,a.token(),1,1).quote(); var qb=shop.quote(second,b.token(),1,1).quote();
        long start=helper.getLevel().getGameTime();
        helper.runAfterDelay(65,() -> {
            // Let vanilla's 60-tick join protection expire naturally; do not bypass invulnerability.
            helper.assertTrue(helper.getLevel().getGameTime()>=start+65,"World did not keep ticking");
            float health=first.getHealth();
            helper.assertTrue(first.hurt(first.damageSources().generic(),1) && first.getHealth()<health,"Menu protected player from damage");
            first.kill();
            helper.assertTrue(shop.buy(first,a.token(),2,qa.id()).status()==Rac1ShopPurchases.Status.INVALID_SESSION,"Dead viewer could buy");
            second.teleportTo(pos.getX()+100,pos.getY(),pos.getZ());
            helper.assertTrue(shop.buy(second,b.token(),2,qb.id()).status()==Rac1ShopPurchases.Status.INVALID_SESSION,"Out-of-reach viewer could buy");
            var ledger=Rac1BoltLedger.get(helper.getLevel());
            helper.assertTrue(ledger.balance(first.getUUID())==5000 && ledger.balance(second.getUUID())==5000,"Invalid viewer charged");
            first.server.getPlayerList().remove(first); second.server.getPlayerList().remove(second); helper.succeed();
        });
    }
    static Rac1ShopNetwork.Reply wire(ServerPlayer player,Rac1ShopNetwork.Command command) {
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        Rac1ShopNetwork.Command decoded;
        try { Rac1ShopNetwork.Command.CODEC.encode(buffer,command); decoded=Rac1ShopNetwork.Command.CODEC.decode(buffer); }
        finally { buffer.release(); }
        net.neoforged.neoforge.network.registration.NetworkRegistry.handleModdedPayload(player.connection,
                new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(decoded));
        var channel=(io.netty.channel.embedded.EmbeddedChannel)player.connection.getConnection().channel();
        channel.runPendingTasks();
        for (Object value;(value=channel.readOutbound())!=null;) {
            if (value instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof Rac1ShopNetwork.Reply reply && reply.token().equals(command.token())
                    && reply.sequence()==command.sequence()) return reply;
        }
        throw new IllegalStateException("No registered shop reply on fixture embedded channel");
    }
    static BlockPos vendor(GameTestHelper helper,ServerPlayer player) {
        prepare(player);
        var pos=helper.absolutePos(new BlockPos(2,2,2));
        helper.getLevel().setBlock(pos,RacMcBlocks.VENDOR.get().defaultBlockState(),3);
        player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);
        player.getInventory().clearContent();
        return pos;
    }
    static void fund(GameTestHelper helper,ServerPlayer player,int amount) {
        prepare(player);
        var level=helper.getLevel(); var ledger=Rac1BoltLedger.get(level); var old=ids(ledger,helper);
        ledger.reward(UUID.randomUUID(),level,player.blockPosition(),amount);
        for (UUID id:ids(ledger,helper)) if (!old.contains(id))
            helper.assertTrue(ledger.collect(id,player),"Fixture canonical pickup credit failed");
    }
    private static Set<UUID> ids(Rac1BoltLedger ledger,GameTestHelper helper) {
        var ids=new HashSet<UUID>();
        for (Tag tag:ledger.save(new CompoundTag(),helper.getLevel().registryAccess()).getList("pickups",Tag.TAG_COMPOUND))
            ids.add(((CompoundTag)tag).getUUID("id"));
        return ids;
    }
    private static void prepare(ServerPlayer player) {
        prepare(player.connection.getConnection());
    }
    static ServerPlayer player(GameTestHelper helper) {
        return player(helper,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"p35-shop-fixture"));
    }
    static ServerPlayer player(GameTestHelper helper,com.mojang.authlib.GameProfile profile) {
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var player=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),profile,cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        prepare(connection); // before PlayerLoggedInEvent's mandatory canonical balance send
        player.server.getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }
    private static void prepare(net.minecraft.network.Connection connection) {
        // GameTest's EmbeddedChannel player never performs login negotiation. Declare only
        // these registered channel capabilities; this is NOT a socket/handshake verification.
        var channels=new HashMap<net.minecraft.resources.ResourceLocation,net.neoforged.neoforge.network.registration.NetworkChannel>();
        var bolts=com.github.giannigr.racmc.loot.Rac1BoltNetwork.Balance.TYPE.id();
        channels.put(bolts,new net.neoforged.neoforge.network.registration.NetworkChannel(bolts,"1"));
        var belt=com.github.giannigr.racmc.gear.Rac1BeltNetwork.Snapshot.TYPE.id();
        channels.put(belt,new net.neoforged.neoforge.network.registration.NetworkChannel(belt,"belt-1"));
        var gear=com.github.giannigr.racmc.gear.Rac1GearNetwork.Snapshot.TYPE.id();
        channels.put(gear,new net.neoforged.neoforge.network.registration.NetworkChannel(gear,"gear-2"));
        // Current-main login/tick listeners send these even to a shopper with no Clank equipped.
        for (var id:List.of(com.github.giannigr.racmc.clank.Rac1ClankNetwork.Snapshot.TYPE.id(),
                com.github.giannigr.racmc.clank.Rac1ClankNetwork.Correction.TYPE.id()))
            channels.put(id,new net.neoforged.neoforge.network.registration.NetworkChannel(id,"clank-moveset-2"));
        var hydro=com.github.giannigr.racmc.clank.Rac1HydroNetwork.Snapshot.TYPE.id();
        channels.put(hydro,new net.neoforged.neoforge.network.registration.NetworkChannel(hydro,"clank-hydro-test-1"));
        for (var id:List.of(Rac1ShopNetwork.Command.TYPE.id(),Rac1ShopNetwork.Opened.TYPE.id(),Rac1ShopNetwork.Reply.TYPE.id()))
            channels.put(id,new net.neoforged.neoforge.network.registration.NetworkChannel(id,Rac1ShopNetwork.VERSION));
        for (var id:List.of(Rac1PdaNetwork.Command.TYPE.id(),Rac1PdaNetwork.Opened.TYPE.id(),Rac1PdaNetwork.Reply.TYPE.id()))
            channels.put(id,new net.neoforged.neoforge.network.registration.NetworkChannel(id,"pda-2"));
        var observed=Rac1ShopObserverNetwork.State.TYPE.id();
        channels.put(observed,new net.neoforged.neoforge.network.registration.NetworkChannel(observed,"shop-observer-1"));
        net.neoforged.neoforge.network.registration.ChannelAttributes.setPayloadSetup(connection,
                new net.neoforged.neoforge.network.registration.NetworkPayloadSetup(Map.of(net.minecraft.network.ConnectionProtocol.PLAY,channels)));
    }
}
