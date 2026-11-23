package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import org.junit.jupiter.api.Test;

class Rac1ClankFlameGeometryTest {
    private static Rac1ClankJets.Flame flame(float scale) {
        return new Rac1ClankJets.Flame(new Rac1ClankNozzles.Frame(new Point(10,0,5),
                new Point(0,1,0),new Point(0,0,1),new Point(1,0,0)),scale,new double[]{.1,0,.05,0});
    }
    @Test void quadTableScaleDoesNotScaleCallbackJitterOrNozzleOffset() {
        for(float scale:new float[]{1,.5f,.125f}) {
            var f=flame(scale);var a=Rac1ClankFlameGeometry.quad(f,0);
            assertEquals(10-.075-.05*scale-.1,a.get(1).x(),1e-9);
            assertEquals(10-.075+.05*scale,a.get(0).x(),1e-9);
            assertEquals(5-.05*scale,a.get(0).z(),1e-9);
            var b=Rac1ClankFlameGeometry.quad(f,1);
            assertEquals(10-.075-.05*scale-.05,b.get(1).x(),1e-9);
        }
        assertThrows(IllegalArgumentException.class,()->Rac1ClankFlameGeometry.quad(flame(1),2));
    }
    @Test void glowSizeIsFixedDuringRampAndFacesPointToViewer() {
        var camera=new Point(0,-20,5);
        var full=Rac1ClankFlameGeometry.glow(flame(1),camera);
        assertEquals(full,Rac1ClankFlameGeometry.glow(flame(.125f),camera));
        Point centre=flame(1).frame().transform(0,0,-.13);
        double[] average=new double[3];
        for(var p:full) {
            average[0]+=p.x()/4;average[1]+=p.y()/4;average[2]+=p.z()/4;
            assertEquals(.08,p.distanceSquared(centre),1e-9);
            assertEquals(0,(p.x()-centre.x())*(camera.x()-centre.x())
                    +(p.y()-centre.y())*(camera.y()-centre.y())
                    +(p.z()-centre.z())*(camera.z()-centre.z()),1e-9);
        }
        assertArrayEquals(new double[]{centre.x(),centre.y(),centre.z()},average,1e-9);
    }
    @Test void degenerateGravityOrViewerBasisDoesNotInventAnotherSocketOrOrientation() {
        Point centre=flame(1).frame().transform(0,0,-.13);
        for(Point camera:new Point[]{centre,centre.add(0,1,0)}) {
            var points=Rac1ClankFlameGeometry.glow(flame(1),camera);
            assertTrue(points.stream().allMatch(centre::equals));
        }
    }
}
