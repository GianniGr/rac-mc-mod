package com.github.giannigr.racmc.block;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.ModSource;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class Rac1CrateKnockbackPayloadTest {
    @Test
    void payloadMatchesVanillaFloatImpulseAndAdditivePlayerSemantics() {
        Vec3 push = new Vec3(0.47551703603176826, 0.15454303104171987, -0.031289871263);
        ClientboundExplodePacket vanilla = new ClientboundExplodePacket(0, 0, 0, 4,
                List.of(), push, Explosion.BlockInteraction.KEEP,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
        Rac1CrateKnockbackPayload payload = new Rac1CrateKnockbackPayload(push);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            Rac1CrateKnockbackPayload.CODEC.encode(buffer, payload);
            assertEquals(12, buffer.readableBytes());
            Rac1CrateKnockbackPayload decoded = Rac1CrateKnockbackPayload.CODEC.decode(buffer);
            assertEquals(vanilla.getKnockbackX(), decoded.x());
            assertEquals(vanilla.getKnockbackY(), decoded.y());
            assertEquals(vanilla.getKnockbackZ(), decoded.z());
            Vec3 existing = new Vec3(2, -0.7, 1.3);
            assertEquals(existing.add(vanilla.getKnockbackX(), vanilla.getKnockbackY(),
                    vanilla.getKnockbackZ()), decoded.applyTo(existing));
            assertNotEquals(push, decoded.applyTo(existing));
        } finally {
            buffer.release();
        }
    }

    @Test
    void transportIsClientboundMainThreadImpulseOnlyWithoutVanillaFxReplay() {
        String payload = ModSource.of(
                "com/github/giannigr/racmc/block/Rac1CrateKnockbackPayload.java");
        assertTrue(payload.contains(".playToClient(TYPE, CODEC"));
        assertTrue(payload.contains("context.enqueueWork"));
        assertTrue(payload.contains("payload.applyTo(player.getDeltaMovement())"));
        assertFalse(payload.contains("playToServer"));
        assertFalse(payload.contains("hurt("));
        assertFalse(payload.contains("finalizeExplosion("));
        String blast = ModSource.of("com/github/giannigr/racmc/block/Rac1CrateBlast.java");
        assertTrue(blast.contains("!player.isCreative() || !player.getAbilities().flying"));
        assertTrue(blast.contains("PacketDistributor.sendToPlayer"));
        assertFalse(blast.contains("new ClientboundExplodePacket"));
    }
}
