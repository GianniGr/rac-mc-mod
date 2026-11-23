package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.clank.*;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class Rac1ClankPreparedAssetsTest {
    @Test void cpuPreparationMatchesExistingIdleIconVerticesNormalsAndJointFit() throws Exception {
        var source=ClankFixtures.bundle();
        var stages=new EnumMap<Rac1ClankPreparedAssets.Stage,Long>(Rac1ClankPreparedAssets.Stage.class);
        var prepared=Rac1ClankPreparedAssets.prepare(source,4,stages::put);
        for(var module:List.of(Module.HELI,Module.THRUSTER)) {
            var body=source.copyModel(Role.CLANK);var pack=source.copyModel(Rac1ClankItemPose.packRole(module));
            var bodyPose=Rac1ImportedItemPose.sample(body,1,0);var packPose=Rac1ImportedItemPose.sample(pack,1,0);
            var icon=prepared.item(module);
            assertArrayEquals(bodyPose.positions(),icon.bodyPose().positions());
            assertArrayEquals(bodyPose.normals(),icon.bodyPose().normals());
            assertArrayEquals(packPose.positions(),icon.packPose().positions());
            assertArrayEquals(packPose.normals(),icon.packPose().normals());
            assertEquals(Rac1ClankItemPose.fit(bodyPose.positions(),packPose.positions()),icon.fit());
            assertSame(prepared.model(Role.CLANK),icon.body());
            assertSame(prepared.model(Rac1ClankItemPose.packRole(module)),icon.pack());
        }
        assertSame(prepared.item(Module.HELI).bodyPose(),prepared.item(Module.THRUSTER).bodyPose());
        assertNull(prepared.item(Module.NONE));
        assertEquals(Set.of(Rac1ClankPreparedAssets.Stage.values()),stages.keySet());
        assertTrue(stages.values().stream().allMatch(n->n>=0));
    }
    @Test void generationAndIdentityTagNeverAuthorizeStalePublication() throws Exception {
        var prepared=Rac1ClankPreparedAssets.prepare(ClankFixtures.bundle(),7);
        assertTrue(prepared.matches(ClankFixtures.disc(0),7));
        assertFalse(prepared.matches(ClankFixtures.disc(0),8));
        assertFalse(prepared.matches(ClankFixtures.disc(1),7));
        assertFalse(prepared.matches(null,7));
        assertThrows(IllegalArgumentException.class,()->Rac1ClankPreparedAssets.prepare(ClankFixtures.bundle(),-1));
    }
    @Test void detachedPreparationOwnsItsArraysAndDoesNotMutateExistingSidecarContract() throws Exception {
        var source=ClankFixtures.bundle();byte[] before=Rac1ClankSidecarCodec.encode(source);
        var first=Rac1ClankPreparedAssets.prepare(source,1);var second=Rac1ClankPreparedAssets.prepare(source,2);
        first.model(Role.CLANK).mesh().positions()[0]=999;
        first.item(Module.HELI).bodyPose().positions()[0]=888;
        assertEquals(0,second.model(Role.CLANK).mesh().positions()[0]);
        assertEquals(0,second.item(Module.HELI).bodyPose().positions()[0]);
        assertArrayEquals(before,Rac1ClankSidecarCodec.encode(source));
        assertEquals(1,Rac1ClankSidecarCodec.FORMAT);assertEquals(1,Rac1ClankSidecarCodec.IMPORTER);
    }
    @Test void allPreparationStagesCanRunOnWorkerWithoutMinecraftOrGpu() throws Exception {
        var source=ClankFixtures.bundle();var worker=Executors.newSingleThreadExecutor();
        try {
            long caller=Thread.currentThread().threadId();var threads=new ArrayList<Long>();
            var prepared=worker.submit(()->Rac1ClankPreparedAssets.prepare(source,1,
                    (stage,nanos)->threads.add(Thread.currentThread().threadId()))).get();
            assertEquals(3,threads.size());assertTrue(threads.stream().allMatch(t->t!=caller));
            assertNotNull(prepared.item(Module.THRUSTER));
        } finally {worker.shutdown();}
    }
}
