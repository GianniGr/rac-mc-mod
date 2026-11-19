package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;

/** Item-only joint fit: both original components share one origin and one uniform scale. */
public final class Rac1ClankItemPose {
    private Rac1ClankItemPose() {}
    public record Fit(float x,float y,float z,float scale) {}
    public static Role packRole(Module module) {
        return switch(module) {
            case HELI -> Role.HELI;
            case THRUSTER -> Role.THRUSTER;
            default -> throw new IllegalArgumentException("No wearable item module");
        };
    }
    public static Fit fit(float[] body,float[] pack) {
        float[] a=Rac1ImportedItemPose.bounds(body),b=Rac1ImportedItemPose.bounds(pack);
        if(a==null||b==null)throw new IllegalArgumentException("Missing original item component");
        float x=Math.min(a[0],b[0]),y=Math.min(a[1],b[1]),z=Math.min(a[2],b[2]);
        float maxX=Math.max(a[3],b[3]),maxY=Math.max(a[4],b[4]),maxZ=Math.max(a[5],b[5]);
        double diameter=Math.sqrt((maxX-x)*(maxX-x)+(maxY-y)*(maxY-y)+(maxZ-z)*(maxZ-z));
        if(!Double.isFinite(diameter)||diameter<=0)throw new IllegalArgumentException("Invalid item bounds");
        return new Fit((x+maxX)/2,(y+maxY)/2,(z+maxZ)/2,(float)(.8/diameter));
    }
}
