package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1SourceTiming;
import com.github.giannigr.racmc.shop.Rac1ShopPurchases;
import java.util.*;
import java.util.function.IntUnaryOperator;

/** OWNER-ACCEPTED PROVISIONAL synchronization, not exact PS2 conversion/update/reset/blend fidelity. */
public final class Rac1ShopPresenter implements AutoCloseable {
    public record Frame(int sequence,float seconds,int streamId,boolean attempted) {}
    /** Source seconds -> signed-range source header-time/16. Not a key index or threshold/60. */
    public static final class Timeline {
        private final float[] seconds;
        private final int[] header;
        public Timeline(Rac1SourceTiming source) {
            if (!source.available()) throw new IllegalArgumentException("Presenter source clock missing");
            seconds=source.sourceSeconds(); header=source.headerTimes16();
            for (int i=0;i<header.length;i++)
                if (header[i]>32767 || i>0 && (header[i]<header[i-1] || seconds[i]<seconds[i-1]))
                    throw new IllegalArgumentException("Unsupported signed/nonmonotonic presenter clock");
            if (duration()<=0) throw new IllegalArgumentException("Empty presenter source clock");
        }
        public float duration() { return seconds[seconds.length-1]; }
        public float cursor(float time) {
            if (!Float.isFinite(time) || time<0) throw new IllegalArgumentException("Invalid presenter time");
            double value=header[header.length-1]/16.0;
            if (time<=seconds[0]) value=header[0]/16.0;
            else for (int i=1;i<seconds.length;i++) if (time<seconds[i] && seconds[i]>seconds[i-1]) {
                double f=(time-seconds[i-1])/(seconds[i]-seconds[i-1]);
                value=(header[i-1]+f*(header[i]-header[i-1]))/16; break;
            }
            // Chosen Java truncation of biased positive value, NOT a claim about the uninspected stub.
            return Rac1ShopVoiceHelpers.selector4((float)value,4,v -> (int)v);
        }
    }
    private final Thread thread=Thread.currentThread();
    private final Map<Integer,Rac1Animation> animations=new HashMap<>();
    private final Map<Integer,Timeline> clocks=new HashMap<>();
    private final Map<Integer,Rac1ShopVoiceSource.Clip> clips=new HashMap<>();
    private final Rac1ShopVoiceTiming timing;
    private final Rac1ShopVoicePlayback voice;
    private final IntUnaryOperator random;
    private Rac1ShopVoiceTiming.Binding speech;
    private float seconds,previousCursor=Float.NEGATIVE_INFINITY;
    private long quietFrames;
    private boolean attempted,purchasePending,closed;
    public Rac1ShopPresenter(Rac1Animation[] animations,Rac1ShopVoiceData data,
                            Rac1ShopVoicePlayback voice,IntUnaryOperator random) {
        if (!data.ready()) throw new IllegalArgumentException("Verified dialogue not ready");
        this.timing=data.timing(); this.voice=Objects.requireNonNull(voice); this.random=Objects.requireNonNull(random);
        for (var a:animations) {
            if (a.name().startsWith("sequence_")) this.animations.put(Integer.parseInt(a.name().substring(9)),a);
        }
        if (!this.animations.containsKey(2)) throw new IllegalArgumentException("Presenter idle missing");
        for (var c:data.clips()) if (clips.put(c.index(),c)!=null) throw new IllegalArgumentException("Duplicate dialogue");
        for (int state=4;state<=6;state++) for (int variant=0;variant<2;variant++) {
            var b=timing.binding(state,variant);
            var a=this.animations.get(b.sequence());
            if (a==null || !clips.containsKey(b.streamId()-10000)) throw new IllegalArgumentException("Missing presenter pairing");
            var clock=new Timeline(a.sourceTiming());
            if (clock.cursor(0)>b.rawPredicateThreshold() || clock.cursor(clock.duration())<b.rawPredicateThreshold())
                throw new IllegalArgumentException("Unreachable presenter threshold for sequence"+b.sequence());
            clocks.put(b.sequence(),clock);
        }
        start(4); // provisional opening policy: once source/PCM is ready in the valid focused screen
    }
    public static boolean purchased(Rac1ShopClientFlow.Mode before,Rac1ShopClientFlow.Mode after,Rac1ShopPurchases.Status status) {
        return (before==Rac1ShopClientFlow.Mode.WAIT_BUY || before==Rac1ShopClientFlow.Mode.UNCERTAIN)
                && after==Rac1ShopClientFlow.Mode.BROWSE && status==Rac1ShopPurchases.Status.PURCHASED;
    }
    public void purchaseAccepted() { check(); if (!closed) { purchasePending=true; quietFrames=0; } }
    public Frame frame() {
        check();
        return new Frame(speech==null ? 2:speech.sequence(),seconds,speech==null ? -1:speech.streamId(),attempted);
    }
    public Frame tick(int referenceFrames,boolean focused) {
        return tick(referenceFrames,focused,true);
    }
    /** Offer switches may suppress NEW dialogue, never masquerade as a focus/lifetime loss. */
    public Frame tick(int referenceFrames,boolean focused,boolean allowSpeech) {
        check();
        if (referenceFrames<0 || referenceFrames>3600) throw new IllegalArgumentException("Bounded presenter update required");
        if (closed) return frame();
        voice.tick();
        if (voice.state()==Rac1ShopVoicePlayback.State.CLOSED) { close(); return frame(); }
        if (!focused) { suspend(); return frame(); }
        if (speech!=null && seconds>=clocks.get(speech.sequence()).duration() && attempted
                && voice.state()!=Rac1ShopVoicePlayback.State.PLAYING) idle();
        if (speech==null && purchasePending) { purchasePending=false; if (allowSpeech) start(6); }
        if (speech==null) {
            quietFrames+=referenceFrames;
            float duration=animations.get(2).times()[animations.get(2).keyCount()-1];
            seconds=duration>0 ? (seconds+referenceFrames/60f)%duration:0;
            if (allowSpeech && quietFrames>=600) start(5); // chosen quiet interval, not native timing certification
        }
        if (speech!=null) {
            var clock=clocks.get(speech.sequence());
            seconds=Math.min(clock.duration(),seconds+referenceFrames/60f);
            float cursor=clock.cursor(seconds);
            if (!attempted && previousCursor<speech.rawPredicateThreshold() && cursor>=speech.rawPredicateThreshold()) {
                attempted=true; // one attempt per sequence epoch, even when muted/dropped/failed
                var request=allowSpeech ? voice.request():null;
                if (request!=null) voice.complete(request,clips.get(speech.streamId()-10000),1);
            }
            previousCursor=cursor;
        }
        return frame();
    }
    public void suspend() {
        check(); if (closed) return;
        voice.cancel(); purchasePending=false; idle();
    }
    private void idle() {
        speech=null; seconds=0; previousCursor=Float.NEGATIVE_INFINITY; attempted=false; quietFrames=0;
    }
    private void start(int state) {
        int variant=random.applyAsInt(2);
        if (variant<0 || variant>1) throw new IllegalArgumentException("Invalid source variant");
        voice.cancel();
        speech=timing.binding(state,variant); seconds=0; previousCursor=Float.NEGATIVE_INFINITY;
        attempted=false; quietFrames=0;
    }
    @Override public void close() {
        check(); if (closed) return;
        closed=true; purchasePending=false; speech=null; seconds=0; voice.close();
        clips.clear(); animations.clear(); clocks.clear();
    }
    private void check() {
        if (Thread.currentThread()!=thread) throw new IllegalStateException("Presenter requires screen thread");
    }
}
