package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1BeltNetwork {
    private static BiConsumer<Rac1BeltState, IPayloadContext> receiver = (state, context) -> {};

    private Rac1BeltNetwork() {}

    public static void receiver(BiConsumer<Rac1BeltState, IPayloadContext> client) {
        receiver = Objects.requireNonNull(client);
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("belt-1"); // Game-thread handlers.
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC,
                (message, context) -> receiver.accept(message.state(), context));
        registrar.playToServer(Equip.TYPE, Equip.CODEC, (message, context) -> {
            if (context.player() instanceof ServerPlayer player) Rac1BeltServer.request(player, message);
        });
    }

    public record Snapshot(Rac1BeltState state) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(id("belt_self"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.state.epoch());
                    writeSlot(buf, packet.state.boltGrabber());
                },
                buf -> new Snapshot(new Rac1BeltState(buf.readLong(), readSlot(buf))));

        private static void writeSlot(RegistryFriendlyByteBuf buf, Rac1BeltState.Slot slot) {
            buf.writeLong(slot.revision());
            buf.writeByte(slot.availability().wireId());
        }

        private static Rac1BeltState.Slot readSlot(RegistryFriendlyByteBuf buf) {
            return new Rac1BeltState.Slot(buf.readLong(), Rac1BeltState.Availability.fromWire(buf.readUnsignedByte()));
        }

        @Override public Type<Snapshot> type() { return TYPE; }
    }

    public record Equip(Rac1BeltGadget gadget, long epoch, long revision, boolean equipped)
            implements CustomPacketPayload {
        public Equip {
            if (gadget == null || epoch <= 0 || revision <= 0)
                throw new IllegalArgumentException("Invalid Belt request");
        }

        public static final Type<Equip> TYPE = new Type<>(id("belt_equip"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Equip> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeByte(packet.gadget.wireId());
                    buf.writeLong(packet.epoch);
                    buf.writeLong(packet.revision);
                    buf.writeBoolean(packet.equipped);
                },
                buf -> {
                    var gadget = Rac1BeltGadget.fromWire(buf.readUnsignedByte());
                    long epoch = buf.readLong();
                    long revision = buf.readLong();
                    int equipped = buf.readUnsignedByte();
                    if (equipped > 1) throw new IllegalArgumentException("Invalid Belt equip flag");
                    return new Equip(gadget, epoch, revision, equipped == 1);
                });

        @Override public Type<Equip> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path);
    }
}
