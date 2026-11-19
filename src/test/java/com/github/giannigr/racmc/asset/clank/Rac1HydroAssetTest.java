package com.github.giannigr.racmc.asset.clank;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class Rac1HydroAssetTest {
    @Test void optional609AssemblyIsValidatedAndDetachedWithoutChangingClnkRoles() throws Exception {
        var source=ClankFixtures.model(609);
        var asset=new Rac1HydroAsset(ClankFixtures.disc(0),source);
        source.mesh().positions()[0]=100;
        assertEquals(0,asset.copyModel().mesh().positions()[0]);
        var copy=asset.copyModel();copy.mesh().positions()[0]=200;
        assertEquals(0,asset.copyModel().mesh().positions()[0]);
        assertEquals(609,copy.classNumber());assertEquals(10,copy.skeleton().jointCount());
        assertEquals(5,copy.animations().length);
        assertEquals(3,Rac1ClankAssets.Role.values().length);
        assertThrows(IOException.class,()->new Rac1HydroAsset(ClankFixtures.disc(0),ClankFixtures.model(608)));
        assertThrows(IllegalArgumentException.class,()->new Rac1HydroAsset("invalid-disc",source));
    }
}
