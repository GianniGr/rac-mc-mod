package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Small intent-only C2S protocol; no target, pose, inventory, damage or position in requests. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class WrenchActionNetwork {
    private static Consumer<Snapshot> clientReceiver = snapshot -> {};

    private WrenchActionNetwork() {}

    public static void clientReceiver(Consumer<Snapshot> receiver) {
        clientReceiver = receiver;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("wrench-actions-1");
        registrar.playToServer(Intent.TYPE, Intent.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player && packet.serial >= 0
                    && packet.action >= 0 && packet.action < WrenchActionState.Intent.values().length) {
                WrenchActions.intent(player, packet.serial,
                        WrenchActionState.Intent.values()[packet.action]);
            }
        });
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC,
                (packet, context) -> clientReceiver.accept(packet));
    }

    static void broadcast(ServerPlayer player, boolean available, WrenchActionPresentation view) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new Snapshot(player.getUUID(), player.serverLevel().getGameTime(), available, view));
    }

    public record Intent(long serial, int action) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = new Type<>(id("wrench_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeLong(packet.serial);
                    buffer.writeByte(packet.action);
                }, buffer -> new Intent(buffer.readLong(), buffer.readUnsignedByte()));

        @Override
        public Type<Intent> type() { return TYPE; }
    }

    public record Snapshot(UUID player, long serverTick, boolean available,
            WrenchActionPresentation view) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(id("wrench_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUUID(packet.player);
                    buffer.writeLong(packet.serverTick);
                    buffer.writeBoolean(packet.available);
                    WrenchActionPresentation view = packet.view;
                    buffer.writeLong(view.serial());
                    buffer.writeByte(view.phase().ordinal());
                    buffer.writeInt(view.bodySequence());
                    buffer.writeInt(view.wrenchSequence());
                    buffer.writeDouble(view.clipSeconds());
                    buffer.writeDouble(view.wrenchClipSeconds());
                    buffer.writeDouble(view.bodyBlendWeight());
                    buffer.writeDouble(view.wrenchBlendWeight());
                    buffer.writeBoolean(view.heldVisible());
                    buffer.writeDouble(view.detachedPosition().x());
                    buffer.writeDouble(view.detachedPosition().y());
                    buffer.writeDouble(view.detachedPosition().z());
                    buffer.writeDouble(view.localSpinRadians());
                }, buffer -> {
                    UUID player = buffer.readUUID();
                    long tick = buffer.readLong();
                    boolean available = buffer.readBoolean();
                    long serial = buffer.readLong();
                    int phase = buffer.readUnsignedByte();
                    if (phase >= WrenchActionPresentation.Phase.values().length) {
                        throw new IllegalArgumentException("Unknown wrench phase");
                    }
                    return new Snapshot(player, tick, available, new WrenchActionPresentation(
                            serial, WrenchActionPresentation.Phase.values()[phase],
                            buffer.readInt(), buffer.readInt(), buffer.readDouble(),
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                            buffer.readBoolean(), new ActionVector(buffer.readDouble(),
                                    buffer.readDouble(), buffer.readDouble()), buffer.readDouble()));
                });

        @Override
        public Type<Snapshot> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path);
    }
}
