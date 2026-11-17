package com.github.giannigr.racmc.effect;

/** Server-local, non-cancellable observation after accepted gameplay. Effects remain optional.
 * Future sound consolidation may observe semantic facts; no SOUND dependency is installed here. */
public final class EffectTriggeredEvent extends net.neoforged.bus.api.Event {
    private final EffectSignal signal;
    public EffectTriggeredEvent(EffectSignal signal) { this.signal = java.util.Objects.requireNonNull(signal); }
    public EffectSignal signal() { return signal; }
}
