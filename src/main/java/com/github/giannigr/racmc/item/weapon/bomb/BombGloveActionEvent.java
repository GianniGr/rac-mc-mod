package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.RacMcMod;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Server-only, non-cancellable AV notification. Flight reload is not a new action. */
public final class BombGloveActionEvent extends Event {
    public enum Kind { RELEASE, WATER_ENTRY, BLAST, DEEP_BURST }

    private final Kind kind;
    private final Rac1Bomb bomb;

    private BombGloveActionEvent(Kind kind, Rac1Bomb bomb) {
        this.kind = kind;
        this.bomb = bomb;
    }

    public Kind kind() { return kind; }
    public Rac1Bomb bomb() { return bomb; }

    static void emit(Kind kind, Rac1Bomb bomb) {
        try {
            NeoForge.EVENT_BUS.post(new BombGloveActionEvent(kind, bomb));
        } catch (RuntimeException failure) {
            RacMcMod.LOGGER.debug("Optional Bomb Glove presentation unavailable: {}", failure.toString());
        }
    }
}
