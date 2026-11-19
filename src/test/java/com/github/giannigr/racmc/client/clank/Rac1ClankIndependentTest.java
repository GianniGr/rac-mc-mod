package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import com.github.giannigr.racmc.clank.Rac1ClankSession;
import com.github.giannigr.racmc.clank.Rac1ClankWire;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Independent T22 gaps: actual retention boundary and exhaustive intent discriminants. */
class Rac1ClankIndependentTest {
    private static Rac1ClankSession.Snapshot snapshot(UUID player, long epoch, long revision, long tick) {
        return new Rac1ClankSession.Snapshot(player, epoch, revision, tick,
                Rac1ClankEquipment.EMPTY, Action.IDLE, 0, 0, 0);
    }

    @Test void observerBoundRejectsNewUuidButAllowsExistingUpdateAndReclaimedSlot() {
        var views = new Rac1ClankViews();
        for (int i = 0; i < 4096; i++)
            assertTrue(views.accept(snapshot(new UUID(0, i), 1, 0, 10)));
        UUID extra = new UUID(1, 0);
        assertFalse(views.accept(snapshot(extra, 1, 0, 10)));
        assertNull(views.get(extra));
        UUID existing = new UUID(0, 7);
        assertTrue(views.accept(snapshot(existing, 1, 1, 11)));
        assertEquals(11, views.get(existing).serverTick());
        views.forget(existing);
        assertTrue(views.accept(snapshot(extra, 1, 0, 10)));
        views.clear();
        assertTrue(views.accept(snapshot(existing, 1, 0, 10)));
    }

    @Test void newerRevisionCannotMoveSameEpochClockBackButNewEpochCanResetClock() {
        var views = new Rac1ClankViews();
        UUID player = new UUID(2, 3);
        assertTrue(views.accept(snapshot(player, 2, 5, 100), 20));
        assertFalse(views.accept(snapshot(player, 2, 6, 99), 21));
        assertEquals(102.5, views.serverTime(player, 22.5));
        assertTrue(views.accept(snapshot(player, 3, 0, 1), 23));
        assertEquals(2.5, views.serverTime(player, 24.5));
        assertFalse(views.accept(snapshot(player, 2, 1000, 1000), 25));
    }

    @Test void everyUnknownIntentDiscriminantAndNonbooleanWornByteIsRejected() {
        byte[] valid = Rac1ClankWire.encode(new Rac1ClankSession.Intent(
                1, 2, Rac1ClankSession.IntentKind.EQUIPMENT, 0, false));
        for (int value = 0; value < 256; value++) {
            final int current = value;
            byte[] kind = valid.clone();
            kind[16] = (byte) current;
            if (current >= Rac1ClankSession.IntentKind.values().length)
                assertThrows(IOException.class, () -> Rac1ClankWire.intent(kind));
            byte[] module = valid.clone();
            module[17] = (byte) current;
            if (current != 0 && current != 2 && current != 3)
                assertThrows(IOException.class, () -> Rac1ClankWire.intent(module));
            byte[] worn = valid.clone();
            worn[18] = (byte) current;
            if (current > 1)
                assertThrows(IOException.class, () -> Rac1ClankWire.intent(worn));
        }
        for (int length : new int[] {0, 1, 18, 20, 4096})
            assertThrows(IOException.class, () -> Rac1ClankWire.intent(new byte[length]));
    }
}
