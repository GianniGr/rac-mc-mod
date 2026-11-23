package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import com.github.giannigr.racmc.client.sound.Rac1PcmStream;
import com.github.giannigr.racmc.disc.Rac1DiscTables;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.WadDecoder;
import java.nio.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit selected-disc in-memory decode/stream check; never opens an audio device or writes PCM. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
@EnabledIfSystemProperty(named="racmc.shopVoiceProbe",matches="true")
class RealDiscShopVoiceTest {
    @Test void approvedMappedDispatcherAndSixEnglishBodiesUseExistingDecoderAndMemoryStream() throws Exception {
        try (var result=new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(result.isSupported());
            var image=result.image(); boolean pal=image.revision()==Rac1DiscRevision.PAL_V2_00;
            byte[] toc=Rac1DiscTables.read(image).bytes();
            var tables=Rac1DiscTables.of(toc);
            byte[] level=image.readBytes(tables.levelSector(1)*2048L,0x2434);
            long base=Integer.toUnsignedLong(ByteBuffer.wrap(level).order(ByteOrder.LITTLE_ENDIAN).getInt(8))*2048;
            var header=ByteBuffer.wrap(image.readBytes(base,0x58)).order(ByteOrder.LITTLE_ENDIAN);
            int offset=header.getInt(0),length=header.getInt(4);
            assertTrue(offset>=0 && length>0 && length<=4*1024*1024);
            byte[] overlay=image.readBytes(base+offset,length);
            if (WadDecoder.isWad(overlay)) overlay=WadDecoder.decode(overlay,-1);
            byte[] code=Rac1HudImporter.overlayRange(overlay,pal ? 0x27a978:0x279c08,208);
            int[] bodyBytes={0};
            var clips=Rac1ShopVoiceSource.read((at,size) -> {
                if (size!=48) { bodyBytes[0]+=size; assertTrue(bodyBytes[0]<=204880); }
                return image.readBytes(at,size);
            },toc,code,pal);
            assertEquals(204880,bodyBytes[0]);
            for (int i=0;i<clips.size();i++) {
                var clip=clips.get(i);
                assertEquals(Rac1ShopVoiceSourceTest.INDICES[i],clip.index());
                assertEquals(Rac1ShopVoiceSourceTest.LENGTHS[i]/16-1,clip.frames());
                assertEquals(44056,clip.rate());
                assertEquals(clip.frames()*56,clip.pcm().length);
                try (var stream=new Rac1PcmStream(clip.pcm(),clip.rate())) {
                    assertEquals(44056,stream.getFormat().getSampleRate());
                    assertEquals(16,stream.getFormat().getSampleSizeInBits());
                    assertEquals(1,stream.getFormat().getChannels());
                    int consumed=0;
                    while (true) {
                        var part=stream.read(1024);
                        if (!part.hasRemaining()) break;
                        consumed+=part.remaining();
                    }
                    assertEquals(clip.pcm().length,consumed);
                }
                System.out.println("English dialogue index="+clip.index()+" rate="+clip.rate()
                        +" decodedFrames="+clip.frames()+" pcmBytes="+clip.pcm().length+"; in memory, no playback/export");
            }
        }
    }
}
