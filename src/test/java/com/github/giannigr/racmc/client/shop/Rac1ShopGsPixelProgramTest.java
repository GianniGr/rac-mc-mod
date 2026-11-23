package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopGsPixelProgram.*;

class Rac1ShopGsPixelProgramTest {
    @Test void neutral128ModulateIsIdentityIncludingAlpha() {
        Rgba texture=new Rgba(200,101,7,128);
        assertEquals(texture,modulate(texture,new Rgba(128,128,128,128)));
        assertEquals(new Rgba(100,50,3,64),modulate(texture,new Rgba(64,64,64,64)));
        assertEquals(new Rgba(255,255,255,255),modulate(new Rgba(255,255,255,255),new Rgba(255,255,255,255)));
    }
    @Test void alphaFailureWritesBlendedRgbButPreservesAlphaAndDepth() {
        var destination=new Rgba(100,40,0,23);
        var result=fragment(new Rgba(200,80,64,64),destination,11,10,0,false);
        assertEquals(new Rgba(150,60,32,23),result.color());
        assertEquals(10,result.depth24());
        var pass=fragment(new Rgba(200,80,64,96),destination,11,10,0,false);
        assertEquals(new Rgba(175,70,48,96),pass.color());
        assertEquals(11,pass.depth24());
    }
    @Test void destinationAlphaIsSourceOnPassNotSourceOverAndFbaIsOff() {
        assertEquals(96,fragment(new Rgba(10,10,10,96),new Rgba(0,0,0,128),10,10,0,false).color().a());
        assertEquals(128,fragment(new Rgba(10,10,10,128),new Rgba(0,0,0,255),10,10,0,false).color().a());
        assertEquals(0,fragment(new Rgba(0,0,0,64),new Rgba(1,1,1,0),10,10,0,false).color().r());
        assertEquals(255,fragment(new Rgba(200,200,200,255),new Rgba(100,100,100,0),10,10,0,false).color().r());
    }
    @Test void depthFrameMasksAndZmskRemainIndependent() {
        var src=new Rgba(200,80,64,128); var dst=new Rgba(100,40,0,23);
        assertEquals(new Result(dst,10),fragment(src,dst,9,10,0,false));
        assertEquals(new Result(new Rgba(200,80,64,128),10),fragment(src,dst,10,10,0,true));
        assertEquals(new Rgba(100,80,64,23),fragment(src,dst,11,10,0xff0000ff,false).color());
        assertThrows(IllegalArgumentException.class,() -> fragment(src,dst,0x1000000,10,0,false));
        assertThrows(IllegalArgumentException.class,() -> new Rgba(256,0,0,0));
    }
    @Test void globallySplittingPassAndFailChangesOriginalPrimitiveOrdering() {
        var initial=new Rgba(20,20,20,7);
        var low=new Rgba(120,120,120,64); var high=new Rgba(50,50,50,128);
        var first=fragment(low,initial,11,10,0,false);
        var original=fragment(high,first.color(),10,first.depth24(),0,false);
        var reordered=fragment(low,fragment(high,initial,10,10,0,false).color(),11,10,0,false);
        assertEquals(50,original.color().r());
        assertEquals(85,reordered.color().r());
        assertNotEquals(original.color(),reordered.color());
    }
}
