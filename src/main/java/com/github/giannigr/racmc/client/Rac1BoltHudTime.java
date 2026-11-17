package com.github.giannigr.racmc.client;

/** Owner's deliberate four-second timeout, measured on a monotonic wall clock, including menus. */
public final class Rac1BoltHudTime {
    private Rac1BoltHudTime() {}
    public static boolean visible(long pickupNanos, long now) {
        return pickupNanos != 0 && now - pickupNanos >= 0 && now - pickupNanos < 4_000_000_000L;
    }
}
