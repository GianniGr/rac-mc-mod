package com.github.giannigr.racmc.block;

/** Shared loaded-tick state machine for grounded and airborne explosive crates. */
public final class Rac1ExplosiveFuse {
    public static final int FULL_TICKS = 60;
    public static final int IDLE = -1;
    public static final int TERMINAL = -2;
    private int remaining = IDLE;
    private long lastTick = Long.MIN_VALUE;

    public int remaining() {
        return remaining;
    }

    public void restore(int value, long processedTick) {
        remaining = value == IDLE || value == TERMINAL ? value
                : value >= 0 && value <= FULL_TICKS ? value : TERMINAL;
        lastTick = processedTick;
    }

    public boolean arm(boolean chain, long gameTime) {
        if (remaining != IDLE) {
            return false;
        }
        // One original update is below MC tick resolution: defer chain by one server tick.
        remaining = chain ? 1 : FULL_TICKS;
        lastTick = gameTime;
        return true;
    }

    public boolean tick(long gameTime) {
        if (remaining < 0 || lastTick == gameTime) {
            return false;
        }
        lastTick = gameTime;
        if (remaining > 0) {
            remaining--;
        }
        if (remaining == 0) {
            remaining = TERMINAL; // Reserve emission before any external callbacks.
            return true;
        }
        return false;
    }

    /** Original wrench hit: 5..15 updates at 60 Hz, rounded UP to the 20 Hz server clock.
     * Repeated contacts can shorten, never extend or resurrect an already terminal fuse. */
    public boolean wrenchHit(int sourceUpdates, long gameTime) {
        if (sourceUpdates < 5 || sourceUpdates > 15)
            throw new IllegalArgumentException("Wrench fuse requires 5..15 source updates");
        int ticks = (sourceUpdates + 2) / 3;
        if (remaining == TERMINAL || remaining != IDLE && remaining <= ticks) return false;
        remaining = ticks;
        lastTick = gameTime;
        return true;
    }

    public long lastTick() {
        return lastTick;
    }

    public void save(net.minecraft.nbt.CompoundTag tag) {
        tag.putInt("RacFuse", remaining);
        tag.putLong("RacFuseTick", lastTick);
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("RacFuse")
                && !tag.contains("RacFuse", net.minecraft.nbt.Tag.TAG_INT)) {
            restore(TERMINAL, Long.MIN_VALUE);
            return;
        }
        restore(tag.contains("RacFuse") ? tag.getInt("RacFuse") : IDLE,
                tag.contains("RacFuseTick") ? tag.getLong("RacFuseTick") : Long.MIN_VALUE);
    }

    public static boolean red(int remaining) {
        return red(remaining, 0.0F);
    }

    /**
     * Stateless reference sampler for an aligned counter/fraction pair. Live renderers must
     * use Rac1CrateFlashClock: an unchanged packet counter plus a wrapping fraction rewinds.
     */
    public static boolean red(int remaining, float partialTick) {
        if (remaining <= 0 || remaining > FULL_TICKS) {
            return false;
        }
        double partial = Float.isFinite(partialTick) ? Math.clamp(partialTick, 0.0F, 1.0F) : 0.0;
        return redAtElapsed(FULL_TICKS - remaining + partial);
    }

    /** Original 60 Hz predicate; hold the final frame until authoritative removal. */
    public static boolean redAtElapsed(double elapsedTicks) {
        if (!Double.isFinite(elapsedTicks) || elapsedTicks < 0) {
            return false;
        }
        int originalCounter = Math.max(1,
                179 - (int) Math.floor(3.0 * Math.min(FULL_TICKS, elapsedTicks)));
        return originalCounter % 60 > 40 || originalCounter < 15;
    }
}
