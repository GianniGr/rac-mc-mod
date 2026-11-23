package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.effect.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import com.mojang.blaze3d.vertex.*;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Confirmed crossed FX12 quads + FX11 glow. Unavailable original overlay cone tables are not invented. */
final class Rac1ClankFlameRenderer {
    private Rac1ClankFlameRenderer() {}
    static void draw(PoseStack pose,List<Rac1ClankJets.Flame> flames,Rac1EffectClient.LocalPresentation assets,
                     Point camera) {
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        var quad=Rac1EffectClient.localType(assets.generation(),assets.fx(12),EffectMaterial.ADDITIVE);
        var glow=Rac1EffectClient.localType(assets.generation(),assets.fx(11),EffectMaterial.ADDITIVE);
        for(var flame:flames) {
            if(quad!=null) {
                VertexConsumer out=buffers.getBuffer(quad);
                for(int plane=0;plane<2;plane++) {
                    put(out,pose,Rac1ClankFlameGeometry.quad(flame,plane),camera,0xffffffff);
                }
                buffers.endBatch(quad);
            }
            if(glow!=null) {
                put(buffers.getBuffer(glow),pose,Rac1ClankFlameGeometry.glow(flame,camera),
                        camera,EffectMaterial.rgba(0x7f2020ff));buffers.endBatch(glow);
            }
        }
    }
    private static void put(VertexConsumer out,PoseStack pose,List<Point> p,Point camera,int rgba) {
        // Native quad order, converted from original strip order 0,1,2,3.
        for(int i:new int[]{0,1,3,2})out.addVertex(pose.last(),(float)(p.get(i).x()-camera.x()),
                (float)(p.get(i).y()-camera.y()),(float)(p.get(i).z()-camera.z()))
                .setUv(i<2?0:1,i%2==0?0:1).setColor(rgba>>>24,rgba>>>16&255,rgba>>>8&255,rgba&255);
    }
}
