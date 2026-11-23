package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.shop.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.*;
import static com.github.giannigr.racmc.client.pda.Rac1PdaClientFlow.Mode;

class Rac1PdaClientFlowTest {
    private static final class Fixture {
        final UUID token=UUID.randomUUID();
        final Rac1PdaClientFlow flow=new Rac1PdaClientFlow(token);
        Quote q=new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,25,250,0,25,201);
        boolean reply(long seq,Status status,Quote quote,boolean replay) {
            return flow.receive(new Rac1PdaNetwork.Reply(token,seq,new Result(status,quote,replay)));
        }
        void quote() { flow.request(25); assertTrue(reply(1,Status.QUOTED,q,false)); }
    }
    @Test void higherPriceQuoteNeedsActualPresentationBeforeExplicitConfirmation() {
        var f=new Fixture(); var request=f.flow.request(25).intent();
        assertEquals(Rac1ShopNetwork.Action.QUOTE,request.action()); assertEquals(25,request.quantity());
        assertNull(f.flow.confirm()); assertNull(f.flow.lastPurchase());
        assertTrue(f.reply(1,Status.QUOTED,f.q,false)); assertEquals(250,f.flow.quote().price());
        assertNull(f.flow.confirm());
        f.flow.presented(UUID.randomUUID()); assertNull(f.flow.confirm());
        f.flow.presented(f.q.id()); var buy=f.flow.confirm().intent();
        assertEquals(f.q.id(),buy.quote()); assertEquals(2,buy.sequence());
        assertEquals(Mode.WAIT_BUY,f.flow.mode()); assertNull(f.flow.lastPurchase());
        assertTrue(f.reply(2,Status.PURCHASED,f.q,false));
        assertEquals(Mode.BROWSE,f.flow.mode()); assertEquals(f.q,f.flow.lastPurchase());
        assertFalse(f.reply(2,Status.PURCHASED,f.q,true)); // duplicate receipt cannot repeat feedback
    }
    @Test void mismatchedOwnerSequenceQuantityAndQuoteAreIgnored() {
        var f=new Fixture(); f.flow.request(25);
        assertFalse(f.flow.receive(new Rac1PdaNetwork.Reply(UUID.randomUUID(),1,new Result(Status.QUOTED,f.q,false))));
        assertFalse(f.reply(2,Status.QUOTED,f.q,false));
        var wrong=new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,1,10,0,1,201);
        assertFalse(f.reply(1,Status.QUOTED,wrong,false));
        assertTrue(f.reply(1,Status.QUOTED,f.q,false)); f.flow.presented(f.q.id()); f.flow.confirm();
        assertFalse(f.reply(2,Status.PURCHASED,wrong,false)); assertEquals(Mode.WAIT_BUY,f.flow.mode());
    }
    @Test void repeatedActivationAndWaitingCannotGenerateAnotherRequest() {
        var f=new Fixture();
        assertTrue(f.flow.activationDown(257)); assertFalse(f.flow.activationDown(257));
        f.flow.release(257); assertTrue(f.flow.activationDown(257));
        f.flow.request(25); assertNull(f.flow.request(1)); assertNull(f.flow.confirm());
    }
    @Test void pendingBuyRetransmitsExactIntentThenBlocksFreshSpendingOnTimeout() {
        var f=new Fixture(); f.quote(); f.flow.presented(f.q.id()); var buy=f.flow.confirm();
        for (int i=1;i<=120;i++) {
            var retry=f.flow.tick();
            if (i==40 || i==80) assertEquals(buy,retry);
            else assertNull(retry);
        }
        assertEquals(Mode.UNCERTAIN,f.flow.mode()); assertNull(f.flow.request(1));
        f.flow.cancelQuote(); assertEquals(Mode.UNCERTAIN,f.flow.mode());
        assertTrue(f.reply(2,Status.PURCHASED,f.q,true)); assertEquals(Mode.BROWSE,f.flow.mode());
    }
    @Test void uncertainQuoteAlsoRetainsSequenceAndAcceptsLateReceipt() {
        var f=new Fixture(); f.flow.request(25);
        for (int i=0;i<120;i++) f.flow.tick();
        assertEquals(Mode.UNCERTAIN,f.flow.mode()); assertNull(f.flow.request(1));
        assertTrue(f.reply(1,Status.QUOTED,f.q,true)); assertEquals(Mode.CONFIRM,f.flow.mode());
    }
    @Test void rateLimitDoesNotAdvanceSequenceOrPermitAnotherPurchase() {
        var f=new Fixture(); var request=f.flow.request(25);
        assertTrue(f.reply(1,Status.RATE_LIMIT,null,false));
        for (int i=1;i<40;i++) assertNull(f.flow.tick());
        assertEquals(request,f.flow.tick()); assertNull(f.flow.request(1));
        assertTrue(f.reply(1,Status.QUOTED,f.q,false));
        f.flow.presented(f.q.id()); assertEquals(2,f.flow.confirm().intent().sequence());
    }
    @Test void cancelQuoteReservesNothingAndCannotConfirmAnOldQuote() {
        var f=new Fixture(); f.quote(); f.flow.presented(f.q.id()); f.flow.cancelQuote();
        assertNull(f.flow.confirm()); assertEquals(Mode.BROWSE,f.flow.mode());
        var next=f.flow.request(1); assertEquals(2,next.intent().sequence());
        assertFalse(f.reply(1,Status.QUOTED,f.q,true));
    }
    @Test void expirationDisarmsPresentedQuoteAndAllowsRequote() {
        var f=new Fixture(); f.quote(); f.flow.presented(f.q.id());
        for (int i=0;i<QUOTE_TICKS;i++) f.flow.tick();
        assertNull(f.flow.confirm()); assertEquals(Status.STALE_QUOTE,f.flow.outcome());
        assertNotNull(f.flow.request(1));
    }
    @Test void denialFullAndRevisionFailureAreVisualOutcomesNotSuccess() {
        for (var status:new Status[]{Status.INSUFFICIENT,Status.STOCK_CAP,Status.FULL,Status.STALE_BALANCE,Status.STALE_OFFER}) {
            var f=new Fixture(); f.flow.request(25); assertTrue(f.reply(1,status,null,false));
            assertEquals(Mode.FAILURE,f.flow.mode()); assertEquals(status,f.flow.outcome());
            assertNull(f.flow.lastPurchase()); assertNull(f.flow.confirm()); assertNotNull(f.flow.request(1));
        }
    }
    @Test void closeIsIdempotentAndLateReceiptsNeverReopenOrGrant() {
        var f=new Fixture(); f.quote(); f.flow.presented(f.q.id()); f.flow.confirm();
        var close=f.flow.close().intent(); assertEquals(0,close.sequence());
        assertEquals(Rac1ShopNetwork.Action.CLOSE,close.action()); assertNull(f.flow.close());
        assertFalse(f.reply(2,Status.PURCHASED,f.q,false)); assertEquals(Mode.CLOSED,f.flow.mode());
        assertNull(f.flow.lastPurchase());
    }
    @Test void serverInvalidationAlwaysClosesOnlyMatchingSession() {
        var f=new Fixture(); f.quote();
        assertTrue(f.reply(0,Status.INVALID_SESSION,null,false)); assertEquals(Mode.CLOSED,f.flow.mode());
        assertNull(f.flow.request(1)); assertNull(f.flow.close());
    }
    @Test void malformedQuantityIsNeverSent() {
        var f=new Fixture();
        for (int qty:new int[]{Integer.MIN_VALUE,-1,0,201,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,() -> f.flow.request(qty));
    }
}
