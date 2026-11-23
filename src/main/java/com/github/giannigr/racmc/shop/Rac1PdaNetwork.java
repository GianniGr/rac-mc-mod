package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.*;
import java.util.function.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Separate portable channels; no kiosk position, player selector, client price or asset paths. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class Rac1PdaNetwork {
    /** Reuse only bounded intent syntax, not the kiosk handler/session/price assumptions. */
    public record Command(Rac1ShopNetwork.Command intent) implements CustomPacketPayload {
        public static final Type<Command> TYPE=Rac1PdaNetwork.type("pda_command");
        public static final StreamCodec<FriendlyByteBuf,Command> CODEC=
                Rac1ShopNetwork.Command.CODEC.map(Command::new,Command::intent);
        public Command { Objects.requireNonNull(intent); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Opened(UUID token,InteractionHand hand) implements CustomPacketPayload {
        public static final Type<Opened> TYPE=Rac1PdaNetwork.type("pda_opened");
        public static final StreamCodec<FriendlyByteBuf,Opened> CODEC=codec(
                b -> new Opened(b.readUUID(),at(InteractionHand.values(),b.readUnsignedByte())),
                (b,p) -> { b.writeUUID(p.token); b.writeByte(p.hand.ordinal()); });
        public Opened { Objects.requireNonNull(token); Objects.requireNonNull(hand); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Reply(UUID token,long sequence,Rac1ShopPurchases.Result result) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=Rac1PdaNetwork.type("pda_result");
        public static final StreamCodec<FriendlyByteBuf,Reply> CODEC=codec(b -> {
            UUID token=b.readUUID(); long sequence=b.readLong();
            var status=at(Rac1ShopPurchases.Status.values(),b.readUnsignedByte()); boolean replay=b.readBoolean();
            Rac1ShopPurchases.Quote q=null;
            if (b.readBoolean()) {
                UUID id=b.readUUID(); int quantity=b.readUnsignedShort(); long price=b.readLong();
                q=new Rac1ShopPurchases.Quote(id,Rac1ShopDelivery.Kind.AMMO,quantity,price,
                        b.readInt(),quantity,b.readLong(),at(Rac1ShopOffer.values(),b.readUnsignedByte()));
            }
            return new Reply(token,sequence,new Rac1ShopPurchases.Result(status,q,replay));
        },(b,p) -> {
            b.writeUUID(p.token); b.writeLong(p.sequence); b.writeByte(p.result.status().ordinal()); b.writeBoolean(p.result.replay());
            var q=p.result.quote(); b.writeBoolean(q!=null);
            if (q!=null) {
                b.writeUUID(q.id()); b.writeShort(q.quantity()); b.writeLong(q.price());
                b.writeInt(q.currentArrows()); b.writeLong(q.expires()); b.writeByte(q.offer().ordinal());
            }
        });
        public Reply {
            Objects.requireNonNull(token); Objects.requireNonNull(result); Objects.requireNonNull(result.status());
            if (sequence<0 || sequence>Rac1ShopSessions.MAX_REQUESTS+1)
                throw new IllegalArgumentException("Invalid PDA reply sequence");
            var q=result.quote();
            boolean success=result.status()==Rac1ShopPurchases.Status.QUOTED || result.status()==Rac1ShopPurchases.Status.PURCHASED;
            if (success!=(q!=null) && !(result.status()==Rac1ShopPurchases.Status.COMMIT_REJECTED && q!=null))
                throw new IllegalArgumentException("Invalid PDA quote presence");
            if (q!=null && !Rac1ShopNetwork.validQuote(q,true))
                throw new IllegalArgumentException("Invalid PDA quote facts");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public interface ClientSink { void opened(Opened packet); void reply(Reply packet); }
    private static ClientSink client;
    private Rac1PdaNetwork() {}
    public static void client(ClientSink sink) { client=Objects.requireNonNull(sink); }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("pda-2");
        r.playToServer(Command.TYPE,Command.CODEC,(packet,context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handle(player,packet.intent);
        }));
        r.playToClient(Opened.TYPE,Opened.CODEC,(packet,context) -> context.enqueueWork(() -> { if (client!=null) client.opened(packet); }));
        r.playToClient(Reply.TYPE,Reply.CODEC,(packet,context) -> context.enqueueWork(() -> { if (client!=null) client.reply(packet); }));
    }
    private static void handle(ServerPlayer player,Rac1ShopNetwork.Command command) {
        var service=Rac1PdaServer.get(player.server);
        Rac1ShopPurchases.Result result;
        if (command.action()==Rac1ShopNetwork.Action.CLOSE) {
            service.close(player,command.token());
            result=new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.INVALID_SESSION,null,false);
        } else if (command.action()==Rac1ShopNetwork.Action.QUOTE)
            result=service.quote(player,command.token(),command.sequence(),command.offer(),command.quantity());
        else result=service.buy(player,command.token(),command.sequence(),command.quote());
        logger().info("PDA intent session={} seq={} action={} requestedOffer={} quantity={} result={} quotedOffer={} replay={}",
                command.token(),command.sequence(),command.action(),
                command.action()==Rac1ShopNetwork.Action.QUOTE ? command.offer():null,command.quantity(),result.status(),
                result.quote()==null ? null:result.quote().offer(),result.replay());
        send(player,new Reply(command.token(),command.sequence(),result));
    }
    static void opened(ServerPlayer player,UUID token,InteractionHand hand) {
        try { com.github.giannigr.racmc.loot.Rac1BoltNetwork.send(player,
                com.github.giannigr.racmc.loot.Rac1BoltLedger.get(player.serverLevel()),false); }
        catch (RuntimeException failure) { logger().warn("PDA opening balance sync failed",failure); }
        send(player,new Opened(token,hand));
    }
    static void closed(ServerPlayer player,UUID token) {
        send(player,new Reply(token,0,new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.INVALID_SESSION,null,false)));
    }
    private static void send(ServerPlayer player,CustomPacketPayload packet) {
        try { PacketDistributor.sendToPlayer(player,packet); }
        catch (RuntimeException failure) { logger().warn("PDA sync failed; never rerunning a purchase",failure); }
    }
    private static org.slf4j.Logger logger() { return org.slf4j.LoggerFactory.getLogger(Rac1PdaNetwork.class); }
    private static <T> T at(T[] values,int ordinal) {
        if (ordinal<0 || ordinal>=values.length) throw new IllegalArgumentException("Unknown PDA enum"); return values[ordinal];
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
