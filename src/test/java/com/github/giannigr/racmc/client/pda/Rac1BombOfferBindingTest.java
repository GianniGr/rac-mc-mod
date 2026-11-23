package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.client.shop.Rac1ShopClientFlow;
import com.github.giannigr.racmc.shop.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.*;

class Rac1BombOfferBindingTest {
    private static Quote ammo(UUID id,Rac1ShopOffer offer,boolean pda) {
        return new Quote(id,Rac1ShopDelivery.Kind.AMMO,1,offer.stock().unitPrice(pda),0,1,200,offer);
    }
    @Test void pdaCannotClaimAndRequiresMatchingWeaponSpecificQuoteAndReceipt() {
        UUID token=UUID.randomUUID(),id=UUID.randomUUID();
        var flow=new Rac1PdaClientFlow(token);
        assertThrows(IllegalArgumentException.class,() -> flow.request(1,Rac1ShopOffer.BOMB_STARTER));
        assertEquals(Rac1ShopOffer.BOMB_AMMO,flow.request(1,Rac1ShopOffer.BOMB_AMMO).intent().offer());
        assertFalse(flow.receive(new Rac1PdaNetwork.Reply(token,1,new Result(Status.QUOTED,ammo(id,Rac1ShopOffer.BLASTER,true),false))));
        var quote=ammo(id,Rac1ShopOffer.BOMB_AMMO,true);
        assertTrue(flow.receive(new Rac1PdaNetwork.Reply(token,1,new Result(Status.QUOTED,quote,false))));
        assertEquals(25,flow.quote().price()); flow.presented(id);
        assertEquals(id,flow.confirm().intent().quote());
        assertThrows(IllegalArgumentException.class,() -> new Rac1PdaNetwork.Reply(token,2,new Result(Status.PURCHASED,
                new Quote(id,quote.kind(),quote.quantity(),999,quote.currentAmmo(),quote.deliveredAmmo(),quote.expires(),quote.offer()),false)));
        assertFalse(flow.receive(new Rac1PdaNetwork.Reply(token,2,new Result(Status.PURCHASED,
                new Quote(id,quote.kind(),quote.quantity(),quote.price(),quote.currentAmmo()+1,quote.deliveredAmmo(),quote.expires(),quote.offer()),false))));
        assertFalse(flow.receive(new Rac1PdaNetwork.Reply(token,2,new Result(Status.PURCHASED,ammo(id,Rac1ShopOffer.BLASTER,true),false))));
        assertTrue(flow.receive(new Rac1PdaNetwork.Reply(token,2,new Result(Status.PURCHASED,quote,false))));
    }
    @Test void kioskPaidWeaponQuoteCannotSwapKindOrReceiptRow() {
        UUID token=UUID.randomUUID(),id=UUID.randomUUID();
        var flow=new Rac1ShopClientFlow(token);
        assertEquals(Rac1ShopOffer.BOMB_AMMO,flow.request(1,Rac1ShopOffer.BOMB_AMMO).offer());
        var quote=new Quote(id,Rac1ShopDelivery.Kind.WEAPON,1,1000,4,6,200,Rac1ShopOffer.BOMB_AMMO);
        assertFalse(flow.receive(new Rac1ShopNetwork.Reply(token,1,new Result(Status.QUOTED,ammo(id,Rac1ShopOffer.BLASTER,false),false))));
        assertTrue(flow.receive(new Rac1ShopNetwork.Reply(token,1,new Result(Status.QUOTED,quote,false))));
        assertEquals(1000,flow.quote().price()); assertEquals(6,flow.quote().deliveredAmmo());
        flow.presented(id); assertEquals(id,flow.confirm().quote());
        assertFalse(flow.receive(new Rac1ShopNetwork.Reply(token,2,new Result(Status.PURCHASED,ammo(id,Rac1ShopOffer.BOMB_AMMO,false),false))));
        assertTrue(flow.receive(new Rac1ShopNetwork.Reply(token,2,new Result(Status.PURCHASED,quote,false))));
    }
    @Test void ammoRequoteCannotBecomeAWeaponPurchaseWhenOwnershipChanges() {
        var flow=new Rac1ShopClientFlow(UUID.randomUUID());
        var offer=Rac1ShopOffer.BOMB_AMMO;
        flow.beginBuy(offer);
        flow.receive(new Rac1ShopNetwork.Reply(flow.token(),1,new Result(Status.QUOTED,ammo(UUID.randomUUID(),offer,false),false)));
        flow.request(1,offer);
        var weapon=new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.WEAPON,1,1000,0,10,200,offer);
        assertTrue(flow.receive(new Rac1ShopNetwork.Reply(flow.token(),2,new Result(Status.QUOTED,weapon,false))));
        flow.presented(weapon.id());
        assertEquals(Status.STALE_OFFER,flow.outcome()); assertNull(flow.confirm());
    }
    @Test void bothPendingBuysKeepExactRetryIdentityAndRejectReselectionThroughUncertainty() {
        UUID token=UUID.randomUUID(),id=UUID.randomUUID();
        var q=ammo(id,Rac1ShopOffer.BOMB_AMMO,true);
        var pda=new Rac1PdaClientFlow(token); pda.request(1,Rac1ShopOffer.BOMB_AMMO);
        pda.receive(new Rac1PdaNetwork.Reply(token,1,new Result(Status.QUOTED,q,false)));
        pda.presented(id); var buy=pda.confirm();
        for (int tick=1;tick<=120;tick++) {
            var retry=pda.tick();
            if (tick==40 || tick==80) assertEquals(buy,retry);
        }
        assertNull(pda.request(1,Rac1ShopOffer.BLASTER));
        assertNull(pda.confirm());
        assertTrue(pda.receive(new Rac1PdaNetwork.Reply(token,2,new Result(Status.PURCHASED,q,true))));
        assertFalse(pda.receive(new Rac1PdaNetwork.Reply(token,2,new Result(Status.PURCHASED,q,true))));

        var kiosk=new Rac1ShopClientFlow(token); q=ammo(id,Rac1ShopOffer.BOMB_AMMO,false);
        kiosk.beginBuy(Rac1ShopOffer.BOMB_AMMO);
        kiosk.receive(new Rac1ShopNetwork.Reply(token,1,new Result(Status.QUOTED,q,false)));
        kiosk.presented(id); var kioskBuy=kiosk.confirm();
        for (int tick=1;tick<=120;tick++) {
            var retry=kiosk.tick();
            if (tick==40 || tick==80) assertEquals(kioskBuy,retry);
        }
        kiosk.cancel(); assertEquals(Rac1ShopClientFlow.Mode.UNCERTAIN,kiosk.mode());
        assertNull(kiosk.request(1,Rac1ShopOffer.BLASTER));
        assertNull(kiosk.confirm());
        assertTrue(kiosk.receive(new Rac1ShopNetwork.Reply(token,2,new Result(Status.PURCHASED,q,true))));
        assertFalse(kiosk.receive(new Rac1ShopNetwork.Reply(token,2,new Result(Status.PURCHASED,q,true))));
    }
    @Test void kioskMarkedBombAmmoUsesFiveWithoutChangingLegacyBlasterDefault() {
        var bomb=new Rac1ShopClientFlow(UUID.randomUUID());
        var token=bomb.token(); var id=UUID.randomUUID();
        assertEquals(Rac1ShopOffer.BOMB_AMMO,bomb.request(1,Rac1ShopOffer.BOMB_AMMO).offer());
        assertTrue(bomb.receive(new Rac1ShopNetwork.Reply(token,1,new Result(Status.QUOTED,ammo(id,Rac1ShopOffer.BOMB_AMMO,false),false))));
        assertEquals(5,bomb.quote().price());
        var legacy=new Rac1ShopClientFlow(UUID.randomUUID());
        assertEquals(Rac1ShopOffer.BLASTER,legacy.beginBuy().offer());
    }
}
