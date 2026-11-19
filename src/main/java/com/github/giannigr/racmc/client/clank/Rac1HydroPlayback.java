package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Hydro-only local-joint transitions, using the reviewed shared Clank pose sampler/blender. */
public final class Rac1HydroPlayback {
    private record Key(UUID player,int component) {}
    private static final Map<Key,Channel> CHANNELS=new HashMap<>();
    private static final class Channel {
        Object model;
        int sequence=-1;
        double switched;
        Rac1ClankPoseBlend.Bone[] last,from;
    }
    private Rac1HydroPlayback() {}
    public static float[] pose(UUID player,Rac1AnimatedAsset model,int sequence,double seconds,double sourceTime) {
        var clip=java.util.Arrays.stream(model.animations()).filter(c->c.name().equals("sequence_"+sequence))
                .findFirst().orElseThrow();
        var key=new Key(player,model.classNumber());
        if(CHANNELS.size()>=768&&!CHANNELS.containsKey(key))CHANNELS.clear();
        var channel=CHANNELS.computeIfAbsent(key,k->new Channel());
        if(channel.model!=model||channel.sequence!=sequence) {
            channel.from=channel.model==model?channel.last:null;
            channel.model=model;channel.sequence=sequence;channel.switched=sourceTime;
        }
        double elapsed=Math.max(0,sourceTime-channel.switched);
        double duration=clip.times()[clip.keyCount()-1];
        float time=(float)(clip.loop()&&duration>0?seconds%duration:Math.min(seconds,duration));
        var target=Rac1ClankPoseBlend.sample(model,clip,elapsed<2&&channel.from!=null?0:time);
        channel.last=Rac1ClankPoseBlend.blend(channel.from,target,(float)Math.clamp(elapsed/2,0,1));
        return channel.last==null?Rac1AnimationEvaluator.evaluate(model.skeleton(),clip,time,1)
                :Rac1ClankPoseBlend.skin(model,channel.last);
    }
    public static void forget(UUID player){CHANNELS.keySet().removeIf(k->k.player().equals(player));}
    public static void clear(){CHANNELS.clear();}
}
