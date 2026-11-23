package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Rac1ClankAuthoredSoundKeysTest {
    @Test void firstAuthoredWordInIntervalWinsNotChronologicalSortOrInputFrame() {
        int[] words={(160<<16)|7,(144<<16)|9,(160<<16)|11};
        assertEquals(7,Rac1ClankAuthoredSoundKeys.first(words,128,160,false).orElseThrow());
        assertTrue(Rac1ClankAuthoredSoundKeys.first(words,160,176,false).isEmpty());
        assertEquals(9,Rac1ClankAuthoredSoundKeys.first(words,128,159,false).orElseThrow());
    }
    @Test void blendWrapHoldAndMissingTablesNeverInventBindings() {
        int[] words={(16<<16)|5};
        assertTrue(Rac1ClankAuthoredSoundKeys.first(words,0,20,true).isEmpty());
        assertTrue(Rac1ClankAuthoredSoundKeys.first(words,20,0,false).isEmpty());
        assertTrue(Rac1ClankAuthoredSoundKeys.first(words,16,16,false).isEmpty());
        assertTrue(Rac1ClankAuthoredSoundKeys.first(new int[0],0,10000,false).isEmpty());
    }
    @Test void timeIsSignedLocalIdUnsignedAndTableReadIsBounded() {
        assertEquals(65535,Rac1ClankAuthoredSoundKeys.first(new int[]{(-1<<16)|65535},-2,0,false).orElseThrow());
        assertThrows(IllegalArgumentException.class,()->Rac1ClankAuthoredSoundKeys.first(new int[1025],0,10,false));
    }
}
