package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.shop.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopPresenterTest {
    @Test void productionSessionSurvivesBothDirectionsAndDoesNotPermanentlyCloseVoice() throws Exception {
        var driver=new Rac1ShopVoicePlaybackTest.Driver();
        var live=new AtomicBoolean(true);
        try (var session=new Rac1ShopPresenterSession(live::get,Rac1ShopVoiceSound.backend(driver))) {
            var p=session.attach(animations(),data(),n -> 0);
            session.tick(30,true);
            for (int i=0;i<4;i++) {
                session.select(false);
                session.tick(120,true); session.tick(0,true);
                assertNotEquals(Rac1ShopVoicePlayback.State.CLOSED,session.voice().state());
                float before=p.frame().seconds(); session.tick(3,true);
                assertNotEquals(before,p.frame().seconds(),"Real adapter clock must continue after navigation");
                session.select(true); session.tick(3,true);
                assertNotEquals(Rac1ShopVoicePlayback.State.CLOSED,session.voice().state());
            }
            session.tick(600,true);
            assertFalse(driver.submitted.isEmpty(),"New valid voice epochs still work after returning");
            live.set(false); session.tick(3,true);
            assertEquals(Rac1ShopVoicePlayback.State.CLOSED,session.voice().state());
        }
    }
    @Test void nonBlasterSelectionKeepsIndependentIdleClockWithoutNewSpeech() throws Exception {
        var driver=new Rac1ShopVoicePlaybackTest.Driver();
        var voice=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
        try (var p=new Rac1ShopPresenter(animations(),data(),voice,n -> 0)) {
            p.tick(120,true,false); p.tick(0,true,false);
            assertEquals(2,p.frame().sequence());
            float before=p.frame().seconds();
            p.tick(3,true,false);
            assertTrue(p.frame().seconds()>before);
            p.tick(600,true,false);
            assertEquals(2,p.frame().sequence());
            assertTrue(driver.submitted.isEmpty());
            p.tick(3,true,true);
            assertEquals(5,p.frame().sequence(),"Returning permits valid new idle speech, not opening replay");
        }
    }
    static Rac1Animation animation(int sequence) {
        return new Rac1Animation("sequence_"+sequence,new float[]{0,1,2},false,new float[12],
                new int[0],new int[0],new float[0],new int[0],new int[0],new float[0],new boolean[0],
                new Rac1SourceTiming(0,new int[]{0,1600,4800},new int[3],new float[]{0,1,2},new int[]{0,1,2}));
    }
    static Rac1Animation[] animations() {
        return java.util.stream.IntStream.of(2,4,5,6,7,8,9).mapToObj(Rac1ShopPresenterTest::animation).toArray(Rac1Animation[]::new);
    }
    static Rac1ShopVoiceData data() throws Exception {
        var b=ByteBuffer.allocate(52).order(ByteOrder.LITTLE_ENDIAN);
        for (int v:new int[]{0x2aeec8,0x2af228,0x2aef68,0x2aefd0,0x2af010,0x2af01c,0x2af028,
                0x2af228,0x2af228,0x2af228,0x2af0b4,0x2af1b0,0x2af138}) b.putInt(v);
        var t=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        for (int v:new int[]{100,100,200,100,44,100}) t.putInt(v);
        var clips=java.util.stream.IntStream.of(0,6,12,18,24,30)
                .mapToObj(i -> new Rac1ShopVoiceSource.Clip(i,44056,1,new byte[56])).toList();
        return new Rac1ShopVoiceData(clips,Rac1ShopVoiceTiming.read(b.array(),t.array(),false),null);
    }
    @Test void decodedHeaderClockIsNotThresholdDividedBySixtyAndRejectsUnsupportedMetadata() {
        var clock=new Rac1ShopPresenter.Timeline(animation(4).sourceTiming());
        assertEquals(100,clock.cursor(1)); assertEquals(200,clock.cursor(1.5f)); assertEquals(300,clock.cursor(2));
        assertEquals(300,clock.cursor(20)); assertEquals(0,clock.cursor(0));
        assertThrows(IllegalArgumentException.class,() -> clock.cursor(Float.NaN));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopPresenter.Timeline(
                new Rac1SourceTiming(0,new int[]{0,40000},new int[2],new float[]{0,1},new int[]{0,1})));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopPresenter.Timeline(
                new Rac1SourceTiming(0,new int[]{16,0},new int[2],new float[]{0,1},new int[]{0,1})));
    }
    @Test void crossingIsOneAttemptPerEpochAndAudioDrainsBeforeNextLine() throws Exception {
        var driver=new Rac1ShopVoicePlaybackTest.Driver();
        var voice=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
        try (var p=new Rac1ShopPresenter(animations(),data(),voice,n -> 0)) {
            assertEquals(4,p.frame().sequence());
            p.tick(30,true); assertTrue(driver.submitted.isEmpty());
            p.tick(30,true); assertEquals(1,driver.submitted.size()); assertEquals(10000,p.frame().streamId());
            p.tick(120,true); assertEquals(4,p.frame().sequence(),"Hold end pose while buffered sound drains");
            p.tick(0,true); assertEquals(1,driver.submitted.size());
            driver.active.clear(); p.tick(0,true); assertEquals(2,p.frame().sequence());
            p.tick(600,true); assertEquals(5,p.frame().sequence()); assertEquals(10006,p.frame().streamId());
            assertEquals(2,driver.submitted.size(),"Skipped updates still cross the idle threshold once");
        }
        assertTrue(driver.active.isEmpty());
    }
    @Test void sixOriginalPairsAreSelectedAndAcceptedReceiptsCoalesceWithoutOverlap() throws Exception {
        for (int variant:new int[]{0,1}) {
            var driver=new Rac1ShopVoicePlaybackTest.Driver();
            var voice=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
            try (var p=new Rac1ShopPresenter(animations(),data(),voice,n -> variant)) {
                p.tick(120,true);
                assertEquals(4+variant*3,p.frame().sequence()); assertEquals(10000+variant*18,p.frame().streamId());
                p.purchaseAccepted(); p.purchaseAccepted(); p.tick(30,true);
                assertEquals(1,driver.submitted.size(),"Receipt must not overlap current speech");
                driver.active.clear(); p.tick(0,true); p.tick(120,true);
                assertEquals(6+variant*3,p.frame().sequence()); assertEquals(10012+variant*18,p.frame().streamId());
                assertEquals(2,driver.submitted.size(),"Bounded pending receipt coalesces audio only");
                driver.active.clear(); p.tick(0,true); p.tick(600,true);
                assertEquals(5+variant*3,p.frame().sequence()); assertEquals(10006+variant*18,p.frame().streamId());
                assertEquals(3,driver.submitted.size());
            }
        }
    }
    @Test void focusWorldCloseAndReentryCannotReplayStaleSpeechOrStopAnotherViewer() throws Exception {
        var driver=new Rac1ShopVoicePlaybackTest.Driver(); var valid=new AtomicBoolean(true);
        var va=new Rac1ShopVoicePlayback(valid::get,Rac1ShopVoiceSound.backend(driver));
        var vb=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
        try (var a=new Rac1ShopPresenter(animations(),data(),va,n -> 0);
             var b=new Rac1ShopPresenter(animations(),data(),vb,n -> 1)) {
            a.tick(60,true); b.tick(60,true); assertEquals(2,driver.active.size());
            a.purchaseAccepted(); a.tick(3,false); assertEquals(1,driver.active.size());
            a.tick(3,true); assertEquals(2,driver.submitted.size(),"No greeting/receipt replay on refocus");
            valid.set(false); a.tick(3,true); a.tick(600,true); assertEquals(1,driver.active.size());
            assertEquals(Rac1ShopVoicePlayback.State.CLOSED,va.state());
        }
        assertTrue(driver.active.isEmpty());
        var fresh=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
        try (var p=new Rac1ShopPresenter(animations(),data(),fresh,n -> 0)) {
            p.tick(60,true); assertEquals(3,driver.submitted.size(),"New screen has a new opening epoch");
        }
        assertTrue(driver.active.isEmpty());
    }
    @Test void mutedDroppedLineIsNotRetriedWhenUnmutedAndFailureCannotBlockShopClock() throws Exception {
        var driver=new Rac1ShopVoicePlaybackTest.Driver(); driver.drop=true;
        var voice=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
        try (var p=new Rac1ShopPresenter(animations(),data(),voice,n -> 0)) {
            p.tick(60,true); assertEquals(Rac1ShopVoicePlayback.State.DROPPED,voice.state());
            driver.drop=false; p.tick(3,true); assertEquals(1,driver.submitted.size());
            p.tick(60,true); p.tick(0,true); assertEquals(2,p.frame().sequence());
        }
        driver=new Rac1ShopVoicePlaybackTest.Driver(); driver.failPlay=true;
        voice=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
        try (var p=new Rac1ShopPresenter(animations(),data(),voice,n -> 0)) {
            p.tick(120,true); p.tick(0,true);
            assertEquals(Rac1ShopVoicePlayback.State.FAILED,voice.state()); assertEquals(2,p.frame().sequence());
        }
    }
    @Test void onlyAcceptedMatchingBuyReceiptsCanSelectTransactionVoice() {
        var flow=new Rac1ShopClientFlow(UUID.randomUUID());
        flow.request(1);
        var q=new Rac1ShopPurchases.Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,1,1,100,1,250);
        var quote=new Rac1ShopNetwork.Reply(flow.token(),1,new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,q,false));
        var before=flow.mode(); assertTrue(flow.receive(quote));
        assertFalse(Rac1ShopPresenter.purchased(before,flow.mode(),flow.outcome()));
        flow.presented(q.id()); var buy=flow.confirm(); before=flow.mode();
        var receipt=new Rac1ShopNetwork.Reply(flow.token(),buy.sequence(),
                new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.PURCHASED,q,true));
        assertTrue(flow.receive(receipt)); assertTrue(Rac1ShopPresenter.purchased(before,flow.mode(),flow.outcome()));
        assertFalse(flow.receive(receipt),"Duplicate receipt must not queue another voice");
        assertFalse(Rac1ShopPresenter.purchased(Rac1ShopClientFlow.Mode.WAIT_QUOTE,
                Rac1ShopClientFlow.Mode.FAILURE,Rac1ShopPurchases.Status.PURCHASED));
        assertFalse(Rac1ShopPresenter.purchased(Rac1ShopClientFlow.Mode.WAIT_BUY,
                Rac1ShopClientFlow.Mode.DENIED,Rac1ShopPurchases.Status.INSUFFICIENT));
    }
}
