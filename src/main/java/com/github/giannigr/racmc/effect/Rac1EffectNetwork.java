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
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Packet.TYPE, Packet.CODEC, (packet, context) ->
                context.enqueueWork(() -> receiver.accept(packet.signal())));
    }
    public static void send(ServerLevel level, EffectSignal signal) {
        if (!level.dimension().location().toString().equals(signal.dimension())) return;
        for (var player : level.players())
            if (player.distanceToSqr(signal.anchor().x(), signal.anchor().y(), signal.anchor().z()) <= 128 * 128)
                PacketDistributor.sendToPlayer(player, new Packet(signal));
    }
}
