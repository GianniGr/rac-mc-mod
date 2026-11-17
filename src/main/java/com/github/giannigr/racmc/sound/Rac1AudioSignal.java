package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** S2C only, finite semantic ID/position/seed. No ISO bytes, paths or bank IDs. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public record Rac1AudioSignal(Rac1AudioEvent event, double x, double y, double z, long seed,
                              ResourceLocation dimension)
        implements CustomPacketPayload {
    public static final Type<Rac1AudioSignal> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "audio_signal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Rac1AudioSignal> CODEC =
            StreamCodec.of((b, p) -> {
                b.writeEnum(p.event); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeLong(p.seed);
                b.writeResourceLocation(p.dimension);
            }, b -> new Rac1AudioSignal(b.readEnum(Rac1AudioEvent.class),
                    b.readDouble(), b.readDouble(), b.readDouble(), b.readLong(), b.readResourceLocation()));
    // Installed only by the client subscriber; dedicated servers never link client audio classes.
    public static java.util.function.Consumer<Rac1AudioSignal> receiver = signal -> {};
    public Rac1AudioSignal {
        if (event == null || dimension == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30000000 || Math.abs(z) > 30000000 || Math.abs(y) > 30000000) {
            throw new IllegalArgumentException("Invalid semantic audio position");
        }
    }
    public static void emit(ServerLevel level, Vec3 at, Rac1AudioEvent event) {
        var signal = new Rac1AudioSignal(event, at.x, at.y, at.z, level.random.nextLong(), level.dimension().location());
        for (var player : level.players()) {
            if (player.distanceToSqr(at) <= 32 * 32) PacketDistributor.sendToPlayer(player, signal);
        }
    }
    /** Original thresholds crossed at MC tick resolution; no replay of skipped/unloaded time. */
    public static boolean beep(int before, int after) {
        if (after < 0 || after >= before) return false;
        for (int threshold : new int[]{59, 39, 19, 4}) {
            if (before > threshold && after <= threshold) return true;
        }
        return false;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC,
                (signal, context) -> context.enqueueWork(() -> receiver.accept(signal)));
    }
}
