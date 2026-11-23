package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.clank.*;
import java.util.*;
import org.joml.Matrix4f;

/** Bounded eight-placement body history. Source60 input; current native pose is drawn separately. */
public final class Rac1ClankTrail {
    public record Ghost(Matrix4f root,int alpha) {
        public Ghost {root=new Matrix4f(root);}
        @Override public Matrix4f root(){return new Matrix4f(root);}
    }
    private final Matrix4f[] ring=new Matrix4f[8];
    private int count,head,maximumPhase;
    private long epoch=-1,serial=-1,step=-1;
    private Rac1ClankCapability.Action action=Rac1ClankCapability.Action.IDLE;
    private Matrix4f previous;
    public void clear(){Arrays.fill(ring,null);count=head=maximumPhase=0;epoch=serial=step=-1;previous=null;}
    public List<Ghost> sample(Rac1ClankSession.Snapshot view,long sourceStep,int phase,Matrix4f root,boolean valid) {
        var a=view.action();
        if(!valid||view.equipment().selected()!=Rac1ClankEquipment.Module.THRUSTER
                ||a!=Rac1ClankCapability.Action.HIGH_JUMP&&a!=Rac1ClankCapability.Action.LONG_JUMP) {
            clear();return List.of();
        }
        if(epoch!=view.epoch()||serial!=view.actionSerial()||action!=a||sourceStep<step||sourceStep-step>8
                ||previous!=null&&previous.getTranslation(new org.joml.Vector3f())
                        .distanceSquared(root.getTranslation(new org.joml.Vector3f()))>16) {
            clear();epoch=view.epoch();serial=view.actionSerial();action=a;
        }
        if(step<0) {put(root);step=sourceStep;}
        else if(sourceStep>step) {
            long delta=sourceStep-step;
            // Native render sampling is not an original placement clock. Fill only bounded short gaps.
            for(int i=1;i<=delta;i++)put(previous.lerp(root,i/(float)delta,new Matrix4f()));
            step=sourceStep;
        }
        previous=new Matrix4f(root);
        maximumPhase=Math.max(maximumPhase,phase);
        int[] lag=a==Rac1ClankCapability.Action.LONG_JUMP?new int[]{2,4,6}:new int[]{3,5};
        int[] alpha={0x28,0x14,0x0a};
        var result=new ArrayList<Ghost>();
        for(int i=0;i<lag.length;i++) {
            int opacity=Math.max(0,alpha[i]-2*Math.max(0,maximumPhase-12));
            if(count>=lag[i]&&opacity>0)
                result.add(new Ghost(ring[Math.floorMod(head-lag[i],8)],opacity));
        }
        return List.copyOf(result);
    }
    private void put(Matrix4f root){ring[head]=new Matrix4f(root);head=(head+1)%8;count=Math.min(8,count+1);}
}
