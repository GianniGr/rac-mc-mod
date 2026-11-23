package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Shop-only bounded wire. No assets, account selector, client price or changes to the bolt protocol. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class Rac1ShopNetwork {
    public enum Action { QUOTE, BUY, CLOSE }
    public record Command(UUID token,long sequence,Action action,int quantity,UUID quote,Rac1ShopOffer offer) implements CustomPacketPayload {
        public Command(UUID token,long sequence,Action action,int quantity,UUID quote) {
            this(token,sequence,action,quantity,quote,Rac1ShopOffer.BLASTER);
        }
        public static final Type<Command> TYPE=Rac1ShopNetwork.type("shop_command");
        public static final StreamCodec<FriendlyByteBuf,Command> CODEC=codec(b -> {
            UUID token=b.readUUID(); long sequence=b.readLong(); Action action=at(Action.values(),b.readUnsignedByte());
            if (action==Action.QUOTE)
                return new Command(token,sequence,action,b.readUnsignedShort(),null,at(Rac1ShopOffer.values(),b.readUnsignedByte()));
            return new Command(token,sequence,action,0,action==Action.BUY ? b.readUUID():null);
        },(b,p) -> {
            b.writeUUID(p.token); b.writeLong(p.sequence); b.writeByte(p.action.ordinal());
            if (p.action==Action.QUOTE) { b.writeShort(p.quantity); b.writeByte(p.offer.ordinal()); }
            if (p.action==Action.BUY) b.writeUUID(p.quote);
        });
        public Command {
            Objects.requireNonNull(token); Objects.requireNonNull(action); Objects.requireNonNull(offer);
            if (sequence<0 || sequence>Rac1ShopSessions.MAX_REQUESTS+1
                    || action!=Action.CLOSE && sequence==0
                    || action==Action.CLOSE && sequence!=0
                    || action==Action.QUOTE && (quantity<1 || quantity>Rac1AmmoPolicy.MAX_ORDER)
                    || action!=Action.QUOTE && (quantity!=0 || offer!=Rac1ShopOffer.BLASTER)
                    || (action==Action.BUY)!=(quote!=null)) throw new IllegalArgumentException("Invalid shop command");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Opened(UUID token,BlockPos position,ResourceLocation dimension,boolean starterAvailable) implements CustomPacketPayload {
        public Opened(UUID token,BlockPos position,ResourceLocation dimension) { this(token,position,dimension,false); }
        public static final Type<Opened> TYPE=Rac1ShopNetwork.type("shop_opened");
        public static final StreamCodec<FriendlyByteBuf,Opened> CODEC=codec(b ->
                new Opened(b.readUUID(),b.readBlockPos(),ResourceLocation.parse(b.readUtf(128)),b.readBoolean()),
                (b,p) -> { b.writeUUID(p.token); b.writeBlockPos(p.position); b.writeUtf(p.dimension.toString(),128);
                    b.writeBoolean(p.starterAvailable); });
        public Opened { Objects.requireNonNull(token); position=Objects.requireNonNull(position).immutable(); Objects.requireNonNull(dimension); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Reply(UUID token,long sequence,Rac1ShopPurchases.Result result) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=Rac1ShopNetwork.type("shop_result");
        public static final StreamCodec<FriendlyByteBuf,Reply> CODEC=codec(b -> {
            UUID token=b.readUUID(); long sequence=b.readLong();
            var status=at(Rac1ShopPurchases.Status.values(),b.readUnsignedByte()); boolean replay=b.readBoolean();
            Rac1ShopPurchases.Quote q=null;
            if (b.readBoolean()) q=new Rac1ShopPurchases.Quote(b.readUUID(),at(Rac1ShopDelivery.Kind.values(),b.readUnsignedByte()),
                    b.readUnsignedShort(),b.readLong(),b.readInt(),b.readUnsignedShort(),b.readLong(),
                    at(Rac1ShopOffer.values(),b.readUnsignedByte()));
            return new Reply(token,sequence,new Rac1ShopPurchases.Result(status,q,replay));
        },(b,p) -> {
            b.writeUUID(p.token); b.writeLong(p.sequence); b.writeByte(p.result.status().ordinal()); b.writeBoolean(p.result.replay());
            var q=p.result.quote(); b.writeBoolean(q!=null);
            if (q!=null) {
                b.writeUUID(q.id()); b.writeByte(q.kind().ordinal()); b.writeShort(q.quantity()); b.writeLong(q.price());
                b.writeInt(q.currentArrows()); b.writeShort(q.deliveredArrows()); b.writeLong(q.expires()); b.writeByte(q.offer().ordinal());
            }
        });
        public Reply {
            Objects.requireNonNull(token); Objects.requireNonNull(result); Objects.requireNonNull(result.status());
            if (sequence<0 || sequence>Rac1ShopSessions.MAX_REQUESTS+1) throw new IllegalArgumentException("Invalid shop reply sequence");
            var q=result.quote();
            if (q==null && (result.status()==Rac1ShopPurchases.Status.QUOTED || result.status()==Rac1ShopPurchases.Status.PURCHASED))
                throw new IllegalArgumentException("Missing shop quote");
            if (q!=null && !validQuote(q,false))
                throw new IllegalArgumentException("Invalid shop quote facts");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Installed only by client setup. Common/server class loading never resolves Minecraft client classes. */
    public interface ClientSink { void opened(Opened packet); void reply(Reply packet); }
    private static ClientSink client;
    private Rac1ShopNetwork() {}
    public static void client(ClientSink sink) { client=Objects.requireNonNull(sink); }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("shop-2");
        r.playToServer(Command.TYPE,Command.CODEC,(packet,context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handle(player,packet);
        }));
        r.playToClient(Opened.TYPE,Opened.CODEC,(packet,context) -> context.enqueueWork(() -> { if (client!=null) client.opened(packet); }));
        r.playToClient(Reply.TYPE,Reply.CODEC,(packet,context) -> context.enqueueWork(() -> { if (client!=null) client.reply(packet); }));
    }
    public static Reply handle(ServerPlayer player,Command command) {
        var service=Rac1ShopTransactionServer.get(player.server);
        Rac1ShopPurchases.Result result;
        if (command.action==Action.CLOSE) { service.close(player,command.token); result=new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.INVALID_SESSION,null,false); }
        else if (command.action==Action.QUOTE) result=service.quote(player,command.token,command.sequence,command.offer,command.quantity);
        else result=service.buy(player,command.token,command.sequence,command.quote);
        var reply=new Reply(command.token,command.sequence,result); send(player,reply); return reply;
    }
    public static void opened(ServerPlayer player,Rac1ShopSessions.Opened opened) {
        try { com.github.giannigr.racmc.loot.Rac1BoltNetwork.send(player,com.github.giannigr.racmc.loot.Rac1BoltLedger.get(player.serverLevel()),false); }
        catch (RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(Rac1ShopNetwork.class).warn("Shop opening balance sync failed",failure); }
        send(player,new Opened(opened.token(),BlockPos.of(opened.vendor().position()),player.level().dimension().location(),
                Rac1StarterClaim.available(player)));
    }
    public static void closed(ServerPlayer player,UUID token) {
        send(player,new Reply(token,0,new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.INVALID_SESSION,null,false)));
    }
    private static void send(ServerPlayer player,CustomPacketPayload packet) {
        try { PacketDistributor.sendToPlayer(player,packet); }
        catch (RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(Rac1ShopNetwork.class).warn("Shop result sync failed; never rerunning a purchase",failure); }
    }
    static boolean validQuote(Rac1ShopPurchases.Quote q,boolean portable) {
        if (q.id()==null || q.kind()==null || q.offer()==null || !q.offer().permitted(portable)
                || q.quantity()<1 || q.quantity()>Rac1AmmoPolicy.MAX_ORDER || q.currentAmmo()<0 || q.expires()<0)
            return false;
        var stock=q.offer().stock();
        if (q.offer()==Rac1ShopOffer.BOMB_STARTER)
            return q.kind()==Rac1ShopDelivery.Kind.WEAPON && q.quantity()==1 && q.price()==0
                    && q.deliveredAmmo()==stock.starterUnits();
        if (q.kind()==Rac1ShopDelivery.Kind.WEAPON)
            return !portable && q.offer()==Rac1ShopOffer.BLASTER && q.quantity()==1 && q.price()==stock.weaponPrice()
                    && q.deliveredAmmo()==Math.max(0,stock.weaponFill()-q.currentAmmo());
        return q.currentAmmo()<=stock.cap()-q.quantity() && q.price()==(long)q.quantity()*stock.unitPrice(portable)
                && q.deliveredAmmo()==q.quantity();
    }
    static <T> T at(T[] values,int ordinal) {
        if (ordinal>=values.length) throw new IllegalArgumentException("Unknown shop wire enum"); return values[ordinal];
    }
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID,name));
    }
    private static <T> StreamCodec<FriendlyByteBuf,T> codec(Function<FriendlyByteBuf,T> read,BiConsumer<FriendlyByteBuf,T> write) {
        return new StreamCodec<>() {
            public T decode(FriendlyByteBuf b) { return read.apply(b); }
            public void encode(FriendlyByteBuf b,T value) { write.accept(b,value); }
        };
    }
}
