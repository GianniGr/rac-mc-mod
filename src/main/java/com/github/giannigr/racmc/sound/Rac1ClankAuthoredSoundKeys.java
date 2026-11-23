package com.github.giannigr.racmc.sound;

import java.util.OptionalInt;

/**
 * Source-backed hero KEY-INDEX interval: index*16+trunc(t*16), not stored key-header timestamps.
 * Does not manufacture tables or map a source animation clock onto Minecraft state age.
 */
public final class Rac1ClankAuthoredSoundKeys {
    private Rac1ClankAuthoredSoundKeys() {}
    public static OptionalInt first(int[] authoredWords,int before16,int after16,boolean blendingBeforeAdvance) {
        if(authoredWords.length>1024)throw new IllegalArgumentException("Unbounded sound trigger list");
        if(blendingBeforeAdvance||after16<=before16)return OptionalInt.empty();
        for(int word:authoredWords) {
            int time=word>>16;
            if(before16<time&&time<=after16)return OptionalInt.of(word&0xffff);
        }
        return OptionalInt.empty();
    }
}
