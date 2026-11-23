package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.*;

/** READ-ONLY source packet facts; no shared reader/schema/mesh/renderer mutation. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscShopMaterialTest {
    @Test void verifiesOriginalNormalMultipliersAndSeparateMetalPacketBounds() throws Exception {
        try (var outcome = new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported(),outcome.result().message());
            var level = Rac1LevelAssets.load(outcome.image(),1);
            for (int classId : new int[]{11,12,13,1137,1143}) {
                byte[] blob = level.classBlob(level.coreIndex().mobyClass(classId).offset());
                int table = word(blob,0), high = blob[4]&255, low = blob[5]&255, metal = blob[6]&255;
                int metalBegin = blob[7]&255, joints = blob[8]&255;
                range(blob,table,(high+low+metal)*16);
                var reader = MobyClassReader.of(blob);
                String priorMesh = switch (classId) {
                    case 11 -> "9091297a1a1921f2f7fc84c233e51c4cecec48a7aa5b8cc6342899a10b5ae150";
                    case 12 -> "519dae94d93275fbbbe7fb322667cb12b907260ccf152e0f91efb863705dc58a";
                    case 13 -> "0d67f9915ec759440bf150df281ffcc4f7d0ff0db8ef6f7f564c25781a683e2e";
                    case 1137 -> "b208067c71e86b202bd35ad771a524c470428aa1d361003f67238abe412551fc";
                    case 1143 -> outcome.result().revision() == com.github.giannigr.racmc.iso.Rac1DiscRevision.PAL_V2_00
                            ? "fdc999124739bb8a15ec1acc848ea7a36a5d5d6fb2a81af71c9a97e438ceaedd"
                            : "7d0c58925e552654b0b63b5ee62822252bb74ed893a805e2dda3e5a285fc0036";
                    default -> throw new AssertionError();
                };
                // Independently generated with isolated, unmodified 90bc536 reader on EACH disc.
                assertEquals(priorMesh,meshDigest(reader.readMesh()));
                reader.readMetalPackets();
                assertEquals(priorMesh,meshDigest(reader.readMesh()),"Raw metal access must not reinterpret any old decoded array");
                var decoded = reader.readMaterialMetadata();
                assertEquals(priorMesh,meshDigest(reader.readMesh()));
                assertEquals(word(blob,0x40),decoded.glowRgba());
                assertEquals((blob[0x44]&255)|((blob[0x45]&255)<<8),decoded.mode44());
                assertEquals(blob[0x47]&255,decoded.mode47());
                assertEquals(high+metal,decoded.packets().size());
                int vertices = 0, multiplierBytes = 0, nonNeutral = 0, paddingNonZero = 0;
                Set<String> normalStates = new TreeSet<>(), metalStates = new TreeSet<>();
                for (int p = 0; p < high; p++) {
                    int entry = table+p*16, header = word(blob,entry+8), count = blob[entry+15]&255;
                    int extent = (blob[entry+12]&255)*16, multiplier = word(blob,header+28);
                    assertEquals(count,word(blob,header+20));
                    assertTrue(multiplier >= 32 && multiplier + (long)count*4 <= extent);
                    range(blob,header,extent);
                    var packet = decoded.packets().get(p);
                    assertEquals(p,packet.packetIndex()); assertFalse(packet.metal()); assertEquals(count,packet.vertexCount());
                    assertArrayEquals(Arrays.copyOfRange(blob,header+multiplier,header+multiplier+count*4),packet.normalMultipliers());
                    vertices += count; multiplierBytes += count*4;
                    for (int i = 0; i < count*4; i++) if ((blob[header+multiplier+i]&255) != 128) nonNeutral++;
                    for (int i = multiplier+count*4; i < extent; i++) if (blob[header+i] != 0) paddingNonZero++;
                    states(blob,entry,false,normalStates,packet.materials());
                }
                assertEquals(MobyClassReader.of(blob).readMesh().vertexCount(),vertices);
                assertEquals(0,nonNeutral,"Required shop source is neutral128, not a fabricated per-vertex color atlas");
                assertEquals(0,paddingNonZero);
                int metalVertices = 0, metalMaxCount = 0;
                for (int p = 0; p < metal; p++) {
                    int entry = table+(metalBegin+p)*16, header = word(blob,entry+8);
                    int count = word(blob,header), transfer = blob[entry+15]&255;
                    assertEquals(transfer,count);
                    assertTrue(count >= 0 && count <= 255); range(blob,header+16,count*16);
                    var packet = decoded.packets().get(high+p);
                    assertEquals(metalBegin+p,packet.packetIndex()); assertTrue(packet.metal()); assertEquals(count,packet.vertexCount());
                    assertEquals(0,packet.normalMultipliers().length);
                    for (int v = 0; v < count; v++) {
                        int at = header+16+v*16, influences = blob[at+11]&255;
                        assertTrue(influences <= 3);
                        metalMaxCount = Math.max(metalMaxCount,influences);
                        int used = Math.max(1,influences), weight = 0;
                        for (int k = 0; k < used; k++) {
                            assertTrue((blob[at+8+k]&255) < joints);
                            weight += blob[at+12+k]&255;
                        }
                        if (influences >= 2) assertEquals(256,weight);
                    }
                    metalVertices += count;
                    states(blob,entry,true,metalStates,packet.materials());
                }
                System.out.printf("%s class%d packet metadata: high=%d normalVertices=%d multiplierBytes=%d nonNeutral=%d paddingNonZero=%d "
                                +"normalStates=%s metal=%d metalVertices=%d maxMetalInfluences=%d metalStates=%s glow=%08x modes=%d/%d%n",
                        outcome.result().revision(),classId,high,vertices,multiplierBytes,nonNeutral,paddingNonZero,
                        normalStates,metal,metalVertices,metalMaxCount,metalStates,word(blob,0x40),
                        (blob[0x44]&255)|((blob[0x45]&255)<<8),blob[0x47]&255);
            }
        }
    }
    private static String meshDigest(MobyClassReader.Mesh mesh) throws Exception {
        var hash = java.security.MessageDigest.getInstance("SHA-256");
        for (float[] values : new float[][]{mesh.positions(),mesh.normals(),mesh.uvs(),mesh.weights()}) {
            var bytes = ByteBuffer.allocate(4+values.length*4).order(ByteOrder.LITTLE_ENDIAN);
            bytes.putInt(values.length); for (float v : values) bytes.putInt(Float.floatToRawIntBits(v));
            hash.update(bytes.array());
        }
        for (int[] values : new int[][]{mesh.indices(),mesh.materials(),mesh.joints()}) {
            var bytes = ByteBuffer.allocate(4+values.length*4).order(ByteOrder.LITTLE_ENDIAN);
            bytes.putInt(values.length); for (int v : values) bytes.putInt(v);
            hash.update(bytes.array());
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void states(byte[] blob, int entry, boolean metal, Set<String> states,
                               List<MobyClassReader.PacketMaterial> actual) throws Exception {
        int at = word(blob,entry), size = (blob[entry+4]&255)|((blob[entry+5]&255)<<8);
        range(blob,at,size*16);
        var unpacks = VifCommandList.read(Arrays.copyOfRange(blob,at,at+size*16)).unpacks();
        int materialIndex = metal ? 1 : 2;
        if (unpacks.size() <= materialIndex) { assertTrue(actual.isEmpty()); return; }
        var material = unpacks.get(materialIndex);
        assertEquals(3,material.components()); assertEquals(0,material.width());
        assertEquals(0,material.count()%4);
        assertEquals(material.count()/4,actual.size());
        var bytes = ByteBuffer.wrap(material.data()).order(ByteOrder.LITTLE_ENDIAN);
        for (int block = 0; block < material.count()/4; block++) {
            int base = block*64, texture = bytes.getInt(base+32);
            long tex1 = bytes.getLong(base), clamp = bytes.getLong(base+16);
            assertEquals(texture,actual.get(block).textureSelector());
            assertEquals(tex1,actual.get(block).tex1()); assertEquals(clamp,actual.get(block).clamp());
            if (metal) assertTrue(texture == -2 || texture == -3); else assertTrue(texture >= -1);
            states.add("slot="+texture+",mag="+((tex1>>>5)&1)+",min="+((tex1>>>6)&7)
                    +",wrap="+(clamp&3)+"/"+((clamp>>>2)&3));
        }
    }
}
