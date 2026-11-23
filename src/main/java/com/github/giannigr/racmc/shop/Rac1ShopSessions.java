package com.github.giannigr.racmc.shop;

import java.util.*;

/**
 * Main-thread, ephemeral protocol foundation, NOT a purchase service. No money, inventory,
 * entitlement or asset paths. Callers must validate loaded vendor/reach/liveness before accept.
 */
public final class Rac1ShopSessions {
    public static final int MAX_SESSIONS = 4096, MAX_REQUESTS = 256, REQUESTS_PER_TICK = 8;
    public static final long IDLE_TICKS = 1200;
    public record Vendor(String dimension, long position, UUID incarnation) {
        public Vendor {
            if (dimension == null || dimension.isBlank() || dimension.length() > 128)
                throw new IllegalArgumentException("Invalid vendor dimension");
            Objects.requireNonNull(incarnation);
        }
    }
    /** Intent fields are bounded transport data, not trusted catalog/product identities. */
    public record Intent(int action, int selection, int quantity) {
        public Intent {
            if (action < 0 || action > 3 || selection < 0 || selection >= 64 || quantity < 0 || quantity > 65535)
                throw new IllegalArgumentException("Invalid shop intent");
        }
    }
    public enum Status { ACCEPTED, REPLAY, INVALID_SESSION, STALE, CONFLICT, RATE_LIMIT, EXHAUSTED }
    public record Opened(UUID token, Vendor vendor) {}
    public record Admission(Status status, long sequence) {}
    private record Receipt(Intent intent) {}
    private static final class Session {
        final UUID token = UUID.randomUUID();
        final Vendor vendor;
        final Map<Long, Receipt> receipts = new HashMap<>();
        long lastTick, rateTick, next = 1;
        int rate;
        Session(Vendor vendor, long tick) { this.vendor = vendor; lastTick = rateTick = tick; }
    }
    private final Map<UUID, Session> owners = new HashMap<>();
    private final Thread thread = Thread.currentThread();

    public Opened open(UUID owner, Vendor vendor, long tick) {
        checkThread(); Objects.requireNonNull(owner); Objects.requireNonNull(vendor); validTick(tick);
        expire(tick);
        if (!owners.containsKey(owner) && owners.size() >= MAX_SESSIONS)
            throw new IllegalStateException("Shop session limit reached");
        Session session = new Session(vendor, tick);
        owners.put(owner, session); // opening another kiosk invalidates this owner's old nonce
        return new Opened(session.token, vendor);
    }

    /** Admission does not authorize a purchase or run mutation/network callbacks. */
    public Admission accept(UUID authenticatedOwner, UUID token, Vendor expectedVendor,
                            long sequence, Intent intent, long tick) {
        checkThread(); validTick(tick); Objects.requireNonNull(intent);
        Session s = owners.get(authenticatedOwner);
        if (s == null || !s.token.equals(token) || !s.vendor.equals(expectedVendor))
            return new Admission(Status.INVALID_SESSION, sequence);
        if (tick < s.lastTick || tick - s.lastTick >= IDLE_TICKS) {
            owners.remove(authenticatedOwner);
            return new Admission(Status.INVALID_SESSION, sequence);
        }
        if (s.rateTick != tick) { s.rateTick = tick; s.rate = 0; }
        if (s.rate >= REQUESTS_PER_TICK) return new Admission(Status.RATE_LIMIT, sequence);
        s.rate++;
        Receipt prior = s.receipts.get(sequence);
        if (prior != null)
            return new Admission(prior.intent.equals(intent) ? Status.REPLAY : Status.CONFLICT, sequence);
        if (sequence != s.next) return new Admission(Status.STALE, sequence);
        if (s.receipts.size() >= MAX_REQUESTS) return new Admission(Status.EXHAUSTED, sequence);
        s.receipts.put(sequence, new Receipt(intent));
        s.next++; s.lastTick = tick;
        return new Admission(Status.ACCEPTED, sequence);
    }

    public void close(UUID authenticatedOwner, UUID token) {
        checkThread();
        Session session = owners.get(authenticatedOwner);
        if (session != null && session.token.equals(token)) owners.remove(authenticatedOwner);
    }
    /** Logout/death/dimension/range invalidation; no committed item refund/regrant exists here. */
    public void invalidate(UUID owner) { checkThread(); owners.remove(owner); }
    public void invalidate(Vendor vendor) { checkThread(); owners.values().removeIf(s -> s.vendor.equals(vendor)); }
    public void clear() { checkThread(); owners.clear(); }
    public void expire(long tick) {
        checkThread(); validTick(tick);
        owners.values().removeIf(s -> tick < s.lastTick || tick - s.lastTick >= IDLE_TICKS);
    }
    /** The only public aggregation: no owner IDs, selections, quantities or tokens. */
    public int viewers(Vendor vendor) {
        checkThread();
        int count = 0;
        for (Session s : owners.values()) if (s.vendor.equals(vendor)) count++;
        return count;
    }
    private void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("Shop sessions require their server thread");
    }
    private static void validTick(long tick) {
        if (tick < 0) throw new IllegalArgumentException("Negative server tick");
    }
}
