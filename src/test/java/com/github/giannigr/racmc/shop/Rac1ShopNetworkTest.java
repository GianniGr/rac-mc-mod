package com.github.giannigr.racmc.shop;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopNetworkTest {
    private static <T> T roundtrip(StreamCodec<FriendlyByteBuf,T> codec,T value) {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try { codec.encode(b,value); assertTrue(b.readableBytes()<256); var decoded=codec.decode(b); assertEquals(0,b.readableBytes()); return decoded; }
        finally { b.release(); }
    }
    @Test void exactBoundedCommandOpenAndResultRoundtripsPreserveOpaqueTokensAndQuotes() {
        UUID token=UUID.randomUUID(),id=UUID.randomUUID();
        for (var c:List.of(new Rac1ShopNetwork.Command(token,1,Rac1ShopNetwork.Action.QUOTE,200,null),
                new Rac1ShopNetwork.Command(token,2,Rac1ShopNetwork.Action.QUOTE,1,null,Rac1ShopOffer.BOMB_STARTER),
                new Rac1ShopNetwork.Command(token,3,Rac1ShopNetwork.Action.QUOTE,40,null,Rac1ShopOffer.BOMB_AMMO),
                new Rac1ShopNetwork.Command(token,2,Rac1ShopNetwork.Action.BUY,0,id),
                new Rac1ShopNetwork.Command(token,0,Rac1ShopNetwork.Action.CLOSE,0,null)))
            assertEquals(c,roundtrip(Rac1ShopNetwork.Command.CODEC,c));
        var open=new Rac1ShopNetwork.Opened(token,new BlockPos(-10,60,30),ResourceLocation.parse("minecraft:overworld"),true);
        assertEquals(open,roundtrip(Rac1ShopNetwork.Opened.CODEC,open));
        var q=new Rac1ShopPurchases.Quote(id,Rac1ShopDelivery.Kind.WEAPON,1,2500,250,0,200);
        var p=new Rac1ShopNetwork.Reply(token,2,new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.PURCHASED,q,true));
        assertEquals(p,roundtrip(Rac1ShopNetwork.Reply.CODEC,p));
    }
    @Test void malformedUnknownEnumTruncationQuantityAndSequenceAreRejectedBeforeDispatch() {
        UUID token=UUID.randomUUID();
        for (int qty:new int[]{0,201,65535}) assertThrows(IllegalArgumentException.class,() ->
                new Rac1ShopNetwork.Command(token,1,Rac1ShopNetwork.Action.QUOTE,qty,null));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopNetwork.Command(token,0,Rac1ShopNetwork.Action.BUY,0,UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopNetwork.Command(token,258,Rac1ShopNetwork.Action.BUY,0,UUID.randomUUID()));
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try { b.writeUUID(token); b.writeLong(1); b.writeByte(255);
            assertThrows(IllegalArgumentException.class,() -> Rac1ShopNetwork.Command.CODEC.decode(b));
            b.clear(); b.writeUUID(token); b.writeLong(1); b.writeByte(Rac1ShopNetwork.Action.QUOTE.ordinal());
            b.writeShort(1); b.writeByte(255);
            assertThrows(IllegalArgumentException.class,() -> Rac1ShopNetwork.Command.CODEC.decode(b));
            b.clear(); assertThrows(IndexOutOfBoundsException.class,() -> Rac1ShopNetwork.Command.CODEC.decode(b));
        } finally { b.release(); }
    }
    @Test void bombQuotesValidateStarterAndPaidStockAsDifferentOffers() {
        UUID token=UUID.randomUUID(), id=UUID.randomUUID();
        for (var q:List.of(
                new Rac1ShopPurchases.Quote(id,Rac1ShopDelivery.Kind.WEAPON,1,0,42,10,200,Rac1ShopOffer.BOMB_STARTER),
                new Rac1ShopPurchases.Quote(id,Rac1ShopDelivery.Kind.AMMO,2,10,38,2,200,Rac1ShopOffer.BOMB_AMMO))) {
            var reply=new Rac1ShopNetwork.Reply(token,2,
                    new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,q,false));
            assertEquals(reply,roundtrip(Rac1ShopNetwork.Reply.CODEC,reply));
        }
        var bad=new Rac1ShopPurchases.Quote(id,Rac1ShopDelivery.Kind.AMMO,2,10,39,2,200,Rac1ShopOffer.BOMB_AMMO);
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopNetwork.Reply(token,2,
                new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,bad,false)));
    }
    @Test void quotesCannotLieAboutOriginalPriceUnitDeliveryOrOmitRequiredFacts() {
        UUID token=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopNetwork.Reply(token,1,
                new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,null,false)));
        var bad=new Rac1ShopPurchases.Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,1,25,0,25,200);
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopNetwork.Reply(token,1,
                new Rac1ShopPurchases.Result(Rac1ShopPurchases.Status.QUOTED,bad,false)));
    }
}
