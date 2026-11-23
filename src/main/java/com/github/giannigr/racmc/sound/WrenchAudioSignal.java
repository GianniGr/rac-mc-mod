package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Separate optional S2C audio edges. No client intent, bank identity or sound bytes. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public record WrenchAudioSignal(Kind kind, UUID emitter, long action, long tick,
        ResourceLocation dimension, double x, double y, double z) implements CustomPacketPayload {
    public enum Kind {
        SLAM_IMPACT("wrench.slam_impact", 32), COMET_RELEASE("wrench.comet_release", 25);

        private final ResourceLocation location;
        private final int range;
        Kind(String path, int range) {
            location = ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path);
            this.range = range;
        }
        public ResourceLocation location() { return location; }
        public int range() { return range; }
    }

    public static final Type<WrenchAudioSignal> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "wrench_audio"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchAudioSignal> CODEC =
            StreamCodec.of((b, s) -> {
                b.writeEnum(s.kind); b.writeUUID(s.emitter);
                b.writeVarLong(s.action); b.writeVarLong(s.tick); b.writeResourceLocation(s.dimension);
                b.writeDouble(s.x); b.writeDouble(s.y); b.writeDouble(s.z);
            }, b -> new WrenchAudioSignal(b.readEnum(Kind.class), b.readUUID(), b.readVarLong(),
                    b.readVarLong(), b.readResourceLocation(), b.readDouble(), b.readDouble(), b.readDouble()));
    public static Consumer<WrenchAudioSignal> receiver = ignored -> {};

    public WrenchAudioSignal {
        if (kind == null || emitter == null || dimension == null || action <= 0 || tick < 0
                || !validPosition(x) || !validPosition(y) || !validPosition(z))
            throw new IllegalArgumentException("Invalid wrench audio edge");
    }

    private static boolean validPosition(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000;
    }

    public boolean canHear(Vec3 listener) {
        return listener.distanceToSqr(x, y, z) <= kind.range() * kind.range();
    }

    public static void emit(ServerPlayer owner, Kind kind, long serial, Vec3 position) {
        var level = owner.serverLevel();
        try {
            var signal = new WrenchAudioSignal(kind, owner.getUUID(), serial, level.getGameTime(),
                    level.dimension().location(), position.x, position.y, position.z);
            for (var player : level.players()) {
                if (signal.canHear(player.position())
                        && player.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(player, signal);
            }
        } catch (RuntimeException unavailable) {
            RacMcMod.LOGGER.debug("Optional wrench audio signal unavailable: {}", unavailable.toString());
        }
    }

    @Override public Type<WrenchAudioSignal> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC,
                (signal, context) -> receiver.accept(signal));
    }
}
