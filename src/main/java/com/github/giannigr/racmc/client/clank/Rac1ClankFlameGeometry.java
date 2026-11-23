package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.List;

/** NTSC callback facts only; no cone substitute or PAL callback equivalence. */
public final class Rac1ClankFlameGeometry {
    private Rac1ClankFlameGeometry() {}
    public static List<Point> quad(Rac1ClankJets.Flame flame,int plane) {
        if(plane<0||plane>1)throw new IllegalArgumentException("Invalid crossed plane");
        var points=new Point[4];var jitter=flame.jitter();
        for(int i=0;i<4;i++) {
            double side=(i<2?-1:1)*.05*flame.scale();
            double depth=(i%2==0?.05:-.05)*flame.scale()-.075;
            // The callback scales the table first, then subtracts jitter in nozzle units.
            if(i%2==1)depth-=jitter[plane*2+i/2];
            points[i]=flame.frame().transform(plane==0?0:side,plane==0?side:0,depth);
        }
        return List.of(points);
    }
    public static List<Point> glow(Rac1ClankJets.Flame flame,Point camera) {
        Point centre=flame.frame().transform(0,0,-.13);
        Point view=unit(new Point(camera.x()-centre.x(),camera.y()-centre.y(),camera.z()-centre.z()));
        // Source gravity maps to vanilla MC down. The callback uses point-to-viewer, not camera roll.
        Point right=unit(new Point(view.z(),0,-view.x())); // view × (0,-1,0)
        Point up=new Point(view.y()*right.z()-view.z()*right.y(),
                view.z()*right.x()-view.x()*right.z(),view.x()*right.y()-view.y()*right.x());
        var points=new Point[4];
        for(int i=0;i<4;i++) {
            // Fixed table ×0.1. Unlike the quads/mesh, glow size does NOT follow the flame ramp.
            double y=(i<2?-1:1)*.2,z=i%2==0?.2:-.2;
            points[i]=centre.add(right.x()*y+up.x()*z,right.y()*y+up.y()*z,right.z()*y+up.z()*z);
        }
        return List.of(points);
    }
    private static Point unit(Point p) {
        double length=Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z());
        return length==0?p:new Point(p.x()/length,p.y()/length,p.z()/length);
    }
}
