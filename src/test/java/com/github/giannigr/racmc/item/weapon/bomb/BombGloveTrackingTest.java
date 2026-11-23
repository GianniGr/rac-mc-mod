package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Commit;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Held;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Key;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Phase;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BombGloveTrackingTest {
    private static final ResourceLocation WORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final UUID OWNER = new UUID(2, 3);
    private static final UUID EPOCH = new UUID(4, 5);

    private static Key key(long id) { return new Key(OWNER, WORLD, EPOCH, id); }
    private static Held held(long id, long revision, Phase phase) { return new Held(key(id), 42, revision, phase, 91); }

    @Test void flightFirstRejectsDelayedHeldSnapshotsEvenAfterFlightEnds() {
        var view = new BombGloveTracking.View(WORLD);
        var commit = new Commit(key(7), 90);
        view.committed(commit);
        view.held(held(7, 8, Phase.WINDUP));
        view.held(held(6, 5, Phase.READY));
        assertNull(view.heldBomb(OWNER, 42));
        var next = held(9, 10, Phase.READY);
        view.held(next);
        view.committed(commit); // duplicate native tracking or another late sight of the old flight
        assertEquals(next, view.heldBomb(OWNER, 42));
    }

    @Test void acceptedAndAbortedSnapshotsWinAgainstOutOfOrderWindupAndReady() {
        var view = new BombGloveTracking.View(WORLD);
        view.held(held(7, 10, Phase.RELEASED));
        view.held(held(7, 9, Phase.WINDUP));
        assertNull(view.heldBomb(OWNER, 42));
        view.held(held(8, 11, Phase.READY));
        assertNotNull(view.heldBomb(OWNER, 42));
        view.held(held(8, 13, Phase.ABORTED));
        view.held(held(8, 12, Phase.WINDUP));
        assertNull(view.heldBomb(OWNER, 42));
    }

    @Test void lateJoinScopesByActorWorldAndEpoch() {
        var view = new BombGloveTracking.View(WORLD);
        var ready = held(1, 3, Phase.READY);
        view.held(ready);
        assertEquals(ready, view.heldBomb(OWNER, 42));
        assertNull(view.heldBomb(OWNER, 43)); // same UUID, respawned actor
        view.committed(new Commit(new Key(OWNER, WORLD, new UUID(8, 9), 1000), 90)); // persisted old server
        view.committed(new Commit(new Key(OWNER, ResourceLocation.withDefaultNamespace("the_nether"), EPOCH,
                1000), 90));
        assertEquals(ready, view.heldBomb(OWNER, 42));
        assertNull(new BombGloveTracking.View(WORLD).heldBomb(OWNER, 42)); // new client-level object
        assertNull(view.heldBomb(new UUID(8, 1), 42));
    }

    @Test void committedMetadataRoundTripsWithNoSpeculativeDefault() {
        var committed = new Commit(key(7), 90);
        assertEquals(committed, Commit.decode(committed.encode()));
        assertNull(Commit.decode(new net.minecraft.nbt.CompoundTag()));
        var bad = committed.encode();
        bad.putLong("Throw", 0);
        assertNull(Commit.decode(bad));
    }

    @Test void heldPacketRoundTripsAllAuthorityFields() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        var packet = new BombGloveTrackingNetwork(held(7, 12, Phase.RELEASED));
        try {
            BombGloveTrackingNetwork.CODEC.encode(buffer, packet);
            assertEquals(packet, BombGloveTrackingNetwork.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
