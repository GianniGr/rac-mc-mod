package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopQuantityTest {
    @Test void latestLocalIntentRequiresNewMatchingQuoteAndExactPresentedFrameBeforeBuy() {
        var selection=new Rac1ShopQuantity(); var flow=new Rac1ShopClientFlow(UUID.randomUUID());
        var first=flow.request(1); var q=quote(1,100);
        assertTrue(flow.receive(new Rac1ShopNetwork.Reply(flow.token(),first.sequence(),
                new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,q,false))));
        selection.accept(q,10000); flow.presented(q.id());
        selection.adjust(5); assertFalse(selection.matches(flow.quote()),"UI gate must block an otherwise presented old quote");
        var request=flow.request(selection.request(0));
        selection.adjust(10);
        assertNull(flow.confirm());
        var old=quote(6,100);
        assertTrue(flow.receive(new Rac1ShopNetwork.Reply(flow.token(),request.sequence(),
                new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,old,false))));
        selection.accept(old,10000);
        assertFalse(selection.matches(old),"Accepted intermediate response is not current local intent");
        var latestRequest=flow.request(selection.request(4)); var latest=quote(16,100);
        assertTrue(flow.receive(new Rac1ShopNetwork.Reply(flow.token(),latestRequest.sequence(),
                new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,latest,false))));
        selection.accept(latest,10000); assertTrue(selection.matches(latest));
        assertNull(flow.confirm(),"New matching quote still needs the exact submitted frame");
        flow.presented(latest.id());
        var buy=flow.confirm(); assertEquals(latest.id(),buy.quote());
        for (int tick=1;tick<=120;tick++) {
            var retry=flow.tick();
            if (tick==40 || tick==80) assertSame(buy,retry);
        }
        assertEquals(Rac1ShopClientFlow.Mode.UNCERTAIN,flow.mode());
        assertNull(flow.request(16)); assertNull(flow.confirm());
    }
    private static Rac1ShopPurchases.Quote quote(int quantity,int arrows) {
        return new Rac1ShopPurchases.Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,quantity,quantity,arrows,quantity,200);
    }
    @Test void localRepeatContinuesThroughDelayedQuotesWithoutAuthorizingStaleQuantity() {
        var selection=new Rac1ShopQuantity(); var initial=quote(1,100);
        selection.accept(initial,10000);
        assertTrue(selection.adjust(1)); assertFalse(selection.matches(initial));
        assertEquals(2,selection.request(0));
        for (int i=0;i<60;i++) {
            assertTrue(selection.adjust(1),"Repeat must not wait on network");
            assertEquals(0,selection.request(i+1),"Never queue another in-flight quote");
        }
        selection.accept(quote(2,100),10000);
        assertEquals(62,selection.desired(),"An old accepted reply must not roll back local intent");
        assertFalse(selection.matches(quote(2,100)));
        assertEquals(62,selection.request(61));
        var exact=quote(62,100); selection.accept(exact,10000);
        assertTrue(selection.matches(exact));
    }
    @Test void coalescesThreeReferenceRepeatsPerTickAndBoundsQuoteCadenceAndLocalCap() {
        var selection=new Rac1ShopQuantity(); selection.accept(quote(1,0),10000);
        int requests=0;
        for (int tick=0;tick<20;tick++) {
            for (int repeat=0;repeat<3;repeat++) selection.adjust(1);
            int qty=selection.request(tick);
            if (qty!=0) { requests++; selection.accept(quote(qty,0),10000); }
        }
        assertEquals(61,selection.desired());
        assertEquals(5,requests,"One request per four client ticks, never one per reference repeat");
        for (int i=0;i<500;i++) selection.adjust(1);
        assertEquals(200,selection.desired());
        selection.accept(quote(197,3),10000);
        assertEquals(197,selection.desired(),"Inventory drift clamps local intent; server still validates");
        selection.accept(quote(5,3),5);
        assertEquals(5,selection.desired());
        for (int i=0;i<500;i++) selection.adjust(-1);
        assertEquals(1,selection.desired());
    }
    @Test void cancellationAndIndependentScreensDiscardIntentWithoutChangingFlowRetryOrConfirmation() {
        var a=new Rac1ShopQuantity(); var b=new Rac1ShopQuantity();
        var initial=quote(1,100);
        a.accept(initial,10000); b.accept(initial,10000);
        a.adjust(20); a.request(0);
        assertEquals(1,b.desired()); assertTrue(b.matches(initial));
        a.clear(); assertFalse(a.active()); assertFalse(a.adjust(1)); assertEquals(0,a.request(100));
        a.accept(initial,10000); assertEquals(1,a.desired());
    }
}
