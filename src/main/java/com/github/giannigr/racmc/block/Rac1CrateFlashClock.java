package com.github.giannigr.racmc.block;

/**
 * Transient presentation phase, never the gameplay fuse. A snapshot's server tick keeps
 * its remaining count aligned when client partialTick wraps before the next packet arrives.
 */
public final class Rac1CrateFlashClock {
    private double elapsed = Double.NaN;

    public boolean red(int remaining, long processedServerTick, long clientTick, float partialTick) {
        if (remaining <= 0 || remaining > Rac1ExplosiveFuse.FULL_TICKS) {
            elapsed = Double.NaN;
            return false;
        }
        double partial = Float.isFinite(partialTick) ? Math.clamp(partialTick, 0.0F, 1.0F) : 0;
        double sinceSnapshot = processedServerTick == Long.MIN_VALUE ? 0
                : (double) clientTick - processedServerTick + partial;
        // Predict at most one loaded tick, not an offline/world-time gap. Retain the
        // furthest phase across stale packets or client time corrections; never replay red.
        double candidate = Rac1ExplosiveFuse.FULL_TICKS - remaining
                + Math.clamp(sinceSnapshot, 0.0, 1.0);
        elapsed = Double.isNaN(elapsed) ? candidate : Math.max(elapsed, candidate);
        return Rac1ExplosiveFuse.redAtElapsed(elapsed);
    }

    /** Diagnostic visual phase only; not serialized and never decrements the server fuse. */
    public double elapsedTicks() {
        return elapsed;
    }
}
