package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopMouseConfirmationTest {
    @Test void sameTarget112msDoubleClickCannotSendBuyForEitherOffer() {
        for (var kind:Rac1ShopDelivery.Kind.values()) {
            var flow=new Rac1ShopClientFlow(UUID.randomUUID());
            var mouse=new Rac1ShopMouseConfirmation();
            assertTrue(mouse.press()); var request=flow.request(1);
            mouse.release(); // first 37ms down
            var q=quote(kind);
            flow.receive(reply(flow,request.sequence(),q)); flow.presented(q.id());
            mouse.presented(q.id(),60); // quote really rendered between clicks
            assertTrue(mouse.press()); // second press at112ms
            assertFalse(mouse.approve(q.id(),112));
            assertEquals(Rac1ShopClientFlow.Mode.CONFIRM,flow.mode());
            mouse.release();
            // Waiting alone cannot convert repeated same-target clicks into consent.
            assertTrue(mouse.press()); assertFalse(mouse.approve(q.id(),2000)); mouse.release();
            mouse.movedOutsideBuy(); // deliberate move away, then back to Yes
            assertTrue(mouse.press()); assertTrue(mouse.approve(q.id(),2100));
            var buy=flow.confirm();
            assertEquals(Rac1ShopNetwork.Action.BUY,buy.action());
            assertEquals(q.id(),buy.quote());
            assertNull(flow.confirm()); assertFalse(mouse.approve(q.id(),2101));
        }
    }

    @Test void moveAloneDoesNotWaiveReadTimeAndHeldRepeatsAreNotEdges() {
        var mouse=new Rac1ShopMouseConfirmation(); var id=UUID.randomUUID();
        mouse.presented(id,100);
        mouse.movedOutsideBuy();
        assertTrue(mouse.press()); assertFalse(mouse.press());
        assertFalse(mouse.approve(id,599)); mouse.release();
        assertTrue(mouse.press()); assertTrue(mouse.approve(id,600));
        assertFalse(mouse.press()); assertFalse(mouse.approve(id,700));
    }

    @Test void heldInitialPressCannotArmByDraggingOutOrRepeating() {
        var mouse=new Rac1ShopMouseConfirmation(); var id=UUID.randomUUID();
        mouse.press(); mouse.presented(id,100); mouse.movedOutsideBuy();
        assertFalse(mouse.press()); assertFalse(mouse.approve(id,1000));
        mouse.release();
        assertTrue(mouse.press()); assertFalse(mouse.approve(id,1100));
        mouse.release(); mouse.movedOutsideBuy(); mouse.press();
        assertTrue(mouse.approve(id,1200));
    }

    @Test void cancellationReplacementAndForeignQuoteRequireNewConsent() {
        var mouse=new Rac1ShopMouseConfirmation(); var old=UUID.randomUUID(); var next=UUID.randomUUID();
        mouse.movedOutsideBuy(); // movement before an actual confirmation frame does not arm
        mouse.presented(old,0); mouse.press(); assertFalse(mouse.approve(old,1000)); mouse.release();
        mouse.movedOutsideBuy(); mouse.clear(); mouse.press();
        assertFalse(mouse.approve(old,1000)); mouse.release();
        mouse.presented(next,1000); mouse.press();
        assertFalse(mouse.approve(old,2000)); assertFalse(mouse.approve(next,2000)); mouse.release();
        mouse.movedOutsideBuy(); mouse.presented(next,2100); // redraw must not restart dwell
        mouse.press(); assertTrue(mouse.approve(next,2100)); mouse.release();
        mouse.presented(old,2200); mouse.movedOutsideBuy(); mouse.press();
        assertFalse(mouse.approve(old,2199)); // backwards timestamp fails closed
    }

    @Test void keyboardFreshEdgesStillConfirmWithoutMouseMovementOrDelayAndNoCancels() {
        var flow=new Rac1ShopClientFlow(UUID.randomUUID());
        var mouse=new Rac1ShopMouseConfirmation();
        assertTrue(flow.activationDown(257)); var request=flow.request(1);
        var q=quote(Rac1ShopDelivery.Kind.WEAPON);
        flow.receive(reply(flow,request.sequence(),q)); flow.presented(q.id()); mouse.presented(q.id(),0);
        assertFalse(flow.activationDown(257)); // held Enter cannot confirm
        flow.release(257); assertTrue(flow.activationDown(257));
        assertNotNull(flow.confirm()); // no mouse-only adaptation on original button edges

        var cancel=new Rac1ShopClientFlow(UUID.randomUUID()); request=cancel.request(1);
        cancel.receive(reply(cancel,request.sequence(),q)); cancel.presented(q.id());
        cancel.cancel(); mouse.clear();
        assertEquals(Rac1ShopClientFlow.Mode.BROWSE,cancel.mode());
        assertNull(cancel.confirm()); mouse.press(); assertFalse(mouse.approve(q.id(),1000));
    }

    private static Rac1ShopPurchases.Quote quote(Rac1ShopDelivery.Kind kind) {
        return new Rac1ShopPurchases.Quote(UUID.randomUUID(),kind,1,
                kind==Rac1ShopDelivery.Kind.WEAPON ? 2500:1,0,
                kind==Rac1ShopDelivery.Kind.WEAPON ? 100:1,250);
    }
    private static Rac1ShopNetwork.Reply reply(Rac1ShopClientFlow flow,long sequence,Rac1ShopPurchases.Quote quote) {
        return new Rac1ShopNetwork.Reply(flow.token(),sequence,
                new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,quote,false));
    }
}
