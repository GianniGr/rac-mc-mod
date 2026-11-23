package com.github.giannigr.racmc.client.shop;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** One screen's explicit playback requests. No trigger, delay, variant or subtitle policy. */
public final class Rac1ShopVoicePlayback implements AutoCloseable {
    public interface Handle extends AutoCloseable {
        boolean active();
        @Override void close();
    }
    @FunctionalInterface public interface Backend {
        Handle start(Rac1ShopVoiceSource.Clip clip,float gain);
    }
    public enum State { IDLE, WAITING, PLAYING, FINISHED, DROPPED, FAILED, CLOSED }
    public static final class Request {
        private final Rac1ShopVoicePlayback owner;
        private final long revision;
        private Request(Rac1ShopVoicePlayback owner,long revision) { this.owner=owner; this.revision=revision; }
    }
    private final Thread thread=Thread.currentThread();
    private final BooleanSupplier valid;
    private final Backend backend;
    private Handle current;
    private long revision;
    private State state=State.IDLE;
    private RuntimeException failure;
    public Rac1ShopVoicePlayback(BooleanSupplier valid,Backend backend) {
        this.valid=Objects.requireNonNull(valid); this.backend=Objects.requireNonNull(backend);
    }
    public State state() { check(); return state; }
    public RuntimeException failure() { check(); return failure; }
    /** Reserve on the screen thread; any importer completion must return to that thread. */
    public Request request() {
        check();
        if (!live()) return null;
        cancel();
        if (failure!=null) return null;
        state=State.WAITING;
        return new Request(this,revision);
    }
    public boolean complete(Request request,Rac1ShopVoiceSource.Clip clip,float gain) {
        check();
        if (!live() || request==null || request.owner!=this || request.revision!=revision || state!=State.WAITING)
            return false;
        if (clip==null || !Float.isFinite(gain) || gain<0 || gain>1) {
            failure=new IllegalArgumentException("Bounded dialogue clip/gain required"); state=State.FAILED;
            return false;
        }
        try {
            current=Objects.requireNonNull(backend.start(clip,gain));
            // SoundManager.play reserves a channel synchronously; muted/unavailable
            // playback must not be mistaken for a speaking presenter or replayed later.
            if (!current.active()) { release(); state=failure==null ? State.DROPPED:State.FAILED; return false; }
            state=State.PLAYING; return true;
        } catch (RuntimeException error) {
            failure=error; release(); state=State.FAILED; return false;
        }
    }
    public void tick() {
        check();
        if (!live() || current==null) return;
        try {
            if (!current.active()) { release(); state=failure==null ? State.FINISHED:State.FAILED; }
        } catch (RuntimeException error) { failure=error; release(); state=State.FAILED; }
    }
    public void cancel() {
        check();
        if (state==State.CLOSED) return;
        revision++; release(); state=failure==null ? State.IDLE:State.FAILED;
    }
    @Override public void close() {
        check();
        if (state==State.CLOSED) return;
        revision++; release(); state=State.CLOSED;
    }
    private boolean live() {
        if (state==State.CLOSED) return false;
        try { if (valid.getAsBoolean()) return true; }
        catch (RuntimeException error) { failure=error; }
        close(); return false;
    }
    private void release() {
        Handle previous=current; current=null;
        if (previous!=null) try { previous.close(); }
        catch (RuntimeException error) {
            if (failure==null) failure=error; else if (failure!=error) failure.addSuppressed(error);
        }
    }
    private void check() {
        if (Thread.currentThread()!=thread) throw new IllegalStateException("Dialogue owner requires screen thread");
    }
}
