package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.shop.Rac1ShopNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-side installation of the real shop wire; ignores late/unsolicited opens and old-session results. */
public final class Rac1ShopClientPackets implements Rac1ShopNetwork.ClientSink {
    private static BlockPos expected;
    private static ResourceLocation dimension;
    private static long until;
    private static long clickNanos;
    private Rac1ShopClientPackets() {}
    public static void install() { Rac1ShopNetwork.client(new Rac1ShopClientPackets()); }
    public static void expect(BlockPos pos) {
        var mc=Minecraft.getInstance();
        expected=pos.immutable(); dimension=mc.level.dimension().location(); until=mc.level.getGameTime()+100;
        clickNanos=System.nanoTime();
    }
    public static void clear() { expected=null; dimension=null; }
    public static void send(Rac1ShopNetwork.Command command) {
        var connection=Minecraft.getInstance().getConnection();
        if (command!=null && connection!=null && connection.getConnection().isConnected()) {
            try { PacketDistributor.sendToServer(command); }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(Rac1ShopClientPackets.class).warn("Shop command send failed; no local grant or replacement buy",failure);
            }
        }
    }
    @Override public void opened(Rac1ShopNetwork.Opened packet) {
        var mc=Minecraft.getInstance();
        if (mc.screen instanceof Rac1ShopPreviewScreen shop && shop.token().equals(packet.token())) return;
        var content=RacMcClient.assets().content().orElse(null); var path=RacMcClient.media().configuredPath();
        if (mc.screen==null && mc.level!=null && mc.player!=null && mc.player.isAlive() && expected!=null
                && mc.level.getGameTime()<=until && packet.position().equals(expected) && packet.dimension().equals(dimension)
                && mc.level.dimension().location().equals(dimension) && mc.level.hasChunkAt(expected)
                && mc.level.getBlockEntity(expected) instanceof Rac1VendorBlockEntity vendor
                && content!=null && RacMcClient.assets().isReady() && RacMcClient.media().isReady()
                && !RacMcClient.media().isChecking() && path.isPresent()) {
            long clicked=clickNanos;
            clear(); mc.setScreen(new Rac1ShopPreviewScreen(vendor,path.get(),content,content.discKey(),packet.token(),clicked));
        } else send(new Rac1ShopNetwork.Command(packet.token(),0,Rac1ShopNetwork.Action.CLOSE,0,null));
    }
    @Override public void reply(Rac1ShopNetwork.Reply packet) {
        var mc=Minecraft.getInstance();
        if (mc.screen instanceof Rac1ShopPreviewScreen screen) screen.reply(packet);
    }
}
