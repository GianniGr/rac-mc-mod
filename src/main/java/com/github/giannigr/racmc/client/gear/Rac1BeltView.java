package com.github.giannigr.racmc.client.gear;

import com.github.giannigr.racmc.gear.Rac1BeltState;

/** Client-thread view, private to the current connection/level/player. */
final class Rac1BeltView {
    record Context(Object connection, Object level, Object player) {}
    private Context owner;
    private Rac1BeltState snapshot;

    Rac1BeltState state(Context current) {
        return sameContext(current) ? snapshot : null;
    }

    void receive(Context current, Rac1BeltState next) {
        if (owner != null && owner.connection() == current.connection()) {
            if (next.epoch() < snapshot.epoch()
                    || next.boltGrabber().revision() < snapshot.boltGrabber().revision()) return;
            if (next.epoch() == snapshot.epoch() && (!sameContext(current)
                    || next.boltGrabber().revision() <= snapshot.boltGrabber().revision())) return;
        }
        owner = current;
        snapshot = next;
    }

    private boolean sameContext(Context current) {
        return owner != null && current != null && owner.connection() == current.connection()
                && owner.level() == current.level() && owner.player() == current.player();
    }

    void clear() {
        owner = null;
        snapshot = null;
    }
}
