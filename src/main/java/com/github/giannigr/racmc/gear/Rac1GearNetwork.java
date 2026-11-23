package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import java.io.IOException;
import java.util.Objects;
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
public final class Rac1GearNetwork {
    private static final int BACK_BYTES = 7;

    @FunctionalInterface
    public interface SelfReceiver {
        void receive(Rac1GearState state, IPayloadContext context);
    }

    private static SelfReceiver selfReceiver = (state, context) -> {};

    private Rac1GearNetwork() {}

    public static void selfReceiver(SelfReceiver receiver) {
        selfReceiver = Objects.requireNonNull(receiver);
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("gear-2"); // The default handlers are on the game thread.
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC,
                (packet, context) -> selfReceiver.receive(packet.state(), context));
        registrar.playToServer(EquipBack.TYPE, EquipBack.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) Rac1GearServer.equipBack(player, packet);
        });
    }

    public record Snapshot(Rac1GearState state) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(id("gear_self"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (out, packet) -> {
                    out.writeLong(packet.state.epoch());
                    out.writeLong(packet.state.revision());
                    out.writeBytes(packet.state.back().save());
                    out.writeByte((packet.state.clankInInventory() ? 1 : 0)
                            | (packet.state.rewardPending() ? 2 : 0) | (packet.state.returnPending() ? 4 : 0));
                },
                in -> {
                    long epoch = in.readLong();
                    long revision = in.readLong();
                    byte[] back = new byte[BACK_BYTES];
                    in.readBytes(back);
                    int flags = in.readUnsignedByte();
                    if (flags > 7) throw new IllegalArgumentException("Invalid Gear availability flags");
                    try {
                        return new Snapshot(new Rac1GearState(epoch, revision, Rac1ClankEquipment.load(back),
                                (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0));
                    } catch (IOException invalid) {
                        throw new IllegalArgumentException("Invalid Gear Back slot", invalid);
                    }
                });

        @Override public Type<Snapshot> type() { return TYPE; }
    }

    /** A conditional equip request has no target player, grant, stack, or selectable module. */
    public record EquipBack(long epoch, long revision, boolean worn) implements CustomPacketPayload {
        public EquipBack {
            if (epoch <= 0 || revision < 0) throw new IllegalArgumentException("Invalid Gear request");
        }

        public static final Type<EquipBack> TYPE = new Type<>(id("gear_equip_back"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EquipBack> CODEC = StreamCodec.of(
                (out, packet) -> {
                    out.writeLong(packet.epoch());
                    out.writeLong(packet.revision());
                    out.writeBoolean(packet.worn());
                },
                in -> {
                    long epoch = in.readLong();
                    long revision = in.readLong();
                    int worn = in.readUnsignedByte();
                    if (worn > 1) throw new IllegalArgumentException("Invalid Gear worn value");
                    return new EquipBack(epoch, revision, worn == 1);
                });

        @Override public Type<EquipBack> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path);
    }
}
