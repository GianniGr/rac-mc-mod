package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking;
import com.github.giannigr.racmc.item.weapon.bomb.Rac1Bomb.Phase;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BombGloveFlightRendererTest {
    @Test void onlyCommittedNativeFlightAndSinkingCanDraw() {
        var key = new BombGloveTracking.Key(UUID.randomUUID(),
                ResourceLocation.withDefaultNamespace("overworld"), UUID.randomUUID(), 6);
        var accepted = new BombGloveTracking.Commit(key, 42);
        assertFalse(BombGloveFlightRenderer.draws(null, Phase.FLIGHT));
        assertTrue(BombGloveFlightRenderer.draws(accepted, Phase.FLIGHT));
        assertTrue(BombGloveFlightRenderer.draws(accepted, Phase.SINKING));
        assertFalse(BombGloveFlightRenderer.draws(accepted, Phase.BLAST));
        assertFalse(BombGloveFlightRenderer.draws(accepted, Phase.DEEP_BURST));
    }

    @Test void spinStartsAtServerReleaseTickRatherThanObserverArrival() {
        assertEquals(0, BombGloveFlightRenderer.elapsed(40, 42, .5f));
        assertEquals(0, BombGloveFlightRenderer.elapsed(42, 42, 0));
        assertEquals(.525, BombGloveFlightRenderer.elapsed(52, 42, .5f), 1e-12);
    }
}
