package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.*;
import java.util.function.Consumer;
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

/** Approved optional cosmetic channel; older shop-1 payloads and save formats are untouched. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class Rac1ShopObserverNetwork {
    public record State(ResourceLocation dimension,BlockPos position,UUID incarnation,long revision,
                        Rac1ShopMotion.Phase phase,long startFrame,long serverTick) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID,"shop_observed"));
        public static final StreamCodec<FriendlyByteBuf,State> CODEC=new StreamCodec<>() {
            public State decode(FriendlyByteBuf b) {
                var dim=ResourceLocation.parse(b.readUtf(128)); var pos=b.readBlockPos(); var id=b.readUUID();
                long rev=b.readLong(); int kind=b.readUnsignedByte();
                if (kind>=Rac1ShopMotion.Phase.values().length) throw new IllegalArgumentException("Invalid physical phase");
                return new State(dim,pos,id,rev,Rac1ShopMotion.Phase.values()[kind],b.readLong(),b.readLong());
            }
            public void encode(FriendlyByteBuf b,State s) {
                b.writeUtf(s.dimension.toString(),128); b.writeBlockPos(s.position); b.writeUUID(s.incarnation);
                b.writeLong(s.revision); b.writeByte(s.phase.ordinal()); b.writeLong(s.startFrame); b.writeLong(s.serverTick);
            }
        };
        public State {
            Objects.requireNonNull(dimension); position=Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(incarnation); Objects.requireNonNull(phase);
            if (dimension.toString().length()>128 || revision<1 || serverTick<0
                    || serverTick>(Long.MAX_VALUE-1000)/3 || startFrame<0 || startFrame>serverTick*3)
                throw new IllegalArgumentException("Invalid physical stamp");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static Consumer<State> sink=ignored -> {};
    private Rac1ShopObserverNetwork() {}
    public static void client(Consumer<State> receiver) { sink=Objects.requireNonNull(receiver); }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("shop-observer-1").optional().playToClient(State.TYPE,State.CODEC,
                (packet,context) -> context.enqueueWork(() -> sink.accept(packet)));
    }
    static void send(ServerPlayer player,State state) {
        try {
            if (!player.connection.hasChannel(State.TYPE)) return;
            PacketDistributor.sendToPlayer(player,state);
        }
        catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(Rac1ShopObserverNetwork.class).warn("Physical shop state sync failed; never mutating a purchase",failure);
        }
    }
}
