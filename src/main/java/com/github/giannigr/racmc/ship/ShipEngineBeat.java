package com.github.giannigr.racmc.ship;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A transient physical transition for players already tracking the ship. Never pairing data or NBT. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public record ShipEngineBeat(int shipId, UUID shipUuid, Kind kind, long serial, long serverTick)
        implements CustomPacketPayload {
    public enum Kind { LIFTOFF, TOUCHDOWN }

    public static final Type<ShipEngineBeat> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "travel_ship_engine_beat"));
    static final StreamCodec<RegistryFriendlyByteBuf, ShipEngineBeat> CODEC = StreamCodec.of(
            (buf, beat) -> {
                buf.writeVarInt(beat.shipId);
                buf.writeUUID(beat.shipUuid);
                buf.writeEnum(beat.kind);
                buf.writeVarLong(beat.serial);
                buf.writeVarLong(beat.serverTick);
            },
            buf -> new ShipEngineBeat(buf.readVarInt(), buf.readUUID(), buf.readEnum(Kind.class),
                    buf.readVarLong(), buf.readVarLong()));

    @Override public Type<ShipEngineBeat> type() { return TYPE; }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // v2 also requires the Turbo and normal-forward entity data on both peers.
        event.registrar(ShipCockpitRefit.ENABLED ? "travel-ship-ratio-3" : "travel-ship-legacy-3")
                .playToClient(TYPE, CODEC, (beat, context) -> {
            var level = context.player().level();
            if (level.isClientSide && level.getEntity(beat.shipId) instanceof Rac1TravelShip ship && !ship.isRemoved()) {
                ship.receiveEngineBeat(beat);
            }
        });
    }

    /** Client admission and a short sampling window for rendering. Deduplicate by serial in consumers. */
    static final class Inbox {
        private static final int SAMPLE_TICKS = 2;
        private long lastSerial;
        private long lastServerTick = -1;
        private int receivedAt;
        private ShipEngineBeat beat;

        boolean offer(ShipEngineBeat candidate, int entityId, UUID uuid, int now) {
            if (candidate.shipId != entityId || !uuid.equals(candidate.shipUuid)
                    || candidate.serial <= lastSerial || candidate.serverTick < 0
                    || candidate.serverTick < lastServerTick) return false;
            lastSerial = candidate.serial;
            lastServerTick = candidate.serverTick;
            receivedAt = now;
            beat = candidate;
            return true;
        }

        ShipEngineBeat sample(int now) {
            int age = now - receivedAt;
            return beat != null && age >= 0 && age < SAMPLE_TICKS ? beat : null;
        }

        void clearSample() {
            beat = null; // Keep the watermark within this tracking lifetime.
        }
    }
}
