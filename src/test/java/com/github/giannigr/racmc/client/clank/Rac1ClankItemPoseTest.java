package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import org.junit.jupiter.api.Test;

class Rac1ClankItemPoseTest {
    @Test void itemRoleIsMatchingOriginalPackNotAReplacementOrHydro() {
        assertEquals(Role.HELI,Rac1ClankItemPose.packRole(Module.HELI));
        assertEquals(Role.THRUSTER,Rac1ClankItemPose.packRole(Module.THRUSTER));
        assertThrows(IllegalArgumentException.class,()->Rac1ClankItemPose.packRole(Module.NONE));
    }
    @Test void compoundFitUsesBothOriginalComponentsWithOneSharedCenterAndScale() {
        float[] body={-1,-2,-1,1,2,1},pack={-2,-1,-3,2,1,0};
        var fit=Rac1ClankItemPose.fit(body,pack);
        assertEquals(0,fit.x());assertEquals(0,fit.y());assertEquals(-1,fit.z());
        assertEquals(.8/Math.sqrt(48),fit.scale(),1e-7);
        for(float[] mesh:new float[][]{body,pack})for(int i=0;i<mesh.length;i+=3) {
            double x=(mesh[i]-fit.x())*fit.scale(),y=(mesh[i+1]-fit.y())*fit.scale(),
                    z=(mesh[i+2]-fit.z())*fit.scale();
            assertTrue(Math.sqrt(x*x+y*y+z*z)<=.4+1e-7);
        }
    }
    @Test void missingOrInvalidComponentsFailRatherThanSubstituteArt() {
        assertThrows(IllegalArgumentException.class,()->Rac1ClankItemPose.fit(new float[0],new float[]{0,0,0}));
        assertThrows(IllegalArgumentException.class,()->Rac1ClankItemPose.fit(
                new float[]{Float.NaN,0,0},new float[]{1,1,1}));
    }
}
