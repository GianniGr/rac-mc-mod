package com.github.giannigr.racmc.client.shop;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopVoicePredicateTest {
    private static final Rac1ShopVoicePredicate.Scaling IDENTITY=(value,selector) -> value;
    @Test void inclusiveLowerAndStrictUpperWindowMatchSourceBranches() {
        assertFalse(Rac1ShopVoicePredicate.reached(100,99,2,1.5f,IDENTITY));
        assertTrue(Rac1ShopVoicePredicate.reached(100,100,2,1.5f,IDENTITY));
        assertTrue(Rac1ShopVoicePredicate.reached(100,102,2,1.5f,IDENTITY));
        assertFalse(Rac1ShopVoicePredicate.reached(100,103,2,1.5f,IDENTITY));
        assertFalse(Rac1ShopVoicePredicate.reached(100,104,2,1.5f,IDENTITY));
        assertTrue(Rac1ShopVoicePredicate.reached(100,109,3,4,IDENTITY),"Crossed threshold within supplied source step");
        assertFalse(Rac1ShopVoicePredicate.reached(100,112,3,4,IDENTITY),"Upper endpoint excluded");
        assertFalse(Rac1ShopVoicePredicate.reached(100,100,0,1,IDENTITY));
        assertFalse(Rac1ShopVoicePredicate.reached(100,100,-1,1,IDENTITY));
    }
    @Test void helperCallOrderSelectorAndQuantizationRemainExplicitNotGuessed() {
        var arguments=new ArrayList<Float>();
        var selectors=new ArrayList<Integer>();
        assertTrue(Rac1ShopVoicePredicate.reached(100,102,2,1.5f,(value,selector) -> {
            arguments.add(value); selectors.add(selector); return value;
        }));
        assertEquals(java.util.List.of(2f,3f),arguments);
        assertEquals(java.util.List.of(4,4),selectors);
        assertTrue(Rac1ShopVoicePredicate.reached(100,100.125f,.1875f,1,IDENTITY));
        // Synthetic counterexample only: this is NOT a claim about the original scaler.
        assertFalse(Rac1ShopVoicePredicate.reached(100,100.125f,.1875f,1,
                (value,selector) -> (float)Math.floor(value*4)/4));
        assertThrows(NullPointerException.class,() -> Rac1ShopVoicePredicate.reached(100,100,1,1,null));
    }
    @Test void orderedNanComparisonsAndStatelessnessDoNotInventResetBlendOrOneShotPolicy() {
        assertFalse(Rac1ShopVoicePredicate.reached(Float.NaN,100,1,1,IDENTITY));
        assertFalse(Rac1ShopVoicePredicate.reached(100,Float.NaN,1,1,IDENTITY));
        assertFalse(Rac1ShopVoicePredicate.reached(100,100,Float.NaN,1,IDENTITY));
        assertFalse(Rac1ShopVoicePredicate.reached(100,100,1,1,(value,selector) -> Float.NaN));
        assertTrue(Rac1ShopVoicePredicate.reached(100,100,1,1,IDENTITY));
        assertTrue(Rac1ShopVoicePredicate.reached(100,100,1,1,IDENTITY),"Predicate is not itself a one-shot latch");
    }
}
