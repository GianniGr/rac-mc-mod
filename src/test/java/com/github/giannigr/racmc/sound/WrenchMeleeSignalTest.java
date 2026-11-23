package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WrenchMeleeSignalTest {
    @Test void negotiatedAndUnnegotiatedListenersReceiveExactlyOneAlternativeEach() {
        var vanilla = fallback(SoundSource.PLAYERS, 1, 1);
        var offer = WrenchMeleeSignal.offer(ResourceLocation.withDefaultNamespace("overworld"),
                WrenchMeleeSignal.Variant.ALTERNATE, new Vec3(5, 20, 6), vanilla);
        assertNotNull(offer);
        assertSame(vanilla, offer.packetFor(false));
        var custom = assertInstanceOf(ClientboundCustomPayloadPacket.class, offer.packetFor(true));
        assertSame(offer, custom.payload());

        var plays = new ArrayList<String>();
        offer.deliver(o -> { plays.add("original"); return true; }, p -> plays.add("unexpected fallback"));
        offer.deliver(o -> false, p -> { assertSame(vanilla, p); plays.add("fallback"); });
        assertEquals(List.of("original", "fallback"), plays);
    }

    @Test void packetRoundTripsVariantImpactAndUnchangedVanillaFallbackFields() {
        var vanilla = fallback(SoundSource.PLAYERS, .9F, 1.1F);
        var offer = WrenchMeleeSignal.offer(ResourceLocation.withDefaultNamespace("the_nether"),
                WrenchMeleeSignal.Variant.DEFAULT, new Vec3(4.4, 72.2, 3.5), vanilla);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            WrenchMeleeSignal.CODEC.encode(buf, offer);
            var decoded = WrenchMeleeSignal.CODEC.decode(buf);
            assertEquals(offer.variant(), decoded.variant());
            assertEquals(offer.dimension(), decoded.dimension());
            assertEquals(offer.impact(), decoded.impact());
            assertEquals(vanilla.getSound(), decoded.fallback().getSound());
            assertEquals(vanilla.getSource(), decoded.fallback().getSource());
            assertEquals(vanilla.getX(), decoded.fallback().getX());
            assertEquals(vanilla.getY(), decoded.fallback().getY());
            assertEquals(vanilla.getZ(), decoded.fallback().getZ());
            assertEquals(vanilla.getVolume(), decoded.fallback().getVolume());
            assertEquals(vanilla.getPitch(), decoded.fallback().getPitch());
            assertEquals(vanilla.getSeed(), decoded.fallback().getSeed());
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test void soundRemapsAndGainCategoryChangesRemainVanilla() {
        var vanilla = fallback(SoundSource.PLAYERS, 1, 1);
        assertTrue(WrenchMeleeSignal.unchanged(vanilla, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1, 1));
        assertFalse(WrenchMeleeSignal.unchanged(vanilla, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 1, 1));
        assertFalse(WrenchMeleeSignal.unchanged(vanilla, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1, 1));
        assertFalse(WrenchMeleeSignal.unchanged(vanilla, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, .5F, 1));
        assertFalse(WrenchMeleeSignal.unchanged(vanilla, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1, .5F));
        var notAttack = new ClientboundSoundPacket(Holder.direct(SoundEvents.ZOMBIE_HURT),
                SoundSource.PLAYERS, 0, 0, 0, 1, 1, 99);
        assertNull(WrenchMeleeSignal.offer(ResourceLocation.withDefaultNamespace("overworld"),
                WrenchMeleeSignal.Variant.DEFAULT, Vec3.ZERO, notAttack));
        assertNull(WrenchMeleeSignal.offer(ResourceLocation.withDefaultNamespace("overworld"),
                WrenchMeleeSignal.Variant.DEFAULT, new Vec3(Double.NaN, 0, 0), vanilla));
        assertEquals(80, Rac1WrenchAudioCatalog.HIT.cue());
        assertEquals(81, Rac1WrenchAudioCatalog.HIT_ALT.cue());
    }

    private static ClientboundSoundPacket fallback(SoundSource source, float volume, float pitch) {
        return new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_ATTACK_STRONG),
                source, -7.2, 22.8, 9.9, volume, pitch, 99);
    }
}
