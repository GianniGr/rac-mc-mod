package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Vanilla explosion's float3 additive player impulse, without its terrain replay, sound or FX.
 * Server-to-client only; the recipient cannot select entities or author damage.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public record Rac1CrateKnockbackPayload(float x, float y, float z) implements CustomPacketPayload {
    public static final Type<Rac1CrateKnockbackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "crate_knockback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Rac1CrateKnockbackPayload> CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeFloat(payload.x);
                buffer.writeFloat(payload.y);
                buffer.writeFloat(payload.z);
            }, buffer -> new Rac1CrateKnockbackPayload(
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));

    public Rac1CrateKnockbackPayload(Vec3 impulse) {
        this((float) impulse.x, (float) impulse.y, (float) impulse.z);
    }

    public Vec3 applyTo(Vec3 currentVelocity) {
        return currentVelocity.add(x, y, z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, Rac1CrateKnockbackPayload::handle);
    }

    private static void handle(Rac1CrateKnockbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            player.setDeltaMovement(payload.applyTo(player.getDeltaMovement()));
        });
    }
}
