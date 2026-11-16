package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-owner only. There is deliberately no client credit/debit request. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class Rac1BoltNetwork {
    private Rac1BoltNetwork() {}
    public record Balance(long total, long revision, boolean pickup) implements CustomPacketPayload {
        public static final Type<Balance> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "bolts"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Balance> CODEC =
                new StreamCodec<>() {
                    public Balance decode(RegistryFriendlyByteBuf buf) {
                        return new Balance(buf.readLong(), buf.readLong(), buf.readBoolean());
                    }
                    public void encode(RegistryFriendlyByteBuf buf, Balance value) {
                        buf.writeLong(value.total()); buf.writeLong(value.revision()); buf.writeBoolean(value.pickup());
                    }
                };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Balance.TYPE, Balance.CODEC, (packet, context) ->
                context.enqueueWork(() -> {
                    var data = context.player().getPersistentData();
                    if (packet.revision() < data.getLong("racmcBoltRevision")) return;
                    boolean fresh = packet.revision() > data.getLong("racmcBoltRevision");
                    data.putLong("racmcBolts", packet.total());
                    data.putLong("racmcBoltRevision", packet.revision());
                    if (fresh && packet.pickup()) data.putLong("racmcBoltPickupNanos", System.nanoTime());
                }));
    }
    public static void send(ServerPlayer player, Rac1BoltLedger ledger, boolean pickup) {
        PacketDistributor.sendToPlayer(player, new Balance(ledger.balance(player.getUUID()),
                ledger.revision(player.getUUID()), pickup));
    }
}
