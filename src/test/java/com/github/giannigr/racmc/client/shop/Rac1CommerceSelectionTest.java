package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1CommerceSelectionTest {
    @Test void directionalBrowsingHasEndpointsAndCannotSpendOrWrap() {
        var shop=new Rac1CommerceSelection(false,true,true);
        assertFalse(shop.move(-1)); assertTrue(shop.blaster());
        assertTrue(shop.move(1)); assertEquals(Rac1ShopOffer.BOMB_AMMO,shop.offer());
        assertTrue(shop.move(1)); assertEquals(Rac1ShopOffer.BOMB_STARTER,shop.offer());
        assertFalse(shop.move(1)); assertTrue(shop.move(-1));
        assertEquals(Rac1ShopOffer.BOMB_AMMO,shop.offer());
        assertThrows(IllegalArgumentException.class,() -> shop.move(0));
        var flow=new Rac1ShopClientFlow(UUID.randomUUID());
        assertTrue(flow.activationDown(262));
        assertFalse(flow.activationDown(262),"OS repeat is not a new browse edge");
        flow.release(262); assertTrue(flow.activationDown(262));
        assertEquals(Rac1ShopClientFlow.Mode.BROWSE,flow.mode());
    }
    private static Rac1ShopPurchases.Quote quote(Rac1ShopOffer row,int qty,int current,int delivered,long total) {
        return new Rac1ShopPurchases.Quote(UUID.randomUUID(),row==Rac1ShopOffer.BOMB_STARTER
                ? Rac1ShopDelivery.Kind.WEAPON:Rac1ShopDelivery.Kind.AMMO,qty,total,current,delivered,200,row);
    }
    @Test void portableHasOnlyRealAmmoRowsNeverStarterAndNeverPlaceholder() {
        var absent=new Rac1CommerceSelection(true,false,true);
        absent.cycle(); assertEquals(List.of(Rac1ShopOffer.BLASTER),absent.rows());
        var pda=new Rac1CommerceSelection(true,true,true);
        assertEquals(List.of(Rac1ShopOffer.BLASTER,Rac1ShopOffer.BOMB_AMMO),pda.rows());
        pda.cycle(); assertEquals(Rac1ShopOffer.BOMB_AMMO,pda.offer());
        assertTrue(pda.label().contains("25 bolts/round; cap 40"));
        pda.cycle(); assertTrue(pda.blaster());
    }
    @Test void kioskUsesServerStarterAvailabilityAndExactTenNotCapFill() {
        var used=new Rac1CommerceSelection(false,true,false);
        assertFalse(used.rows().contains(Rac1ShopOffer.BOMB_STARTER));
        var shop=new Rac1CommerceSelection(false,true,true);
        shop.cycle(); assertTrue(shop.label().contains("5 bolts/round"));
        shop.cycle(); assertEquals(Rac1ShopOffer.BOMB_STARTER,shop.offer());
        var q=quote(Rac1ShopOffer.BOMB_STARTER,1,39,10,0);
        String details=String.join("\n",shop.details(q));
        assertTrue(details.contains("MC acquisition adaptation"));
        assertTrue(details.contains("exactly 10")); assertTrue(details.contains("0 bolts (FREE)"));
        shop.purchased(q);
        assertEquals(Rac1ShopOffer.BOMB_AMMO,shop.offer());
        assertFalse(shop.rows().contains(Rac1ShopOffer.BOMB_STARTER));
        assertTrue(String.join("\n",shop.details(null)).contains("personal claim spent"));
    }
    @Test void paidReviewShowsOnlyMatchingServerRowQuantityDeliveryAndTotal() {
        var pda=new Rac1CommerceSelection(true,true,false); pda.cycle();
        var q=quote(Rac1ShopOffer.BOMB_AMMO,7,33,7,175);
        var details=String.join("\n",pda.details(q));
        assertTrue(details.contains("Current: 33; deliver: 7"));
        assertTrue(details.contains("TOTAL: 175 bolts; quantity: 7"));
        assertFalse(String.join("\n",pda.details(quote(Rac1ShopOffer.BLASTER,7,33,7,70))).contains("TOTAL:"));
        pda.purchased(q);
        assertTrue(String.join("\n",pda.details(null)).contains("Paid 175 bolts"));
        pda.cycle(); pda.cycle();
        assertFalse(String.join("\n",pda.details(null)).contains("receipt"));
    }
    @Test void kioskQuantityUsesBombCapAndFiveBoltPriceAndDoesNotMatchBlasterQuote() {
        var quantity=new Rac1ShopQuantity();
        var bomb=quote(Rac1ShopOffer.BOMB_AMMO,1,33,1,5);
        quantity.accept(bomb,25); quantity.adjust(200);
        assertEquals(5,quantity.desired(),"Budget is five rounds, not25");
        assertFalse(quantity.matches(bomb));
        quantity.accept(quote(Rac1ShopOffer.BOMB_AMMO,5,33,5,25),25);
        assertTrue(quantity.matches(quote(Rac1ShopOffer.BOMB_AMMO,5,33,5,25)));
        assertFalse(quantity.matches(quote(Rac1ShopOffer.BLASTER,5,33,5,5)));
        quantity.accept(quote(Rac1ShopOffer.BOMB_AMMO,5,39,1,5),1000);
        assertEquals(1,quantity.desired(),"Inventory drift clamps against40, not200");
    }
}
