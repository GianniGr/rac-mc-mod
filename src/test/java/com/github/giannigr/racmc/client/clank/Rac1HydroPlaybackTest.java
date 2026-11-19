package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.clank.ClankFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Rac1HydroPlaybackTest {
    @Test void hydroSequenceTransitionsUseIndependentFiniteOriginalSkeletonChannels() {
        var player=UUID.randomUUID();var asset=ClankFixtures.model(609);
        for(int sequence:new int[]{0,1,3,1,2}) {
            for(int i=0;i<3;i++) {
                var matrices=Rac1HydroPlayback.pose(player,asset,sequence,i/60.0,sequence*10+i);
                assertEquals(160,matrices.length);
                for(float value:matrices)assertTrue(Float.isFinite(value));
            }
        }
        Rac1HydroPlayback.forget(player);Rac1HydroPlayback.clear();
    }
}
