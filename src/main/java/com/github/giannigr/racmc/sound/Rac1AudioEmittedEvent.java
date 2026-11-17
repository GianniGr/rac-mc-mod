package com.github.giannigr.racmc.sound;

/** Noncancelable observation of an accepted semantic emission, not a second playback hook. */
public final class Rac1AudioEmittedEvent extends net.neoforged.bus.api.Event {
    private final Rac1AudioSignal signal;
    public Rac1AudioEmittedEvent(Rac1AudioSignal signal) { this.signal = signal; }
    public Rac1AudioSignal signal() { return signal; }
}
