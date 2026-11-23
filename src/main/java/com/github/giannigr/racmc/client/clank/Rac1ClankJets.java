package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.effect.EffectAssetRef;
import com.github.giannigr.racmc.clank.Rac1ClankAv;
import com.github.giannigr.racmc.effect.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import java.util.function.IntFunction;
import static com.github.giannigr.racmc.effect.EffectRuntime.*;

/** Clank-local, bounded source60 cosmetic controllers using the shared sprite pool/backend. */
public final class Rac1ClankJets {
    private static final Point ZERO=new Point(0,0,0);
    public record Request(long epoch,Rac1ClankAv.Selection state,List<Point> hydro,
                          List<Rac1ClankNozzles.Frame> thruster,double speed,double yaw,double pitch,
                          Point displacement,double surface) {
        public Request {
            hydro=List.copyOf(hydro);thruster=List.copyOf(thruster);
            if(state==null||displacement==null||!Double.isFinite(speed)||speed<0||speed>32
                    ||!Double.isFinite(yaw)||!Double.isFinite(pitch)||hydro.size()>2||thruster.size()>2)
                throw new IllegalArgumentException("Invalid Clank cosmetic request");
        }
        Request withHydro(List<Point> outlets) {
            return new Request(epoch,state,outlets,thruster,speed,yaw,pitch,displacement,surface);
        }
    }
    public record Flame(Rac1ClankNozzles.Frame frame,float scale,double[] jitter) {
        public Flame {jitter=jitter.clone();}
        @Override public double[] jitter(){return jitter.clone();}
    }
    private enum Phase { WAIT,GROW,BURN,SHRINK }
    private static final class Channel {
        long epoch;int bubbleTimer,time;Phase phase=Phase.WAIT;
        Point[] smoke=new Point[2];Random random;
        Channel(UUID id,long epoch){this.epoch=epoch;random=new Random(id.getLeastSignificantBits()^epoch);}
    }
    private static final class Particle {
        UUID owner;long handle;int type,life,remaining,c1,c2;boolean split;
        Point at,velocity;float size,startSize,growth;double surface,angle,rate,amp,spin,rotation,cap;
        Random random;
    }
    private final EffectRuntime runtime;
    private final IntFunction<EffectAssetRef> assets;
    private final Map<UUID,Channel> channels=new HashMap<>();
    private final List<Particle> particles=new ArrayList<>();
    private List<Flame> flames=List.of();
    private final int[] emitted=new int[35];
    private long updates;
    public Rac1ClankJets(EffectRuntime runtime,IntFunction<EffectAssetRef> assets) {
        this.runtime=Objects.requireNonNull(runtime);this.assets=Objects.requireNonNull(assets);
    }
    public int emitted(int type){return emitted[type];}
    public List<Flame> flames(){return flames;}
    public int particles(){return particles.size();}
    public void clear(){channels.clear();particles.clear();flames=List.of();runtime.clear();updates=0;}
    public void step(Map<UUID,Request> requests,Point camera) {
        if(requests.size()>16)throw new IllegalArgumentException("Clank owner budget exceeded");
        updates++;Arrays.fill(emitted,0);updateParticles(camera);
        channels.keySet().retainAll(requests.keySet());
        List<Flame> visible=new ArrayList<>();
        for(var entry:requests.entrySet()) {
            var id=entry.getKey();var r=entry.getValue();var c=channels.get(id);
            if(c==null||c.epoch!=r.epoch()){c=new Channel(id,r.epoch());channels.put(id,c);}
            // A bounded water-column probe may not find a surface (deep water/roof).
            // That removes only surface-pop behavior, not submerged nozzle emission.
            if(r.state().hydroJets()&&r.hydro().size()==2) {
                if(c.bubbleTimer<=0||--c.bubbleTimer<=0) {
                    c.bubbleTimer=Math.max(0,(int)((1-r.speed()/7)*5));
                    for(int i=0;i<4;i++)bubble(id,c,r,r.hydro().get(i%2));
                }
            }
            if(r.thruster().size()!=2){c.phase=Phase.WAIT;c.smoke=new Point[2];continue;}
            float scale=0;
            switch(c.phase) {
                case WAIT->{if(r.state().flames()){c.phase=Phase.GROW;c.time=0;}}
                case GROW->{scale=++c.time/2f;if(c.time>=2){c.phase=Phase.BURN;c.smoke=new Point[2];}}
                case BURN->{
                    scale=1;
                    if(r.state().flames())for(int side=0;side<2;side++)exhaust(id,c,r,side);
                    else {c.phase=Phase.SHRINK;c.time=0;}
                }
                case SHRINK->{scale=Math.max(0,1-++c.time/8f);if(c.time>=8)c.phase=Phase.WAIT;}
            }
            if(scale>0)for(var frame:r.thruster()) {
                double[] jitter=new double[4];for(int i=0;i<4;i++)jitter[i]=c.random.nextDouble()*.1;
                visible.add(new Flame(frame,scale,jitter));
            }
        }
        flames=List.copyOf(visible);
    }
    private Particle create(UUID owner,int type,Point at,Point velocity,float size,int colour,int life,
                            EffectMaterial material,Random random) {
        EffectAssetRef asset=assets.apply(type);
        if(asset==null||particles.size()>=2048||runtime.remaining()==0)return null;
        long handle=runtime.spawn(new Spawn(owner,asset,Geometry.BILLBOARD,Program.OWNER_SPRITE,
                at,ZERO,size,1,ZERO,0,colour,600,0,material));
        if(handle==0)return null;
        runtime.bounds(handle,1,type==34?128:320);
        Particle p=new Particle();p.owner=owner;p.handle=handle;p.type=type;p.at=at;p.velocity=velocity;
        p.size=p.startSize=size;p.c1=colour;p.life=p.remaining=life;p.random=random;p.growth=1;
        particles.add(p);emitted[type]++;return p;
    }
    private void bubble(UUID id,Channel c,Request r,Point nozzle) {
        Random random=c.random;
        // Emit at the animated outlet's local origin, not along the torso axis or world up.
        double k=.75/60;
        Point velocity=r.displacement().add(Math.sin(r.yaw())*Math.cos(r.pitch())*k,
                Math.sin(r.pitch())*k,-Math.cos(r.yaw())*Math.cos(r.pitch())*k);
        var p=create(id,34,nozzle,velocity,5250,0x88a0a0a0,70+random.nextInt(71),EffectMaterial.NORMAL,random);
        if(p==null)return;
        p.cap=(6300+random.nextDouble()*3150)*(.75+random.nextDouble()*.5);
        p.rotation=(random.nextInt(21)-10-128)*Math.PI*2/256;
        p.surface=r.surface();p.angle=random.nextDouble()*Math.PI*2;
        p.rate=(1.7453293+random.nextDouble()*(6.981317-1.7453293))/60;
        p.amp=(.5+random.nextDouble()*.9)/60;
    }
    private void exhaust(UUID id,Channel c,Request r,int side) {
        var frame=r.thruster().get(side);Random random=c.random;
        Point v=direction(frame,Math.cos(random.nextDouble()*Math.PI*2)*.05,
                Math.sin(random.nextDouble()*Math.PI*2)*.05,-(.01+random.nextDouble()*.02));
        spark(id,frame.at(),v,10000,0x4f007fff,0x1fffffff,10,true,random);
        Point previous=c.smoke[side];
        if(previous!=null)spark(id,mix(frame.at(),previous,.5),v,10000,0x4f007fff,0x1fffffff,10,true,random);
        Point smoke=frame.transform(0,0,-.3),out=direction(frame,0,0,-.075);
        out=new Point(out.x(),r.state().glide()?.025-5.0/60:.025,out.z());
        int count=previous==null?1:r.state().glide()?2:4;
        for(int i=0;i<count;i++) {
            Point at=i==0?smoke:mix(smoke,previous,i/6.0);
            at=at.add((random.nextDouble()*2-1)*.05,(random.nextDouble()*2-1)*.05,(random.nextDouble()*2-1)*.05);
            var p=create(id,23,at,out,30000,r.state().glide()?0x20a0a0a0:0x20808080,30,EffectMaterial.NORMAL,random);
            if(p!=null){p.growth=r.state().glide()?1.0165f:1.01f+random.nextFloat()*.065f;
                p.spin=(random.nextBoolean()?6:-6)*Math.PI*2/256;}
        }
        c.smoke[side]=smoke;
    }
    private void spark(UUID id,Point at,Point v,float size,int c1,int c2,int life,boolean split,Random random) {
        var p=create(id,21,at,v,size,c1,life,EffectMaterial.ADDITIVE,random);
        if(p!=null){p.c2=c2;p.split=split;p.rotation=random.nextInt(256)*Math.PI*2/256;}
    }
    private void updateParticles(Point camera) {
        List<Particle> children=new ArrayList<>();
        for(var it=particles.iterator();it.hasNext();) {
            var p=it.next();
            if(!runtime.contains(p.handle)||--p.remaining<=0){runtime.stop(p.handle);it.remove();continue;}
            int colour=p.c1;
            if(p.type==34) {
                if(Double.isFinite(p.surface)&&p.at.y()>p.surface) {
                    p.velocity=new Point(p.velocity.x(),0,p.velocity.z());p.remaining=Math.min(p.remaining,12);
                }
                if(Double.isFinite(p.surface)&&p.at.y()+1.3*40/60>p.surface
                        &&p.random.nextInt(20)==0)p.remaining=Math.min(p.remaining,12);
                p.size=(float)Math.min(p.cap,p.size+840.00006);
                p.velocity=new Point(p.velocity.x()*.97,p.velocity.y(),p.velocity.z()*.97);
                p.at=p.at.add(p.velocity.x(),p.velocity.y(),p.velocity.z());
                double vy=p.velocity.y()+Math.clamp(1.3/60-p.velocity.y(),-5.0/3600,5.0/3600);
                p.velocity=new Point(p.velocity.x(),vy,p.velocity.z());
                p.angle+=p.rate;double a=Math.atan2(camera.z()-p.at.z(),camera.x()-p.at.x())+Math.PI/2;
                double wobble=Math.sin(p.angle)*p.amp;p.at=p.at.add(Math.cos(a)*wobble,0,Math.sin(a)*wobble);
                if(p.remaining<14)colour=(p.c1&0xffffff)|((p.c1>>>24)*p.remaining/14)<<24;
            }else if(p.type==23) {
                p.at=p.at.add(p.velocity.x(),p.velocity.y(),p.velocity.z());p.size*=p.growth;p.rotation+=p.spin;
                int age=p.life-p.remaining;
                int alpha=age<10?32+age*96/10:p.remaining*127/20;
                colour=(p.c1&0xffffff)|Math.min(128,alpha)<<24;
            }else {
                p.at=p.at.add(p.velocity.x(),p.velocity.y(),p.velocity.z());
                p.velocity=p.velocity.add(0,-.001,0);
                p.size=p.startSize*p.remaining/p.life;
                colour=tween(p.c2,p.c1,p.remaining/(double)p.life);
                if(p.split&&(updates&1)==1)children.add(p);
                else p.velocity=scale(p.velocity,.96);
            }
            runtime.pose(p.handle,p.at,Math.max(1,p.size),new Point(0,0,p.rotation),colour);
        }
        for(var p:children) {
            double x=p.random.nextDouble()*2-1,y=p.random.nextDouble()*2-1,z=p.random.nextDouble()*2-1;
            double n=Math.sqrt(x*x+y*y+z*z),speed=Math.sqrt(p.velocity.distanceSquared(ZERO));
            double k=n<1e-9?0:speed*(.21+p.random.nextDouble()*.04)/n;
            Point v=scale(p.velocity.add(x*k,y*k,z*k),.75+p.random.nextDouble()*.2);
            spark(p.owner,p.at,v,p.size,tween(p.c2,p.c1,p.remaining/(double)p.life),p.c2,
                    15+p.random.nextInt(6),false,p.random);
        }
    }
    private static int tween(int a,int b,double t) {
        int result=0;for(int shift=0;shift<32;shift+=8)
            result|=((int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;
        return result;
    }
    private static Point direction(Rac1ClankNozzles.Frame f,double x,double y,double z) {
        Point p=f.transform(x,y,z);return new Point(p.x()-f.at().x(),p.y()-f.at().y(),p.z()-f.at().z());
    }
    private static Point scale(Point p,double k){return new Point(p.x()*k,p.y()*k,p.z()*k);}
    private static Point mix(Point a,Point b,double t){return new Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);}
}
