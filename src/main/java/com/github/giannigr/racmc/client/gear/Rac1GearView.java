package com.github.giannigr.racmc.client.gear;

import com.github.giannigr.racmc.gear.Rac1GearState;
import java.util.Optional;

/** Client-thread mailbox. Identity tokens are the actual connection, level and local player. */
final class Rac1GearView {
    record Context(Object connection, Object level, Object player) {}

    private Object connection;
    private long epoch;
    private long revision;
    private Context owner;
    private Rac1GearState state;

    void accept(Context current, Rac1GearState next) {
        if (connection == current.connection()) {
            if (next.epoch() < epoch || next.revision() < revision) return;
            if (next.epoch() == epoch && (!sameOwner(current) || next.revision() <= revision)) return;
        }
        connection = current.connection();
        epoch = next.epoch();
        revision = next.revision();
        owner = current;
        state = next;
    }

    Optional<Rac1GearState> state(Context current) {
        if (!sameOwner(current)) {
            // Drop the stale world/player but retain the ordering watermark on this connection.
            owner = null;
            state = null;
        }
        return Optional.ofNullable(state);
    }

    private boolean sameOwner(Context current) {
        return owner != null && current != null && owner.connection() == current.connection()
                && owner.level() == current.level() && owner.player() == current.player();
    }

    void clear() {
        connection = null;
        epoch = 0;
        revision = 0;
        owner = null;
        state = null;
    }
}
