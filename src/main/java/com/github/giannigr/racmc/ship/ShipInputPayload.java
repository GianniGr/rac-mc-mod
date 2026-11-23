package com.github.giannigr.racmc.ship;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public record ShipInputPayload(int craftId, int session, int sequence, int groundSerial, int flags)
        implements CustomPacketPayload {
    public static final Type<ShipInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "travel_ship_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipInputPayload> CODEC = StreamCodec.of(
            (buf, input) -> {
                buf.writeInt(input.craftId);
                buf.writeInt(input.session);
                buf.writeInt(input.sequence);
                buf.writeInt(input.groundSerial);
                buf.writeShort(input.flags);
            },
            buf -> new ShipInputPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUnsignedShort()));

    @Override public Type<ShipInputPayload> type() { return TYPE; }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("travel-ship-1").playToServer(TYPE, CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer pilot) packet.apply(pilot);
        });
    }

    boolean apply(ServerPlayer pilot) {
        // No entity/chunk lookup from an untrusted ID. The server mount is the only candidate.
        return pilot.getVehicle() instanceof Rac1TravelShip ship && ship.getId() == craftId
                && ship.receive(pilot, session, sequence, groundSerial, flags);
    }
}
