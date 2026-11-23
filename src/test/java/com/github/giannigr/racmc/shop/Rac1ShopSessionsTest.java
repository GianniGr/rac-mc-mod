package com.github.giannigr.racmc.shop;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopSessions.*;

class Rac1ShopSessionsTest {
    final Rac1ShopSessions sessions = new Rac1ShopSessions();
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    final Vendor vendor = new Vendor("minecraft:overworld", 1, UUID.randomUUID());
    final Intent intent = new Intent(0, 1, 1);

    @Test void simultaneousViewersStayPrivateAndOneCloseDoesNotCloseAnother() {
        var sa = sessions.open(a, vendor, 0); var sb = sessions.open(b, vendor, 0);
        assertNotEquals(sa.token(), sb.token()); assertEquals(2, sessions.viewers(vendor));
        assertEquals(Status.INVALID_SESSION, sessions.accept(b, sa.token(), vendor, 1, intent, 1).status());
        assertEquals(Status.ACCEPTED, sessions.accept(a, sa.token(), vendor, 1, intent, 1).status());
        assertEquals(Status.ACCEPTED, sessions.accept(b, sb.token(), vendor, 1, new Intent(0, 5, 20), 1).status());
        sessions.close(a, sb.token()); assertEquals(2, sessions.viewers(vendor));
        sessions.close(a, sa.token()); assertEquals(1, sessions.viewers(vendor));
        assertEquals(Status.REPLAY, sessions.accept(b, sb.token(), vendor, 1, new Intent(0, 5, 20), 2).status());
    }
    @Test void replayConflictOrderingAndRateAreBounded() {
        var s = sessions.open(a, vendor, 0);
        assertEquals(Status.STALE, sessions.accept(a, s.token(), vendor, 2, intent, 0).status());
        assertEquals(Status.ACCEPTED, sessions.accept(a, s.token(), vendor, 1, intent, 0).status());
        assertEquals(Status.REPLAY, sessions.accept(a, s.token(), vendor, 1, intent, 0).status());
        assertEquals(Status.CONFLICT, sessions.accept(a, s.token(), vendor, 1, new Intent(1, 1, 1), 0).status());
        for (int i = 0; i < 4; i++) sessions.accept(a, s.token(), vendor, 1, intent, 0);
        assertEquals(Status.RATE_LIMIT, sessions.accept(a, s.token(), vendor, 2, intent, 0).status());
        assertEquals(Status.ACCEPTED, sessions.accept(a, s.token(), vendor, 2, intent, 1).status());
        for (int i = 3; i <= MAX_REQUESTS; i++)
            assertEquals(Status.ACCEPTED, sessions.accept(a, s.token(), vendor, i, intent, i).status());
        assertEquals(Status.EXHAUSTED, sessions.accept(a, s.token(), vendor, MAX_REQUESTS + 1, intent, 257).status());
        assertEquals(Status.REPLAY, sessions.accept(a, s.token(), vendor, 1, intent, 258).status());
    }
    @Test void lifecycleIncarnationReconnectAndRestartRejectOldNonces() {
        var s = sessions.open(a, vendor, 0);
        var replaced = new Vendor(vendor.dimension(), vendor.position(), UUID.randomUUID());
        assertEquals(Status.INVALID_SESSION, sessions.accept(a, s.token(), replaced, 1, intent, 1).status());
        sessions.invalidate(vendor); assertEquals(0, sessions.viewers(vendor));
        var next = sessions.open(a, vendor, 2);
        assertNotEquals(s.token(), next.token());
        assertEquals(Status.INVALID_SESSION, sessions.accept(a, s.token(), vendor, 1, intent, 3).status());
        sessions.invalidate(a);
        assertEquals(Status.INVALID_SESSION, sessions.accept(a, next.token(), vendor, 1, intent, 3).status());
        var idle = sessions.open(a, vendor, 4);
        sessions.expire(4 + IDLE_TICKS); assertEquals(0, sessions.viewers(vendor));
        assertEquals(Status.INVALID_SESSION, sessions.accept(a, idle.token(), vendor, 1, intent, 1204).status());
        var fresh = sessions.open(a, vendor, 1205);
        assertEquals(Status.INVALID_SESSION, new Rac1ShopSessions().accept(a, fresh.token(), vendor, 1, intent, 1206).status());
        sessions.clear(); assertEquals(0, sessions.viewers(vendor));
    }
    @Test void invalidTransportBoundsAndOffThreadAccessFail() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new Intent(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Intent(0, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> new Intent(0, 0, 65536));
        assertThrows(IllegalArgumentException.class, () -> sessions.open(a, vendor, -1));
        var result = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread t = new Thread(() -> { try { sessions.clear(); } catch (Throwable f) { result.set(f); } });
        t.start(); t.join();
        assertInstanceOf(IllegalStateException.class, result.get());
    }
}
