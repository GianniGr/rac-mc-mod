package com.github.giannigr.racmc.client.shop;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopVoiceHelpersTest {
    // Synthetic conversion contracts only; not a declaration that the uninspected callees do these.
    private static final Rac1ShopVoiceHelpers.IntegerConversion INTEGER=value -> value;
    @Test void normalInterpolationUsesSignedHeaderTimesSixteenthsNotSecondsOrKeyIndex() {
        var fields=new Rac1ShopVoiceHelpers.CursorFields(3,4,2,2,.25f,(short)1600,(short)1920);
        assertEquals(105,Rac1ShopVoiceHelpers.cursor(fields,INTEGER));
        assertEquals(100,Rac1ShopVoiceHelpers.cursor(
                new Rac1ShopVoiceHelpers.CursorFields(3,4,2,2,0,(short)1600,(short)1920),INTEGER));
        assertEquals(-1,Rac1ShopVoiceHelpers.cursor(
                new Rac1ShopVoiceHelpers.CursorFields(0,1,2,2,0,(short)0xfff0,(short)16),INTEGER));
    }
    @Test void sentinelWrapAndDifferentSequenceBranchesFollowActualByteTests() {
        assertEquals(120,Rac1ShopVoiceHelpers.cursor(
                new Rac1ShopVoiceHelpers.CursorFields(3,4,255,2,.5f,(short)1600,(short)1920),INTEGER));
        assertEquals(100.25f,Rac1ShopVoiceHelpers.cursor(
                new Rac1ShopVoiceHelpers.CursorFields(4,3,2,2,.25f,(short)1600,(short)1920),INTEGER));
        assertEquals(105,Rac1ShopVoiceHelpers.cursor(
                new Rac1ShopVoiceHelpers.CursorFields(4,3,1,2,.25f,(short)1600,(short)1920),INTEGER));
        assertEquals(105,Rac1ShopVoiceHelpers.cursor(
                new Rac1ShopVoiceHelpers.CursorFields(3,3,2,2,.25f,(short)1600,(short)1920),INTEGER));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopVoiceHelpers.CursorFields(256,0,0,0,0,(short)0,(short)0));
    }
    @Test void converterOrderAndFloatBiasAreExplicitWithNoConversionDefault() {
        var calls=new ArrayList<Integer>();
        assertEquals(105,Rac1ShopVoiceHelpers.cursor(
                new Rac1ShopVoiceHelpers.CursorFields(3,4,2,2,.25f,(short)1600,(short)1920),
                value -> { calls.add(value); return value; }));
        assertEquals(List.of(1600,1920),calls);
        var input=new ArrayList<Float>();
        assertEquals(.1234f,Rac1ShopVoiceHelpers.selector4(.1234f,4,value -> {
            input.add(value); return 1234;
        }));
        assertEquals(List.of((.1234f+1f/20000f)*10000f),input);
        assertEquals(.1234f,Rac1ShopVoiceHelpers.selector4(.12344f,4,value -> (int)value));
        assertEquals(.1235f,Rac1ShopVoiceHelpers.selector4(.12344f,4,Math::round),
                "Synthetic counterexample: unresolved conversion changes synchronization");
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopVoiceHelpers.selector4(1,3,value -> (int)value));
        assertThrows(NullPointerException.class,() -> Rac1ShopVoiceHelpers.selector4(1,4,null));
        assertThrows(NullPointerException.class,() -> Rac1ShopVoiceHelpers.cursor(
                new Rac1ShopVoiceHelpers.CursorFields(0,0,0,0,0,(short)0,(short)0),null));
    }
}
