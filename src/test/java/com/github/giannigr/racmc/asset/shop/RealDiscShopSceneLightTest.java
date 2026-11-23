package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.WadDecoder;
import java.nio.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Primary numerical/code pins; no original game execution, rendered pixels or scene acceptance. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopSceneLightTest {
    @Test void sourceActorAssignmentsFogAndRttStateMatchIndependentBothDiscProbe() throws Exception {
        var validator=new Rac1DiscValidator();
        try (var outcome=validator.inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported());
            var image=outcome.image(); boolean pal=image.revision()==Rac1DiscRevision.PAL_V2_00;
            String sha=validator.catalog().profiles().stream().filter(p -> p.revision()==image.revision())
                    .findFirst().orElseThrow().imageSha1();
            var toc=Rac1DiscTables.read(image);
            byte[] header=image.readBytes(toc.levelSector(1)*2048L,0x2434);
            long base=Integer.toUnsignedLong(word(header,8))*2048;
            byte[] container=image.readBytes(base,0x58);
            int off=word(container,0),size=word(container,4);
            assertTrue(off>=0 && size>0 && size<=4*1024*1024);
            byte[] overlay=image.readBytes(base+off,size);
            if (WadDecoder.isWad(overlay)) overlay=WadDecoder.decode(overlay,-1);
            for (int variant:new int[]{0x10,0x18}) {
                int sector=word(header,variant),sectors=word(header,variant+4);
                assertTrue(sector>0 && sectors>0 && sectors<=4096);
                byte[] gameplay=WadDecoder.decode(image.readBytes(sector*2048L,sectors*2048),-1);
                var source=Rac1ShopSceneLightSource.read(overlay,pal,gameplay,sha);
                assertEquals(3,source.vendorInstance()); assertEquals(0,source.vendorLightWord());
                assertArrayEquals(new int[]{41,41,41},source.vendorAmbient());
                assertArrayEquals(new float[]{-.42f,-.7f,-.577f,0},source.menuDirection());
                assertArrayEquals(new float[]{.9f,.9f,.6f,-.25f},source.menuColor());
                assertEquals(new Rac1ShopSceneLightSource.Fog(105,127,180,0,245760,255,102),source.worldFog());
                int lights=word(gameplay,4);
                assertEquals(5,word(gameplay,lights)); assertEquals(983,word(gameplay,word(gameplay,0x44)));
                assertEquals("98f55d7ff4557ffc832ea6af4cee78915d6bd152f128564940e9ffb8dd034a95",
                        digest(Arrays.copyOfRange(gameplay,lights+16,lights+16+5*64)));
            }
            // Independent Python source windows. These pin assignments/RTT call state, not ported code.
            pin(overlay,pal ? 0x2b0a78:0x2af7e8,0xbd0,pal
                    ? "39c4f043ea186af3264f0a2ea653456556512a3ab7b9c5aac69125ce658f3057"
                    : "52fc7e455a4c94d03325893aaf97a8a24835b635f70efc0c2385301317047c3e");
            pin(overlay,pal ? 0x222ef0:0x222fc0,0x300,pal
                    ? "6508ae51fbd3a8ddbb68c53053131d930c031689d2552fe63e72c2d8dfd86aee"
                    : "35dc8f4b4188126e07795f23da2073f010641c6527a32732ec532bef7f1008ff");
            pin(overlay,pal ? 0x219ea0:0x219ba0,0x130,pal
                    ? "3cca7d44b2840906edc6ad5fc2b3a34017f253111b667459ca7b158a01150973"
                    : "a2e21783bc431b626ae01dfe93bf35d5bc3ceb5d368253f53f1d3c5d14498dae");
            pin(overlay,pal ? 0x222428:0x2224f8,0x958,pal
                    ? "e750f1f4a05083a5f7b7b8b9152f2e8f37e8f128237d980e17338273fcf29f72"
                    : "e0e7a58ca9d899641919bace0fa862bbb941cb71146bd6e6a9db71785b62a357");
            pin(overlay,pal ? 0x21c190:0x21be90,0x188,pal
                    ? "2db882fcd60857f6284a05705da6696b7e45ca2786a1849c15dc0a4865a3d478"
                    : "d24b008b3c21e00be017d6b8f01f69de33ad4c712507dd1459291c6a881b305e");
            pin(overlay,pal ? 0x2b4e80:0x2b3cc8,0x358,pal
                    ? "caf3afbc2cd4b44020c9e2aaeedf81229bb52754aafa0e9c6c8ee32224c813ba"
                    : "c627aae52233ac5e22188e776320b80e49fe46816686839fe927363398a2f99a");
            var quad=ByteBuffer.wrap(Rac1HudImporter.overlayRange(overlay,pal ? 0x21c190:0x21be90,0x188))
                    .order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0x3402ffff,quad.getInt(0xf8));
            assertEquals(0x3442f000,quad.getInt(0x100));
            assertEquals(16,(quad.getInt(0xfc)>>>6)&31);
            assertEquals(24,(quad.getInt(0x104)>>>6)&31);
            assertEquals(0xfffff0,((0xffffL<<16|0xf000L)<<24)>>>32);
            System.out.printf("%s both gameplay variants: vendor3 ambient41/bank0; menu set14 key+backlight; RTT shared-Z/fog pins pass%n",image.revision());
        }
    }

    @Test void primaryResetStateAndActualVuPayloadsNotNobitsPlaceholdersMatch() throws Exception {
        try (var outcome=new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported());
            boolean pal=outcome.image().revision()==Rac1DiscRevision.PAL_V2_00;
            byte[] elf=outcome.image().readFile(pal ? "SCES_509.16":"SCUS_971.99",16*1024*1024);
            var reset=ByteBuffer.wrap(elfRange(elf,pal ? 0x13d0c0:0x13cfc0,0x130)).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0,reset.getLong(0x80)); assertEquals(0x4a,reset.getLong(0x88)); // FBA_1 off
            assertEquals(1,reset.getLong(0xc0)); assertEquals(0x1a,reset.getLong(0xc8)); // COLCLAMP on
            assertEquals(0,reset.getLong(0xa0)); assertEquals(0x49,reset.getLong(0xa8)); // PABE off
            assertEquals(0,reset.getLong(0xb0)); assertEquals(0x1b,reset.getLong(0xb8)); // dither off
            assertEquals(0,reset.getLong(0xd0)); assertEquals(0x22,reset.getLong(0xd8)); // scan mask off
            assertEquals(0x5360b,reset.getLong(0xe0));
            assertEquals("377c0b633ae5588ac053bd8fb4326dd3e3ab8d21075b3fa5e18d152260b1e066",
                    digest(elfRange(elf,0x100090,0x800)));
            assertEquals("796475560f277fbf5cb75f758d9a55ed86a33954fa9fc82be6bf5dea046448b2",
                    digest(elfRange(elf,0x100898,0x240)));
            assertEquals("2312074cf3abd5df2083e592d1cb5497dd735d78ec6b5a6ba8eef9cd635f8bb3",
                    digest(elfRange(elf,0x10faa8,0x800)));
            assertEquals("2d1c03d44d706588d1b7867e49a6f4d6686643c800810cb9f063f2be5ec55a9f",
                    digest(elfRange(elf,pal ? 0x2137e8:0x212998,0x274)));
            // Actual VU1 upper instructions: maxx.w against floor, mini.w against255, ftoi4.
            var vu=ByteBuffer.wrap(elfRange(elf,0x10faa8,0x800)).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0x10,vu.getInt(73*8+4)&63);
            assertEquals(24,(vu.getInt(73*8+4)>>>16)&31);
            assertEquals(0x2f,vu.getInt(90*8+4)&63);
            assertEquals(11,(vu.getInt(90*8+4)>>>16)&31);
            assertEquals(0x17d,vu.getInt(100*8+4)&2047);
        }
    }
    private static void pin(byte[] overlay,int pc,int size,String hash) throws Exception {
        assertEquals(hash,digest(Rac1HudImporter.overlayRange(overlay,pc,size)));
    }
    private static String digest(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
    private static byte[] elfRange(byte[] elf,int address,int size) {
        var h=ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x464c457f,h.getInt(0)); assertEquals(32,h.getShort(42)&65535);
        int table=h.getInt(28),count=h.getShort(44)&65535;
        assertTrue(count<=128 && table>=0 && (long)table+count*32<=elf.length);
        for (int i=0;i<count;i++) {
            int at=table+i*32;
            if (h.getInt(at)!=1) continue;
            int file=h.getInt(at+4),va=h.getInt(at+8),extent=h.getInt(at+16);
            long relative=(long)address-va;
            if (relative>=0 && relative+size<=extent) {
                assertTrue(file>=0 && (long)file+extent<=elf.length);
                return Arrays.copyOfRange(elf,file+(int)relative,file+(int)relative+size);
            }
        }
        throw new AssertionError("Unmapped primary ELF source");
    }
}
