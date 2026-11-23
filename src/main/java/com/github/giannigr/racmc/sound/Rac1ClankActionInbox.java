package com.github.giannigr.racmc.sound;

import java.util.*;

/** Bounded event deduplication, not a reconstructing action clock. Consume even if media/budget denies. */
public final class Rac1ClankActionInbox {
    public record Pending(Rac1ClankActionEvent event,long receipt,long generation) {}
    private final LinkedHashMap<UUID,Rac1ClankActionEvent> high=new LinkedHashMap<>();
    private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    public boolean offer(Rac1ClankActionEvent e,long receipt,long generation,boolean ready) {
        var old=high.get(e.actor());
        if(old!=null&&(e.epoch()<old.epoch()||e.epoch()==old.epoch()&&(e.serial()<old.serial()
                ||e.serial()==old.serial()&&(e.sourceStep()<old.sourceStep()
                ||e.sourceStep()==old.sourceStep()&&(e.local()!=0||old.local()==0)))))return false;
        if(old==null&&high.size()>=256) {
            var oldest=high.entrySet().iterator().next();
            // Never evict a fresh dedup watermark just because other tracked actors send events.
            if(e.tick()<=oldest.getValue().tick()+4)return false;
            high.remove(oldest.getKey());
        }
        high.remove(e.actor());high.put(e.actor(),e);
        if(ready&&pending.size()<48)pending.addLast(new Pending(e,receipt,generation));
        return true;
    }
    public List<Pending> drain(long tick,long generation) {
        var result=new ArrayList<Pending>();
        while(!pending.isEmpty()) {
            var p=pending.removeFirst();
            if(p.generation()==generation&&tick>=p.receipt()&&tick-p.receipt()<=2)result.add(p);
        }
        return List.copyOf(result);
    }
    /** Wait only for a same-tick semantic snapshot, retaining original receipt/generation/deadline. */
    public void defer(Pending pending){if(this.pending.size()<48)this.pending.addLast(pending);}
    public static boolean needsSnapshot(Pending p,long epoch,long serial,long serverTick,long localTick) {
        if(localTick<p.receipt()||localTick-p.receipt()>=2)return false;
        var e=p.event();
        return epoch<e.epoch()||epoch==e.epoch()
                &&(serial<e.serial()||serial==e.serial()&&serverTick<e.tick());
    }
    public void clear(){high.clear();pending.clear();}
}
