package com.github.giannigr.racmc.item.weapon;

/** Two take-out cues, driven only by server-observed main-hand item identity. */
final class BlasterEquipTransition {
    enum Cue { NONE, START, FINISH }

    // Minecraft presentation gap, not the PS2 draw animation's clock.
    private static final int FINISH_DELAY = 2;
    private boolean observed;
    private boolean held;
    private int remaining;

    Cue observe(boolean blasterHeld) {
        if (!observed) {
            observed = true;
            held = blasterHeld;
            return Cue.NONE;
        }
        if (!blasterHeld) {
            held = false;
            remaining = 0;
            return Cue.NONE;
        }
        if (!held) {
            held = true;
            remaining = FINISH_DELAY;
            return Cue.START;
        }
        if (remaining > 0 && --remaining == 0) return Cue.FINISH;
        return Cue.NONE;
    }
}
