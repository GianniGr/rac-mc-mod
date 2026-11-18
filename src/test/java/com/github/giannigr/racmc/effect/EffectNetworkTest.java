package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.ModSource;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectNetworkTest {
    @Test void actualMinecraftPayloadCodecIsBoundedAndSemanticOnly() {
        var signal = new EffectSignal(EffectSignal.Semantic.CRATE_TNT_BLAST, "minecraft:overworld",
                UUID.randomUUID(), 0, 10, 17, EffectSignal.Op.START, new EffectSignal.Point(1, 2, 3));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            Rac1EffectNetwork.Packet.CODEC.encode(buffer, new Rac1EffectNetwork.Packet(signal));
            assertTrue(buffer.readableBytes() <= EffectSignalCodec.MAX_BYTES + 2);
            assertEquals(signal, Rac1EffectNetwork.Packet.CODEC.decode(buffer).signal());
            buffer.clear();
            buffer.writeVarInt(EffectSignalCodec.MAX_BYTES + 1);
            assertThrows(Exception.class, () -> Rac1EffectNetwork.Packet.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void headlessTransportHasNoClientOrAudioLinkAndHooksFollowTransactions() {
        String network = ModSource.of("com/github/giannigr/racmc/effect/Rac1EffectNetwork.java");
        assertTrue(network.contains(".playToClient("));
        assertFalse(network.contains("playToServer"));
        assertFalse(network.contains("racmc.client"));
        String triggers = ModSource.of("com/github/giannigr/racmc/effect/Rac1EffectTriggers.java");
        assertFalse(triggers.contains("level.random"));
        assertFalse(triggers.contains("SoundEngine"));
        String blaster = ModSource.of("com/github/giannigr/racmc/item/Rac1BlasterServer.java");
        assertTrue(blaster.indexOf("state.recordShot(now)") < blaster.indexOf("Rac1EffectTriggers.shot(arrow)"));
        String blast = ModSource.of("com/github/giannigr/racmc/block/Rac1CrateBlast.java");
        assertTrue(blast.indexOf("Rac1EffectTriggers.blast") > blast.indexOf("playerPushes.forEach"));
    }
}
