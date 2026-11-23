package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.RacMcConstants;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-client only, effects-owned payload. No ISO bytes or client-trigger request. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class Rac1EffectNetwork {
    private static Consumer<EffectSignal> receiver = ignored -> {};
    private Rac1EffectNetwork() {}
    public static void receiver(Consumer<EffectSignal> value) { receiver = value; }
    public record Packet(EffectSignal signal) implements CustomPacketPayload {
        public static final Type<Packet> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "effect_signal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Packet> CODEC = new StreamCodec<>() {
            @Override public Packet decode(RegistryFriendlyByteBuf buffer) {
                try {
                    return new Packet(EffectSignalCodec.read(buffer.readByteArray(EffectSignalCodec.MAX_BYTES)));
                } catch (IOException failure) { throw new IllegalArgumentException("Invalid effect packet", failure); }
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Packet packet) {
                try { buffer.writeByteArray(EffectSignalCodec.write(packet.signal())); }
                catch (IOException failure) { throw new IllegalArgumentException("Invalid effect packet", failure); }
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Optional distinct channel: older observers cannot decode the new semantic on effect_signal. */
    public record DryBurstPacket(EffectSignal signal) implements CustomPacketPayload {
        public DryBurstPacket {
            if (signal.semantic() != EffectSignal.Semantic.BOMB_GLOVE_DRY_BURST)
                throw new IllegalArgumentException("Expected dry Bomb Glove burst");
        }
        public static final Type<DryBurstPacket> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "bomb_glove_dry_burst"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DryBurstPacket> CODEC =
                Packet.CODEC.map(packet -> new DryBurstPacket(packet.signal()), packet -> new Packet(packet.signal()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(Packet.TYPE, Packet.CODEC, (packet, context) ->
                context.enqueueWork(() -> receiver.accept(packet.signal())));
        registrar.playToClient(DryBurstPacket.TYPE, DryBurstPacket.CODEC, (packet, context) ->
                context.enqueueWork(() -> receiver.accept(packet.signal())));
    }
    public static void send(ServerLevel level, EffectSignal signal) {
        if (!level.dimension().location().toString().equals(signal.dimension())) return;
        CustomPacketPayload packet = signal.semantic() == EffectSignal.Semantic.BOMB_GLOVE_DRY_BURST
                ? new DryBurstPacket(signal) : new Packet(signal);
        for (var player : level.players())
            if (player.distanceToSqr(signal.anchor().x(), signal.anchor().y(), signal.anchor().z()) <= 128 * 128
                    && (!(packet instanceof DryBurstPacket) || player.connection.hasChannel(DryBurstPacket.TYPE)))
                PacketDistributor.sendToPlayer(player, packet);
    }
}
