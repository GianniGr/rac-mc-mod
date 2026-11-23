package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import org.joml.*;

/** Same animated blended chain and native back transform as the visible pack; no guessed torso offsets. */
public final class Rac1ClankNozzles {
    public record Frame(Point at,Point right,Point up,Point into) {
        public Point transform(double x,double y,double z) {
            return at.add(right.x()*x+up.x()*y+into.x()*z,right.y()*x+up.y()*y+into.y()*z,
                    right.z()*x+up.z()*y+into.z()*z);
        }
    }
    private Rac1ClankNozzles() {}
    /** Model 609 lists 0/1 mark the outlets at their terminal joints' local (0,0,0). */
    public static List<Point> hydro(Rac1AnimatedAsset model,float[] chain,Matrix4f mount,Point camera) {
        if(model==null||model.classNumber()!=609)return List.of();
        Point a=point(model,chain,0,mount,camera),b=point(model,chain,1,mount,camera);
        return a==null||b==null?List.of():List.of(a,b);
    }
    public static List<Frame> thruster(Rac1AnimatedAsset model,float[] chain,Matrix4f mount,Point camera) {
        if(model==null||model.classNumber()!=608)return List.of();
        List<Frame> result=new ArrayList<>();
        for(int[] pair:new int[][]{{2,3},{0,1}}) {
            Point a=point(model,chain,pair[0],mount,camera),b=point(model,chain,pair[1],mount,camera);
            if(a==null||b==null)return List.of();
            Vector3d into=new Vector3d(a.x()-b.x(),a.y()-b.y(),a.z()-b.z());
            if(into.lengthSquared()<1e-12)return List.of();
            into.normalize();
            Vector3d right=new Vector3d(into).cross(0,1,0);
            if(right.lengthSquared()<1e-12)right=new Vector3d(into).cross(1,0,0);
            right.normalize();Vector3d up=new Vector3d(right).cross(into);
            result.add(new Frame(b,p(right),p(up),p(into)));
        }
        return List.copyOf(result);
    }
    private static Point point(Rac1AnimatedAsset model,float[] chain,int list,Matrix4f mount,Point camera) {
        int[] path=model.attachmentChain(list);
        if(path.length==0||chain==null||chain.length!=model.skeleton().jointCount()*16)return null;
        int at=path[path.length-1]*16;
        var value=mount.transformPosition(new Vector3f(chain[at+12],chain[at+13],chain[at+14]));
        if(!value.isFinite())return null;
        return camera.add(value.x,value.y,value.z);
    }
    private static Point p(Vector3d v){return new Point(v.x,v.y,v.z);}
}
