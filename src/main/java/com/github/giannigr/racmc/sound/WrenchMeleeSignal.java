package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.function.Predicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A listener receives this OR the vanilla packet. The offer embeds the exact vanilla fallback. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public record WrenchMeleeSignal(ResourceLocation dimension, Variant variant, Vec3 impact,
        ClientboundSoundPacket fallback) implements CustomPacketPayload {
    public enum Variant {
        DEFAULT("wrench.melee_impact"), ALTERNATE("wrench.melee_impact_alt");
        private final ResourceLocation location;
        Variant(String id) { location = ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, id); }
        public ResourceLocation location() { return location; }
    }

    public static final Type<WrenchMeleeSignal> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "wrench_melee_audio"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchMeleeSignal> CODEC =
            StreamCodec.of((b, s) -> {
                b.writeResourceLocation(s.dimension); b.writeEnum(s.variant);
                b.writeDouble(s.impact.x); b.writeDouble(s.impact.y); b.writeDouble(s.impact.z);
                ClientboundSoundPacket.STREAM_CODEC.encode(b, s.fallback);
            }, b -> new WrenchMeleeSignal(b.readResourceLocation(), b.readEnum(Variant.class),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    ClientboundSoundPacket.STREAM_CODEC.decode(b)));

    // False means play the embedded vanilla packet. No readiness reports or client request.
    public static Predicate<WrenchMeleeSignal> clientReceiver = ignored -> false;

    public WrenchMeleeSignal {
        if (dimension == null || variant == null || impact == null || !valid(impact.x)
                || !valid(impact.y) || !valid(impact.z) || !allowedFallback(fallback))
            throw new IllegalArgumentException("Invalid wrench melee offer");
    }

    public static WrenchMeleeSignal offer(ResourceLocation dimension, Variant variant, Vec3 impact,
            ClientboundSoundPacket fallback) {
        return valid(impact.x) && valid(impact.y) && valid(impact.z) && allowedFallback(fallback)
                ? new WrenchMeleeSignal(dimension, variant, impact, fallback) : null;
    }

    private static boolean allowedFallback(ClientboundSoundPacket packet) {
        if (packet == null || packet.getSource() == null || !Float.isFinite(packet.getVolume())
                || !Float.isFinite(packet.getPitch())) return false;
        SoundEvent event = packet.getSound().value();
        return event == SoundEvents.PLAYER_ATTACK_STRONG || event == SoundEvents.PLAYER_ATTACK_WEAK;
    }

    public static boolean unchanged(ClientboundSoundPacket packet, SoundEvent sound,
            SoundSource source, float volume, float pitch) {
        return allowedFallback(packet) && packet.getSound().value() == sound && packet.getSource() == source
                && packet.getVolume() == volume && packet.getPitch() == pitch;
    }

    private static boolean valid(double axis) { return Double.isFinite(axis) && Math.abs(axis) <= 30_000_000; }

    /** The action is atomic per listener: never send/play original and fallback together. */
    public void deliver(Predicate<WrenchMeleeSignal> original, java.util.function.Consumer<ClientboundSoundPacket> vanilla) {
        if (!original.test(this)) vanilla.accept(fallback);
    }

    public Packet<?> packetFor(boolean negotiated) {
        return negotiated ? new ClientboundCustomPayloadPacket(this) : fallback;
    }

    @Override public Type<WrenchMeleeSignal> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC,
                (signal, context) -> {
                    if (context.player().level().dimension().location().equals(signal.dimension()))
                        signal.deliver(clientReceiver, context::handle);
                });
    }
}
