package com.github.giannigr.racmc.client.shop;

import java.util.*;

/** Source hologram roles plus one cut-view fit correction; never player item/grip transforms. */
public final class Rac1ShopHologram {
    private Rac1ShopHologram() {}
    public record Transform(float[] rotation,float[] position) {}
    public static Transform placement(Rac1ShopPreviewSource source,Rac1ShopPreviewOffer offer) {
        float[] h=source.shop().sceneTables().blasterHolograms();
        boolean ammo=offer.ammo();
        float[] rotation=Arrays.copyOfRange(h,ammo ? 0:12,ammo ? 3:15);
        rotation[2]=0; // Existing first source spin frame.
        float[] position={-1+h[ammo ? 4:16],.05f+h[ammo ? 5:17],1.3f+h[ammo ? 6:18]};
        if (!ammo) {
            var shift=Rac1ShopActorDrawing.rotate(rotation,h[8],h[9],h[10]);
            for (int i=0;i<3;i++) position[i]+=shift[i];
            var actor=source.product();
            var mesh=Rac1ShopActorDrawing.pose(actor,Rac1ShopActorDrawing.initial(actor),0);
            var used=new BitSet();
            for (var t:mesh.triangles()) if (!Rac1ShopActorDrawing.degenerate(mesh,t)) {
                used.set(t.a()); used.set(t.b()); used.set(t.c());
            }
            float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
            for (int i=used.nextSetBit(0);i>=0;i=used.nextSetBit(i+1)) {
                var v=mesh.vertices().get(i);
                float y=Rac1ShopActorDrawing.rotate(rotation,v.x(),v.y(),v.z())[1]+position[1];
                min=Math.min(min,y); max=Math.max(max,y);
            }
            // The weapon mesh is asymmetric about its authored pivot. Fit its drawn horizontal
            // extent over the pad's Y=0 axis; preserve source height, depth, rotation and all ammo offsets.
            position=centerHorizontal(position,min,max,0);
        }
        return new Transform(rotation,position);
    }
    public static float[] centerHorizontal(float[] position,float min,float max,float axis) {
        if (position.length!=3 || !Float.isFinite(min) || !Float.isFinite(max) || min>max || !Float.isFinite(axis))
            throw new IllegalArgumentException("Invalid hologram horizontal extent");
        float[] fitted=position.clone();
        fitted[1]+=axis-(min+max)*.5f;
        return fitted;
    }
}
