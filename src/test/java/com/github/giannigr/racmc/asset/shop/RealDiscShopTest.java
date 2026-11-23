package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.cache.shop.Rac1ShopCacheCodec;
import com.github.giannigr.racmc.client.shop.Rac1ShopLayout;
import com.github.giannigr.racmc.client.shop.Rac1ShopGlyphLayout;
import com.github.giannigr.racmc.client.shop.Rac1ShopTickerLayout;
import com.github.giannigr.racmc.client.shop.Rac1ShopTextWindow;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only validated-disc import. Converted bytes remain in memory, never asset dumps. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscShopTest {
    @Test void importsExternalSalesmanRigidAmmoOriginalFontsIconsAndSelectedText() throws Exception {
        try (var outcome = new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported(), outcome.result().message());
            var level = Rac1LevelAssets.load(outcome.image(), 1);
            // Full supported identity validation above; this key is only a local in-memory test identity.
            String sha1 = "a".repeat(40);
            var popup = Rac1AssetExtractor.readAnimatedAsset(level, 1137);
            System.out.printf("popup numeric diagnostic: vertices=%d triangles=%d textures=%d materials=%s%n",
                    popup.mesh().vertexCount(), popup.mesh().triangleCount(), popup.textures().length,
                    java.util.Arrays.toString(java.util.Arrays.stream(popup.mesh().materials()).distinct().toArray()));
            var models = Rac1ShopModelImporter.read(outcome.image(), level, sha1);
            assertEquals(92, models.salesman().skeleton().jointCount());
            assertEquals(10, models.salesman().animations().length);
            assertTrue(models.ammo().mesh().triangleCount() > 0);
            // Resident-less salesman must never acquire hero animations via the generic method.
            assertEquals(0, Rac1AssetExtractor.readAnimatedAsset(level, 12).animations().length);
            for (var animation : models.salesman().animations()) {
                assertFalse(animation.name().startsWith("ratchet_"));
                for (float t : new float[]{0, animation.times()[animation.keyCount() - 1] / 2}) {
                    float[] pose = Rac1AnimationEvaluator.evaluatePose(models.salesman().skeleton(), animation, t);
                    for (float v : pose) assertTrue(Float.isFinite(v));
                }
            }
            var graphics = Rac1ShopImporter.read(outcome.image(), level, sha1, 0);
            assertTrue(graphics.missing2d().isEmpty());
            assertTrue(graphics.missingSourceTables().isEmpty());
            var tables = graphics.sceneTables();
            assertEquals(24, tables.tickerIds().length);
            assertEquals(20321, tables.tickerIds()[0]); assertEquals(20332, tables.tickerIds()[23]);
            assertEquals(0, tables.led().cell('A'));
            assertEquals(-1, tables.led().cell(' ')); assertEquals(9, tables.led().advance(' '));
            assertArrayEquals(new float[]{0,-1.57f,-3,0,-2.4f,-1.4f,-.25f,.12f}, tables.blasterPanel(), .00001f);
            assertEquals(1006, tables.ammoClass());
            assertEquals(-.2f, tables.blasterHolograms()[6], .00001f);
            assertEquals((float)Math.PI / 6, tables.blasterHolograms()[12], .00001f);
            assertArrayEquals(new int[]{3,2,5,4},tables.monitorTargets());
            assertEquals(5, graphics.graphics().sprites().get("blaster_icons").size());
            assertEquals(64, graphics.graphics().sprites().get("led_atlas").getFirst().width());
            assertEquals(256, graphics.graphics().fonts().get("small").atlas().width());
            var body = Rac1AssetExtractor.readAnimatedAsset(level, 11);
            var bodyReader = MobyClassReader.of(level.classBlob(level.coreIndex().mobyClass(11).offset()));
            var jointLists = bodyReader.readJointLists();
            System.out.printf("shop vendor numeric joint lists: count=%d endpoints=%s targets20..23=%s%n",
                    jointLists.size(),
                    java.util.Arrays.toString(java.util.stream.IntStream.range(0,Math.min(24,jointLists.size()))
                            .map(i -> { byte[] c = jointLists.get(i).chain(); return c.length == 0 ? -1 : c[c.length - 1] & 255; }).toArray()),
                    jointLists.size() < 24 ? "missing" : java.util.stream.IntStream.range(20,24)
                            .mapToObj(i -> java.util.Arrays.toString(jointLists.get(i).target())).toList());
            var open = java.util.Arrays.stream(body.animations()).filter(a -> a.name().equals("sequence_3")).findFirst().orElseThrow();
            var pose = Rac1AnimationEvaluator.evaluateChain(body.skeleton(), open, 0);
            for (int screen = 0; screen < 6; screen++) {
                var monitor = Rac1ShopLayout.monitorVendor(body,pose,screen,tables.margins());
                assertNotEquals(monitor.corner(), monitor.opposite());
            }
            var poseAdapter = new Rac1ShopKioskPose(body);
            assertArrayEquals(Rac1VendorPose.closed(new Rac1VendorAsset(body,
                    Rac1AssetExtractor.readAnimatedAsset(level,1143))).positions(),
                    poseAdapter.stage(Rac1ShopKioskPose.Stage.CLOSED,0,java.util.List.of()).positions(),1e-5f);
            var fold = java.util.Arrays.stream(body.animations()).filter(a -> a.name().equals("sequence_4")).findFirst().orElseThrow();
            int[] mapping = fold.sourceTiming().sampledSourceKeys();
            int frame9 = java.util.stream.IntStream.range(0,mapping.length).filter(i -> mapping[i] == 9).findFirst().orElseThrow();
            // Exact original frame9, not a time sample: the shared evaluator normalises
            // interior-key interpolation, whereas an original t=0 key copies raw1.15 quaternions.
            var endpoint = Rac1AnimationEvaluator.evaluate(body.skeleton(),exactKey(fold,frame9,body.skeleton().jointCount()),0,1);
            float[] expectedPositions = new float[body.mesh().positions().length], expectedNormals = new float[expectedPositions.length];
            Rac1AnimationEvaluator.pose(body.mesh(),endpoint,expectedPositions,expectedNormals);
            assertArrayEquals(expectedPositions,poseAdapter.stage(Rac1ShopKioskPose.Stage.REVERSE_FOLD,0,java.util.List.of()).positions(),1e-5f);
            for (int language : new int[]{0, 2, 3, 4, 5}) {
                var localized = language == 0 ? graphics : Rac1ShopImporter.read(outcome.image(), level, sha1, language);
                for (int id : localized.text().ids()) {
                    // Validate source glyph extents as well as the bounded window formatter.
                    byte[] string = localized.text().get(id);
                    Rac1ShopTextWindow.layout(localized.graphics().fonts().get("small"),string,
                            new Rac1ShopTextWindow.Window(0,0,128,128,64,64,16,true,true));
                    int start = 0;
                    for (int end = 0; end <= string.length; end++) {
                        if (end == string.length || string[end] == 1) {
                            Rac1ShopGlyphLayout.line(localized.graphics().fonts().get("small"),
                                    java.util.Arrays.copyOfRange(string, start, end), 0x80808080, true);
                            start = end + 1;
                        }
                    }
                }
                for (int id : localized.sceneTables().tickerIds())
                    Rac1ShopTickerLayout.line(localized.sceneTables().led(), localized.text().get(id), 0, true);
            }
            byte[] sidecar = Rac1ShopCacheCodec.write(graphics);
            var decoded = Rac1ShopCacheCodec.read(sidecar, sha1, 0);
            assertTrue(decoded.missing2d().isEmpty());
            assertTrue(decoded.missingImportedModels().isEmpty());
            assertTrue(decoded.missingMaterialMetadata().isEmpty());
            for (int id : Rac1ShopAssets.MATERIAL_CLASSES) {
                var reader = MobyClassReader.of(level.classBlob(level.coreIndex().mobyClass(id).offset()));
                var source = reader.readMaterialMetadata();
                var persisted = decoded.materialMetadata().get(id);
                assertEquals(source.glowRgba(),persisted.glowRgba());
                assertEquals(source.mode44(),persisted.mode44()); assertEquals(source.mode47(),persisted.mode47());
                assertEquals(source.packets().size(),persisted.packets().size());
                for (int i = 0; i < source.packets().size(); i++) {
                    var original = source.packets().get(i); var packet = persisted.packets().get(i);
                    assertEquals(original.packetIndex(),packet.packetIndex()); assertEquals(original.metal(),packet.metal());
                    assertEquals(original.vertexCount(),packet.vertexCount());
                    assertArrayEquals(original.normalMultipliers(),packet.normalMultipliers());
                    assertEquals(original.materials(),packet.materials());
                }
                assertEquals(reader.readMesh().vertexCount(),decoded.normalMaterialVertexCount(id));
            }
            assertArrayEquals(models.salesman().animations()[4].rotations(), decoded.models().salesman().animations()[4].rotations());
            assertArrayEquals(models.ammo().mesh().positions(), decoded.models().ammo().mesh().positions());
            assertEquals(-1, decoded.models().popup().mesh().materials()[0]);
            System.out.printf("%s original shop: salesman=%d clips, ammo=%d triangles, selectedStrings=%d, sidecarBytes=%d, materialSectionBytes=%d%n",
                    outcome.result().revision(), models.salesman().animations().length,
                    models.ammo().mesh().triangleCount(), graphics.text().ids().size(), sidecar.length,
                    com.github.giannigr.racmc.cache.shop.Rac1ShopMaterialCodec.write(decoded.materialMetadata()).length);
        }
    }

    private static Rac1Animation exactKey(Rac1Animation clip, int key, int joints) {
        int[] translations = java.util.stream.IntStream.range(0,clip.translationKeys().length)
                .filter(i -> clip.translationKeys()[i] == key).toArray();
        int[] scales = java.util.stream.IntStream.range(0,clip.scaleKeys().length)
                .filter(i -> clip.scaleKeys()[i] == key).toArray();
        boolean[] inherited = new boolean[scales.length];
        for (int i = 0; i < scales.length; i++) inherited[i]=clip.scaleInherited()[scales[i]];
        return new Rac1Animation("independent_source_key",new float[]{0},false,
                java.util.Arrays.copyOfRange(clip.rotations(),key*joints*4,(key+1)*joints*4),
                new int[translations.length],java.util.Arrays.stream(translations).map(i -> clip.translationJoints()[i]).toArray(),
                vectors(clip.translationValues(),translations),
                new int[scales.length],java.util.Arrays.stream(scales).map(i -> clip.scaleJoints()[i]).toArray(),
                vectors(clip.scaleValues(),scales),inherited);
    }
    private static float[] vectors(float[] values, int[] selected) {
        float[] result = new float[selected.length*3];
        for (int i = 0; i < selected.length; i++) System.arraycopy(values,selected[i]*3,result,i*3,3);
        return result;
    }
}
