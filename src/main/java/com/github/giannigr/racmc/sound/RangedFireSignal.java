package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Accepted Blaster session only; no media bytes, asset indices, paths or serverbound request. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public record RangedFireSignal(UUID emitter, UUID session, long sequence, long tick, boolean active,
                               ResourceLocation dimension, double x, double y, double z)
        implements CustomPacketPayload {
    public static final Type<RangedFireSignal> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "ranged_fire"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RangedFireSignal> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUUID(p.emitter); b.writeUUID(p.session); b.writeLong(p.sequence); b.writeLong(p.tick);
                b.writeBoolean(p.active); b.writeResourceLocation(p.dimension);
                b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
            }, b -> new RangedFireSignal(b.readUUID(), b.readUUID(), b.readLong(), b.readLong(),
                    b.readBoolean(), b.readResourceLocation(), b.readDouble(), b.readDouble(), b.readDouble()));
    public static java.util.function.Consumer<RangedFireSignal> receiver = ignored -> {};
    public RangedFireSignal {
        if (emitter == null || session == null || dimension == null || sequence < 0
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(y) > 30_000_000 || Math.abs(z) > 30_000_000)
            throw new IllegalArgumentException("Invalid ranged fire signal");
    }
    public static void emit(ServerPlayer emitter, UUID session, long sequence, boolean active) {
        var signal = new RangedFireSignal(emitter.getUUID(), session, sequence,
                emitter.serverLevel().getGameTime(), active, emitter.level().dimension().location(),
                emitter.getX(), emitter.getY(), emitter.getZ());
        try {
            for (var observer : emitter.serverLevel().players())
                if (observer.distanceToSqr(emitter) <= 32 * 32) PacketDistributor.sendToPlayer(observer, signal);
        } catch (RuntimeException unavailable) {
            com.github.giannigr.racmc.RacMcMod.LOGGER.debug("Optional firing audio signal unavailable: {}", unavailable.toString());
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC,
                (signal, context) -> context.enqueueWork(() -> receiver.accept(signal)));
    }
}
