package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.shop.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Only a server token following this client's deliberate item-use opens a portable screen. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1PdaClientPackets implements Rac1PdaNetwork.ClientSink {
    private record Expected(Object player,Object level,InteractionHand hand,int slot,ItemStack stack,long until) {}
    private static Expected expected;
    private Rac1PdaClientPackets() {}
    public static void send(Rac1PdaNetwork.Command command) {
        var connection=Minecraft.getInstance().getConnection();
        if (command!=null && connection!=null && connection.getConnection().isConnected()) {
            try { PacketDistributor.sendToServer(command); }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(Rac1PdaClientPackets.class)
                        .warn("PDA send failed; awaiting exact receipt, never granting locally",failure);
            }
        }
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()) return;
        var mc=Minecraft.getInstance();
        if (mc.player==null || event.getEntity()!=mc.player || mc.screen!=null
                || !mc.player.isAlive() || mc.player.isSpectator()) return;
        var stack=mc.player.getItemInHand(event.getHand());
        if (stack.is(RacMcItems.PDA.get()) && stack.getCount()==1)
            expected=new Expected(mc.player,mc.level,event.getHand(),mc.player.getInventory().selected,
                    stack,System.nanoTime()+5_000_000_000L);
    }
    @Override public void opened(Rac1PdaNetwork.Opened packet) {
        var mc=Minecraft.getInstance();
        if (mc.screen instanceof Rac1PdaScreen screen && screen.token().equals(packet.token())) return;
        if (mc.screen instanceof Rac1PdaOriginalScreen screen && screen.token().equals(packet.token())) return;
        var e=expected; expected=null;
        if (e!=null && mc.screen==null && mc.player!=null && mc.player==e.player && mc.level==e.level
                && System.nanoTime()<=e.until && packet.hand()==e.hand
                && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.player.getInventory().selected==e.slot && mc.player.getItemInHand(e.hand)==e.stack
                && e.stack.is(RacMcItems.PDA.get()) && e.stack.getCount()==1) {
            org.slf4j.LoggerFactory.getLogger(Rac1PdaClientPackets.class).info(
                    "PDA opened UI=Rac1PdaOriginalScreen session={} hand={}",packet.token(),packet.hand());
            mc.setScreen(new Rac1PdaOriginalScreen(packet.token(),packet.hand()));
        }
        else send(new Rac1PdaNetwork.Command(new Rac1ShopNetwork.Command(packet.token(),0,Rac1ShopNetwork.Action.CLOSE,0,null)));
    }
    @Override public void reply(Rac1PdaNetwork.Reply packet) {
        var mc=Minecraft.getInstance();
        if (mc.screen instanceof Rac1PdaScreen screen) screen.reply(packet);
        if (mc.screen instanceof Rac1PdaOriginalScreen screen) screen.reply(packet);
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var e=expected;
        if (e==null) return;
        var mc=Minecraft.getInstance();
        if (System.nanoTime()>e.until || mc.player!=e.player || mc.level!=e.level || mc.screen!=null)
            expected=null;
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        expected=null;
        var mc=Minecraft.getInstance();
        if (mc.screen instanceof Rac1PdaScreen || mc.screen instanceof Rac1PdaOriginalScreen) mc.setScreen(null);
        Rac1PdaOriginalSource.clear(); Rac1PdaClosingAudio.clear();
    }
    @EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}
        @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(() -> Rac1PdaNetwork.client(new Rac1PdaClientPackets()));
        }
        @SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
                expected=null;
                var mc=Minecraft.getInstance();
                if (mc.screen instanceof Rac1PdaScreen || mc.screen instanceof Rac1PdaOriginalScreen) mc.setScreen(null);
                Rac1PdaOriginalSource.clear(); Rac1PdaClosingAudio.clear();
            });
        }
    }
}
