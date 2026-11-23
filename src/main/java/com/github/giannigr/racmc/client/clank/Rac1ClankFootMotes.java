package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.effect.EffectAssetRef;
import com.github.giannigr.racmc.effect.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import static com.github.giannigr.racmc.effect.EffectRuntime.*;

/** Source type28 on the existing bounded sprite runtime. No native dust/smoke fallback. */
public final class Rac1ClankFootMotes {
    private static final Point ZERO=new Point(0,0,0);
    private record Burst(int remaining,Random rng) {}
    private static final class Mote {
        long handle;Point at,velocity;float alpha,size,spin;int direction=1,life=252,colour;
    }
    private final EffectRuntime runtime;
    private final EffectAssetRef asset;
    private final Map<UUID,Burst> bursts=new LinkedHashMap<>();
    private final List<Mote> motes=new ArrayList<>();
    private int emitted;
    public Rac1ClankFootMotes(EffectRuntime runtime,EffectAssetRef asset){this.runtime=runtime;this.asset=asset;}
    public void touchdown(UUID actor,long seed) {
        touchdown(actor,seed,5);
    }
    public void touchdown(UUID actor,long seed,int updates) {
        if(updates!=1&&updates!=5)throw new IllegalArgumentException("Unsupported landing burst");
        if(asset!=null&&(bursts.size()<16||bursts.containsKey(actor)))
            bursts.put(actor,new Burst(updates,new Random(seed)));
    }
    public void retain(Set<UUID> actors){bursts.keySet().retainAll(actors);}
    public void cancel(UUID actor){bursts.remove(actor);}
    public void clear(){bursts.clear();for(var m:motes)runtime.stop(m.handle);motes.clear();}
    public int emitted(){return emitted;}
    public int particles(){return motes.size();}
    public void step(Map<UUID,List<Point>> feet) {
        emitted=0;
        for(var it=motes.iterator();it.hasNext();) {
            var m=it.next();
            if(!runtime.contains(m.handle)){it.remove();continue;}
            if(m.direction>0||m.alpha>.03f)m.alpha+=.007f*m.direction;
            else {m.alpha+=.0014f*m.direction;m.size+=5460;}
            if(m.alpha<=.0244f||--m.life<=0){runtime.stop(m.handle);it.remove();continue;}
            if(m.alpha>=.12f){m.direction=-m.direction;m.alpha+=.007f*m.direction;}
            m.size+=5460;m.spin+=.002f;if(m.spin>1)m.spin-=1;
            m.at=m.at.add(m.velocity.x(),m.velocity.y(),m.velocity.z());
            runtime.pose(m.handle,m.at,m.size,new Point(0,0,(int)(m.spin*255)*Math.PI*2/256),
                    m.colour|((int)(m.alpha*255))<<24);
        }
        for(var entry:new ArrayList<>(bursts.entrySet())) {
            var b=entry.getValue();var points=feet.getOrDefault(entry.getKey(),List.of());
            if(points.size()==2)for(var point:points)for(int i=0;i<2;i++)spawn(entry.getKey(),point,b.rng());
            if(b.remaining()<=1)bursts.remove(entry.getKey());
            else bursts.put(entry.getKey(),new Burst(b.remaining()-1,b.rng()));
        }
    }
    private void spawn(UUID actor,Point foot,Random random) {
        if(motes.size()>=256||runtime.remaining()==0)return;
        // LAND spread/jitter; source XY plane maps to MC XZ, source vertical to MC Y.
        float spread=Float.intBitsToFloat(0x3c83126f)+(random.nextFloat()*2-1)*Float.intBitsToFloat(0x3b03126f);
        Point at=foot.add((random.nextFloat()*2-1)*.03,-.05,(random.nextFloat()*2-1)*.03);
        int a=Math.max(1,random.nextInt(0x60)),b=Math.max(1,random.nextInt(a)%64),c=random.nextInt(b);
        int colour=(a+0x8f)|(b+0x8f)<<8|(c%0x30+0x6f)<<16;
        float alpha=random.nextFloat()*Float.intBitsToFloat(0x3cb70e1f)+Float.intBitsToFloat(0x3727c5ac);
        float size=10080+random.nextFloat()*(19950-10080);
        Point velocity=new Point((random.nextFloat()*2-1)*spread,-.0025,(random.nextFloat()*2-1)*spread);
        long handle=runtime.spawn(new Spawn(actor,asset,Geometry.BILLBOARD,Program.OWNER_SPRITE,at,ZERO,
                size,1,ZERO,0,colour,300,0,EffectMaterial.ADDITIVE));
        if(handle==0)return;
        runtime.bounds(handle,1,320);
        var m=new Mote();m.handle=handle;m.at=at;m.velocity=velocity;m.alpha=alpha;m.size=size;m.colour=colour;
        motes.add(m);emitted++;
    }
}
