package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Held;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Key;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Phase;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** S2C owner state only; projectile identity rides its native tracked data. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public record BombGloveTrackingNetwork(Held snapshot) implements CustomPacketPayload {
    public static final Type<BombGloveTrackingNetwork> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "bomb_glove_held"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BombGloveTrackingNetwork> CODEC = StreamCodec.of(
            (buf, packet) -> {
                Held snapshot = packet.snapshot();
                Key key = snapshot.key();
                buf.writeUUID(key.owner()).writeResourceLocation(key.dimension()).writeUUID(key.epoch());
                buf.writeVarLong(key.throwId()).writeVarInt(snapshot.actorId()).writeVarLong(snapshot.revision());
                buf.writeEnum(snapshot.phase()).writeLong(snapshot.phaseTick());
            },
            buf -> new BombGloveTrackingNetwork(new Held(new Key(buf.readUUID(), buf.readResourceLocation(),
                    buf.readUUID(), buf.readVarLong()), buf.readVarInt(), buf.readVarLong(),
                    buf.readEnum(Phase.class), buf.readLong())));

    private static BiConsumer<Level, Held> heldReceiver = (level, snapshot) -> {};
    private static Consumer<Rac1Bomb> flightReceiver = bomb -> {};

    public static void installClient(BiConsumer<Level, Held> held, Consumer<Rac1Bomb> flight) {
        heldReceiver = held;
        flightReceiver = flight;
    }

    static void observeClientFlight(Rac1Bomb bomb) { flightReceiver.accept(bomb); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC,
                (packet, context) -> heldReceiver.accept(context.player().level(), packet.snapshot()));
    }

    static void broadcast(ServerPlayer owner, Held state) {
        send(() -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, new BombGloveTrackingNetwork(state)));
    }

    static void sendTo(ServerPlayer observer, Held state) {
        send(() -> PacketDistributor.sendToPlayer(observer, new BombGloveTrackingNetwork(state)));
    }

    private static void send(Runnable packet) {
        try {
            packet.run();
        } catch (RuntimeException absent) {
            RacMcMod.LOGGER.debug("Optional Bomb Glove held tracking unavailable: {}", absent.toString());
        }
    }
}
