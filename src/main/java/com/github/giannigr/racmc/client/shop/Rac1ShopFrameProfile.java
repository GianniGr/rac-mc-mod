package com.github.giannigr.racmc.client.shop;

import java.util.Arrays;
import java.util.function.LongSupplier;

/** Opt-in CPU wall timings only. Nested stages are not additive; no GPU synchronization added. */
final class Rac1ShopFrameProfile {
    enum Stage { FRAME_CADENCE, RENDER_EVENT, CLIENT_TICK, SCREEN_TICK, GUI_SUBMIT,
        PANEL_CREATE, PRESENTER, TICKER, COMPOSITION, READBACK_GL, READBACK_PACK,
        IMAGE_CONVERT, TEXTURE_UPLOAD, KIOSK_POSE, ACTOR_POSE, ACTOR_PROJECT, ACTOR_SUBMIT }
    static final int FRAMES=120, SAMPLES=4096;
    private static Session active;
    private Rac1ShopFrameProfile() {}

    static void open() {
        if (!Boolean.getBoolean("racmc.shop.frameProfile")) return;
        finish("replaced");
        active=new Session(System::nanoTime);
    }
    static long begin() { return active==null ? 0:active.clock.getAsLong(); }
    static void end(Stage stage,long start) { if (active!=null && start!=0) active.add(stage,start); }
    static void phase(String phase) { if (active!=null) active.phase=phase; }
    static void phase(com.github.giannigr.racmc.shop.Rac1ShopMotion.Phase phase,boolean observer) {
        if (active!=null) active.phase=observer ? "OBSERVER_"+phase.name():phase.name();
    }
    static void frameStart() { if (active!=null) active.frameStart(); }
    static void frameEnd() {
        if (active!=null) {
            active.frameEnd();
            if (active.frames>=FRAMES || active.clock.getAsLong()-active.opened>=10_000_000_000L)
                finish("bounded");
        }
    }
    static void finish(String reason) {
        if (active==null) return;
        var done=active; active=null;
        org.slf4j.LoggerFactory.getLogger(Rac1ShopFrameProfile.class).info("Gadgetron frame profile {}:\n{}",reason,done.report());
    }

    static final class Session {
        private final LongSupplier clock;
        private final long opened;
        private final long[] offset=new long[SAMPLES],duration=new long[SAMPLES];
        private final int[] frame=new int[SAMPLES];
        private final Stage[] stages=new Stage[SAMPLES];
        private final String[] phases=new String[SAMPLES];
        private int size,frames,dropped;
        private long previousFrame,renderStart;
        private String phase="LOADING";
        Session(LongSupplier clock) { this.clock=clock; opened=clock.getAsLong(); }
        void add(Stage stage,long start) {
            long now=clock.getAsLong();
            if (size==SAMPLES) { dropped++; return; }
            offset[size]=start-opened; duration[size]=Math.max(0,now-start);
            frame[size]=frames; stages[size]=stage; phases[size]=phase; size++;
        }
        void frameStart() {
            long now=clock.getAsLong();
            if (previousFrame!=0) add(Stage.FRAME_CADENCE,previousFrame);
            previousFrame=now; renderStart=now;
        }
        void frameEnd() { if (renderStart!=0) { add(Stage.RENDER_EVENT,renderStart); renderStart=0; frames++; } }
        String report() {
            var out=new StringBuilder("CPU_WALL_NS nested-not-additive; cadence includes work outside render event; NOT GPU-present\n");
            out.append("frames=").append(frames).append(" samples=").append(size).append(" dropped=").append(dropped).append('\n');
            for (var stage:Stage.values()) {
                var values=new long[size]; int n=0;
                for (int i=0;i<size;i++) if (stages[i]==stage) values[n++]=duration[i];
                if (n==0) continue;
                Arrays.sort(values,0,n);
                out.append(stage).append(" count=").append(n).append(" p50=").append(values[(n-1)/2])
                        .append(" p95=").append(values[(int)Math.ceil(n*.95)-1]).append(" max=").append(values[n-1]).append('\n');
            }
            out.append("frame,phase,stage,offsetNs,durationNs\n");
            for (int i=0;i<size;i++) out.append(frame[i]).append(',').append(phases[i]).append(',').append(stages[i])
                    .append(',').append(offset[i]).append(',').append(duration[i]).append('\n');
            return out.toString();
        }
    }
}
