package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.Status.*;

class Rac1ShopDeniedIntentTest {
    private static Rac1ShopNetwork.Reply reply(Rac1ShopClientFlow flow,long seq,Rac1ShopPurchases.Status status,boolean replay) {
        return new Rac1ShopNetwork.Reply(flow.token(),seq,new Rac1ShopPurchases.Result(status,null,replay));
    }
    @Test void genuineMaxedOpeningOnceAndFreshSecondOpeningButNotHeldRetryOrDuplicateAck() {
        var f=new Rac1ShopClientFlow(UUID.randomUUID());
        assertTrue(f.activationDown(257)); var command=f.beginBuy();
        assertFalse(f.activationDown(257)); assertNull(f.beginBuy());
        assertTrue(f.receive(reply(f,command.sequence(),STOCK_CAP,false)));
        assertEquals(Rac1ShopClientFlow.Mode.MAXED,f.mode()); assertEquals(1,f.deniedOpening());
        assertFalse(f.receive(reply(f,command.sequence(),STOCK_CAP,false))); assertEquals(1,f.deniedOpening());
        f.cancel(); f.release(257); assertTrue(f.activationDown(257));
        command=f.beginBuy(); f.receive(reply(f,command.sequence(),STOCK_CAP,false));
        assertEquals(2,f.deniedOpening());
    }
    @Test void requoteReplayForeignFailureAndLaterConfirmedPurchaseFailureNeverRequestCue() {
        for (var status:new Rac1ShopPurchases.Status[]{STOCK_CAP,INSUFFICIENT,FULL,STALE_BALANCE,COMMIT_REJECTED}) {
            var f=new Rac1ShopClientFlow(UUID.randomUUID()); var command=f.request(1);
            f.receive(reply(f,command.sequence(),status,false)); assertEquals(0,f.deniedOpening());
        }
        var f=new Rac1ShopClientFlow(UUID.randomUUID()); var command=f.beginBuy();
        f.receive(reply(f,command.sequence(),STOCK_CAP,true)); assertEquals(0,f.deniedOpening());
        f.cancel(); command=f.beginBuy();
        assertFalse(f.receive(new Rac1ShopNetwork.Reply(UUID.randomUUID(),command.sequence(),
                new Rac1ShopPurchases.Result(STOCK_CAP,null,false))));
        assertEquals(0,f.deniedOpening());
        var quote=new Rac1ShopPurchases.Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,1,1,199,1,200);
        f.receive(new Rac1ShopNetwork.Reply(f.token(),command.sequence(),new Rac1ShopPurchases.Result(QUOTED,quote,false)));
        f.presented(quote.id()); command=f.confirm();
        f.receive(reply(f,command.sequence(),INSUFFICIENT,false)); assertEquals(0,f.deniedOpening());
    }
    @Test void successReachingTwoHundredIsNotADeniedOpeningAndInitialPoorUsesSameCue() {
        var f=new Rac1ShopClientFlow(UUID.randomUUID()); var command=f.beginBuy();
        var q=new Rac1ShopPurchases.Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,1,1,199,1,200);
        f.receive(new Rac1ShopNetwork.Reply(f.token(),command.sequence(),new Rac1ShopPurchases.Result(QUOTED,q,false)));
        f.presented(q.id()); command=f.confirm();
        f.receive(new Rac1ShopNetwork.Reply(f.token(),command.sequence(),new Rac1ShopPurchases.Result(PURCHASED,q,false)));
        assertEquals(0,f.deniedOpening());
        command=f.beginBuy(); f.receive(reply(f,command.sequence(),INSUFFICIENT,false));
        assertEquals(1,f.deniedOpening()); assertEquals(Rac1ShopClientFlow.Mode.DENIED,f.mode());
    }
}
