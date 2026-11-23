package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.iso.*;
import java.nio.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Primary original-disc instruction constants, not executing game code or asserting runtime pixels. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopMetalProgramTest {
    @Test void primaryMetalSetupPackingAndNormalTableMatchIndependentBothDiscProbe() throws Exception {
        try(var outcome=new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported(),outcome.result().message());
            boolean pal=outcome.result().revision()==Rac1DiscRevision.PAL_V2_00;
            byte[] elf=outcome.image().readFile(pal ? "SCES_509.16" : "SCUS_971.99",16*1024*1024);
            int start=pal ? 0x1efa04 : 0x1ef65c;
            byte[] driver=range(elf,start,0x2e0);
            var code=ByteBuffer.wrap(driver).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0x3c013e80,code.getInt(0x48)); // load0.25 for light colors
            assertEquals(0x3c034780,code.getInt(0x4c)); //65536 float bias
            assertEquals(0x34620070,code.getInt(0x54)); // RGB0x70
            assertEquals(0x34630080,code.getInt(0x5c)); // alpha0x80
            assertEquals(0x9029000b,code.getInt(0x1a4)); // count byte11
            assertEquals(0x200b0121,code.getInt(0x214)); // two direct matrices
            assertEquals(0x200b0111,code.getInt(0x244)); // three direct matrices
            assertEquals(0x200b012d,code.getInt(0x274)); // single matrix
            assertEquals(pal ? "4f079c3b12a817fc495eb7be6f2b1fd756f9d54e3f0b3962b7da816b0f42e709"
                            : "491d65ab178d7f2b5f6ecfa0b896d11efbd08fcbbe1ea289459a573d7455fb9e",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(driver)));
            var trig=ByteBuffer.wrap(range(elf,pal ? 0x165600 : 0x165500,256*8)).order(ByteOrder.LITTLE_ENDIAN);
            for(int angle=0;angle<256;angle++) {
                assertEquals(Math.cos(angle*Math.PI/128),trig.getFloat(),4e-7);
                assertEquals(Math.sin(angle*Math.PI/128),trig.getFloat(),4e-7);
            }
            System.out.printf("%s primary metal driver=%x, original RGB112/quarter-light/alpha128 and direct count/entries verified%n",
                    outcome.result().revision(),start);
        }
    }
    private byte[] range(byte[] elf,int address,int length) {
        var header=ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x464c457f,header.getInt(0)); assertEquals(1,elf[4]); assertEquals(1,elf[5]);
        int table=header.getInt(28), entry=header.getShort(42)&65535, count=header.getShort(44)&65535;
        assertEquals(32,entry); assertTrue(count<=128);
        for(int i=0;i<count;i++) {
            int at=table+i*entry; assertTrue(at>=0 && at+32<=elf.length);
            if(header.getInt(at)!=1) continue;
            int file=header.getInt(at+4), virtual=header.getInt(at+8), size=header.getInt(at+16);
            long relative=(long)address-virtual;
            if(relative>=0 && relative+length<=size) {
                assertTrue(file>=0 && (long)file+size<=elf.length);
                return Arrays.copyOfRange(elf,file+(int)relative,file+(int)relative+length);
            }
        }
        throw new AssertionError("Unmapped bounded original instruction range");
    }
}
