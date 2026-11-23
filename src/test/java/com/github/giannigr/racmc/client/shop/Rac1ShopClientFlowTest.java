package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopClientFlowTest {
    private static Rac1ShopNetwork.Reply reply(UUID token,long seq,Rac1ShopPurchases.Status status,Rac1ShopPurchases.Quote q,boolean replay) {
        return new Rac1ShopNetwork.Reply(token,seq,new Rac1ShopPurchases.Result(status,q,replay));
    }
    private static Rac1ShopPurchases.Quote quote(int qty) {
        return new Rac1ShopPurchases.Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,qty,qty,100,qty,250);
    }
    @Test void onlyMatchingServerQuoteCanConfirmAndDuplicateResultsCannotPurchaseAgain() {
        UUID token=UUID.randomUUID(); var flow=new Rac1ShopClientFlow(token);
        assertNull(flow.confirm()); var request=flow.request(1); var q=quote(1);
        assertFalse(flow.receive(reply(UUID.randomUUID(),1,Rac1ShopPurchases.Status.QUOTED,q,false)));
        assertFalse(flow.receive(reply(token,2,Rac1ShopPurchases.Status.QUOTED,q,false)));
        assertFalse(flow.receive(reply(token,1,Rac1ShopPurchases.Status.QUOTED,quote(2),false)));
        assertTrue(flow.receive(reply(token,1,Rac1ShopPurchases.Status.QUOTED,q,false)));
        assertNull(flow.confirm(),"Receiving a quote is not displaying its confirmation");
        flow.presented(UUID.randomUUID()); assertNull(flow.confirm());
        flow.presented(q.id());
        var buy=flow.confirm(); assertEquals(q.id(),buy.quote()); assertEquals(2,buy.sequence()); assertNull(flow.confirm());
        assertTrue(flow.receive(reply(token,2,Rac1ShopPurchases.Status.PURCHASED,q,true)));
        assertEquals(Rac1ShopClientFlow.Mode.BROWSE,flow.mode());
        assertFalse(flow.receive(reply(token,2,Rac1ShopPurchases.Status.PURCHASED,q,true))); assertNull(flow.confirm());
        assertEquals(Rac1ShopNetwork.Action.QUOTE,request.action());
    }
    @Test void retryKeepsExactSequenceAndTimeoutBuyLocksUntilReceiptOrClose() {
        var flow=new Rac1ShopClientFlow(UUID.randomUUID()); var command=flow.request(1);
        for (int i=1;i<=80;i++) {
            var retry=flow.tick(); if (i==40 || i==80) assertSame(command,retry); else assertNull(retry);
        }
        var q=quote(1); flow.receive(reply(flow.token(),1,Rac1ShopPurchases.Status.QUOTED,q,false)); flow.presented(q.id()); command=flow.confirm();
        for (int i=0;i<120;i++) flow.tick();
        assertEquals(Rac1ShopClientFlow.Mode.UNCERTAIN,flow.mode()); flow.cancel();
        assertNull(flow.request(1)); assertNull(flow.confirm());
        assertTrue(flow.receive(reply(flow.token(),command.sequence(),Rac1ShopPurchases.Status.PURCHASED,q,true)));
        assertEquals(Rac1ShopClientFlow.Mode.BROWSE,flow.mode());
    }
    @Test void cancellationStaleRepliesExpiryAndIndependentScreensFailClosed() {
        var a=new Rac1ShopClientFlow(UUID.randomUUID()); var b=new Rac1ShopClientFlow(UUID.randomUUID());
        a.request(1); a.cancel(); assertFalse(a.receive(reply(a.token(),1,Rac1ShopPurchases.Status.QUOTED,quote(1),false)));
        a.request(1); var q=quote(1); a.receive(reply(a.token(),2,Rac1ShopPurchases.Status.QUOTED,q,false));
        a.presented(q.id());
        for (int i=0;i<200;i++) a.tick();
        assertEquals(Rac1ShopClientFlow.Mode.FAILURE,a.mode()); assertNull(a.quote()); assertNull(a.confirm());
        assertEquals(Rac1ShopClientFlow.Mode.BROWSE,b.mode()); a.close();
        assertFalse(a.receive(reply(a.token(),2,Rac1ShopPurchases.Status.QUOTED,q,false)));
        b.receive(reply(b.token(),0,Rac1ShopPurchases.Status.INVALID_SESSION,null,false));
        assertEquals(Rac1ShopClientFlow.Mode.CLOSED,b.mode());
    }
    @Test void onlyMoneyAndCapFailuresUseTheirOriginalDeniedRoles() {
        var flow=new Rac1ShopClientFlow(UUID.randomUUID()); flow.request(1);
        flow.receive(reply(flow.token(),1,Rac1ShopPurchases.Status.FULL,null,false));
        assertEquals(Rac1ShopClientFlow.Mode.FAILURE,flow.mode()); flow.cancel(); flow.request(1);
        flow.receive(reply(flow.token(),2,Rac1ShopPurchases.Status.INSUFFICIENT,null,false));
        assertEquals(Rac1ShopClientFlow.Mode.DENIED,flow.mode()); flow.cancel(); flow.request(1);
        flow.receive(reply(flow.token(),3,Rac1ShopPurchases.Status.STOCK_CAP,null,false));
        assertEquals(Rac1ShopClientFlow.Mode.MAXED,flow.mode());
    }
    @Test void heldBuyKeyCannotAutoConfirmWhenServerQuoteArrives() {
        var flow=new Rac1ShopClientFlow(UUID.randomUUID());
        assertTrue(flow.activationDown(257)); flow.request(1);
        flow.receive(reply(flow.token(),1,Rac1ShopPurchases.Status.QUOTED,quote(1),false));
        assertFalse(flow.activationDown(257),"OS repeat must not become confirmation");
        assertEquals(Rac1ShopClientFlow.Mode.CONFIRM,flow.mode());
        flow.presented(flow.quote().id());
        flow.release(257); assertTrue(flow.activationDown(257)); assertNotNull(flow.confirm());
    }
}
