package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.sound.Rac1AudioCatalog;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopDeniedSoundTest {
    private static Rac1AudioCatalog.Clip clip(int id,boolean repeat,int loopStart) {
        return new Rac1AudioCatalog.Clip(new byte[56],22050,new byte[32],new byte[12],new byte[40],
                "0".repeat(64),"0".repeat(64),id,loopStart,28,repeat);
    }
    @Test void privateOneShotCancellationDoesNotCloseAnotherScenesStream() throws Exception {
        var a=new Rac1ShopDeniedSound(clip(70,false,-1));
        var b=new Rac1ShopDeniedSound(clip(70,false,-1));
        assertFalse(a.isLooping()); assertTrue(a.isRelative());
        var first=a.getStream(null,null,false).join();
        var second=b.getStream(null,null,false).join();
        a.close(); a.close();
        assertTrue(a.isStopped()); assertFalse(b.isStopped());
        assertEquals(0,first.read(128).remaining());
        assertEquals(56,second.read(128).remaining());
        b.close();
    }
    @Test void wrongCueAndLoopingFallbackAreRefused() {
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopDeniedSound(clip(68,false,-1)));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopDeniedSound(clip(70,true,0)));
    }
}
