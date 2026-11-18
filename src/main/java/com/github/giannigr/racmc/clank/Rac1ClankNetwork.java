package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.RacMcConstants;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers bounded semantic foundations; no enabled equipment or movement authority. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class Rac1ClankNetwork {
    private static Consumer<Rac1ClankSession.Snapshot> receiver = snapshot -> {};
    private Rac1ClankNetwork() {}
    public static void receiver(Consumer<Rac1ClankSession.Snapshot> consumer) {
        receiver = java.util.Objects.requireNonNull(consumer);
    }
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("clank-foundations-1");
        registrar.playToServer(Intent.TYPE, Intent.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                Rac1ClankServer.intent(player, packet.value());
        });
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC,
                (packet, context) -> receiver.accept(packet.value()));
    }
    public record Intent(Rac1ClankSession.Intent value) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = new Type<>(id("clank_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC = StreamCodec.of(
                (out, packet) -> out.writeBytes(Rac1ClankWire.encode(packet.value())),
                in -> {
                    byte[] bytes = new byte[Rac1ClankWire.INTENT_BYTES]; in.readBytes(bytes);
                    try { return new Intent(Rac1ClankWire.intent(bytes)); }
                    catch (IOException bad) { throw new IllegalArgumentException(bad); }
                });
        @Override public Type<Intent> type() { return TYPE; }
    }
    public record Snapshot(Rac1ClankSession.Snapshot value) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(id("clank_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (out, packet) -> out.writeBytes(Rac1ClankWire.encode(packet.value())),
                in -> {
                    byte[] bytes = new byte[Rac1ClankWire.SNAPSHOT_BYTES]; in.readBytes(bytes);
                    try { return new Snapshot(Rac1ClankWire.snapshot(bytes)); }
                    catch (IOException bad) { throw new IllegalArgumentException(bad); }
                });
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path);
    }
}
