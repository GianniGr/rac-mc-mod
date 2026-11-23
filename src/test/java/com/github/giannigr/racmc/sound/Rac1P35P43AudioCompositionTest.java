package com.github.giannigr.racmc.sound;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Rac1P35P43AudioCompositionTest {
    @TempDir Path temp;
    @Test void acceptedV4SidecarIsNeitherOverwrittenNorReusedByPhysicalP43Profile() throws Exception {
        String hash="a".repeat(40);
        var old=temp.resolve("sound-"+hash+"-v4.racs");
        byte[] sentinel={4,12,42}; Files.write(old,sentinel);
        var store=new Rac1SoundCache(temp);
        assertTrue(store.read(hash).isEmpty());
        store.write(hash,Rac1SoundPipelineTest.catalog());
        assertEquals(14,store.read(hash).orElseThrow().clips().size());
        assertArrayEquals(sentinel,Files.readAllBytes(old));
        assertTrue(Files.isRegularFile(temp.resolve("sound-"+hash+"-v5.racs")));
    }
    @Test void originalTwelveOrdinalBindingsAndP43AppendedIdentityRemainStable() {
        String[] accepted={"CRATE_BREAK","HEALTH_CRATE_BREAK","AMMO_CRATE_BREAK","TNT_BLAST","TNT_BEEP",
                "BOLT_ATTRACTION","HEXNUT_ATTRACTION","STARNUT_ATTRACTION","CIRCLENUT_ATTRACTION",
                "NANOTECH_FIRST_ARRIVAL","BLASTER_FIRE_LOOP","BLASTER_AMMO_PICKUP"};
        for (int i=0;i<accepted.length;i++) assertEquals(accepted[i],Rac1AudioEvent.values()[i].name());
        assertEquals(14,Rac1AudioEvent.BOMB_GLOVE_AMMO_PICKUP.ordinal());
        assertEquals(15,Rac1AudioEvent.BOMB_GLOVE_DRY_BURST.ordinal());
        assertFalse(Rac1AudioEvent.BOMB_GLOVE_AMMO_PICKUP.importsSample());
        assertFalse(Rac1AudioEvent.BOMB_GLOVE_DRY_BURST.importsSample());
    }
}
