package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.item.weapon.bomb.BombGloveActionEvent.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BombGloveDryAudioTest {
    @Test void bindsOnlyTheAcceptedDryBlast() {
        assertSame(Rac1AudioEvent.BOMB_GLOVE_DRY_BURST, BombGloveDryAudio.cueFor(Kind.BLAST));
        assertNull(BombGloveDryAudio.cueFor(Kind.RELEASE));
        assertNull(BombGloveDryAudio.cueFor(Kind.WATER_ENTRY));
        assertNull(BombGloveDryAudio.cueFor(Kind.DEEP_BURST));
    }
}
