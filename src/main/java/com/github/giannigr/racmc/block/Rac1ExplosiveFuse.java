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
     * Sample the original 60 Hz counter within a 20 TPS tick. Visual only: no client fuse
     * advancement. The final original frame is held until the authoritative 60-tick removal.
     */
    public static boolean red(int remaining, float partialTick) {
        if (remaining <= 0 || remaining > FULL_TICKS) {
            return false;
        }
        double partial = Float.isFinite(partialTick) ? Math.clamp(partialTick, 0.0F, 1.0F) : 0.0;
        int originalCounter = Math.max(1,
                179 - (int) Math.floor(3.0 * (FULL_TICKS - remaining + partial)));
        return originalCounter % 60 > 40 || originalCounter < 15;
    }
}
