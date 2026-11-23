package com.github.giannigr.racmc.sound;

import static com.github.giannigr.racmc.actions.WrenchActionPresentation.Phase.*;
import static com.github.giannigr.racmc.iso.Rac1DiscRevision.*;
import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WrenchAudioTest {
    @Test void acceptedEdgesAreOneTimeBoundedAndFencedByReloadOrWorldChange() {
        var gate = new WrenchAudioGate();
        var owner = UUID.randomUUID();
        assertFalse(gate.claim(owner, 1, 93, 100));
        assertFalse(gate.claim(owner, 1, 107, 100));
        assertTrue(gate.claim(owner, 1, 100, 100));
        assertFalse(gate.claim(owner, 1, 100, 101));
        assertFalse(gate.claim(owner, 1, 101, 101));
        assertFalse(gate.claim(owner, 2, 99, 101));
        gate.fence(101);
        assertFalse(gate.claim(owner, 2, 101, 101));
        assertTrue(gate.claim(owner, 2, 102, 102));
        gate.worldChanged();
        assertTrue(gate.claim(owner, 1, 5, 5));
    }

    @Test void flightLeaseFollowsOneActionThroughReturnAndStopsAtCatchOrLoss() {
        assertTrue(WrenchAudioGate.flightMatches(7, 7, DETACHED_OUT, 10, 11));
        assertTrue(WrenchAudioGate.flightMatches(7, 7, DETACHED_BACK, 12, 12));
        assertFalse(WrenchAudioGate.flightMatches(7, 8, DETACHED_BACK, 12, 12));
        assertFalse(WrenchAudioGate.flightMatches(7, 7, DETACHED_BACK, 12, 19));
        assertFalse(WrenchAudioGate.flightMatches(7, 7, THROW_WINDUP, 12, 12));
        assertFalse(WrenchAudioGate.flightMatches(7, 7, CATCH, 12, 12));
        assertFalse(WrenchAudioGate.flightMatches(7, 0, IDLE, 12, 12));
    }

    @Test void packetCarriesOnlySemanticEdgeAndHasBoundedListenerRange() {
        var dim = ResourceLocation.withDefaultNamespace("overworld");
        var owner = UUID.randomUUID();
        var release = new WrenchAudioSignal(WrenchAudioSignal.Kind.COMET_RELEASE,
                owner, 3, 8, dim, 1, 2, 3);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            WrenchAudioSignal.CODEC.encode(buffer, release);
            assertEquals(release, WrenchAudioSignal.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
        assertTrue(release.canHear(new Vec3(26, 2, 3)));
        assertFalse(release.canHear(new Vec3(26.001, 2, 3)));
        assertThrows(IllegalArgumentException.class, () -> new WrenchAudioSignal(
                release.kind(), owner, 3, 8, dim, Double.NaN, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new WrenchAudioSignal(
                release.kind(), owner, 0, 8, dim, 1, 2, 3));
    }

    @Test void catalogKeepsIndependentRegionalGrainPinsAndRejectsMismatchedMetadata() {
        var slam = Rac1WrenchAudioCatalog.SLAM;
        var comet = Rac1WrenchAudioCatalog.COMET;
        assertEquals(82, slam.cue());
        assertEquals(-1, slam.loopStart());
        assertEquals(33, comet.cue());
        assertEquals(28, comet.loopStart());
        assertEquals(2352, comet.end());
        assertEquals(slam.grainHash(NTSC_U_V1_00), slam.grainHash(PAL_V2_00));
        assertNotEquals(comet.grainHash(NTSC_U_V1_00), comet.grainHash(PAL_V2_00));

        byte[] def = new byte[32], cue = new byte[12], palGrain = new byte[40], ntscGrain = new byte[40];
        ntscGrain[0] = 1;
        var synthetic = new Rac1WrenchAudioCatalog.Pin(33, 219, 28, 84,
                SoundBytes.hash(def), SoundBytes.hash(cue), SoundBytes.hash(palGrain),
                SoundBytes.hash(ntscGrain), "a".repeat(64));
        var d = new Rac1SoundMap.Definition(33, def);
        var t = new Rac1SoundBank.Tone(33, 219, 127, 127, 72, 0, 1, cue, palGrain,
                "a".repeat(64), new PsAdpcm.Decoded(new byte[168], 3, 28, 84, true));
        assertTrue(synthetic.matches(PAL_V2_00, d, t));
        assertFalse(synthetic.matches(NTSC_U_V1_00, d, t));
        assertFalse(synthetic.matches(PAL_V2_00, new Rac1SoundMap.Definition(82, def), t));
    }
}
