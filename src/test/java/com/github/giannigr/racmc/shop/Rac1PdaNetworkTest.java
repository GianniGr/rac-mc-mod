package com.github.giannigr.racmc.shop;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.*;

class Rac1PdaNetworkTest {
    private static <T> T roundtrip(StreamCodec<FriendlyByteBuf,T> codec,T value) {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(b,value); assertTrue(b.readableBytes()<128);
            var decoded=codec.decode(b); assertEquals(0,b.readableBytes()); return decoded;
        } finally { b.release(); }
    }
    @Test void portableOpenHasNoKioskPositionAndCommandsCarryOnlyIntent() {
        UUID token=UUID.randomUUID();
        for (var hand:InteractionHand.values()) {
            var opened=new Rac1PdaNetwork.Opened(token,hand);
            assertEquals(opened,roundtrip(Rac1PdaNetwork.Opened.CODEC,opened));
        }
        for (var command:List.of(
                new Rac1ShopNetwork.Command(token,1,Rac1ShopNetwork.Action.QUOTE,200,null),
                new Rac1ShopNetwork.Command(token,2,Rac1ShopNetwork.Action.BUY,0,UUID.randomUUID()),
                new Rac1ShopNetwork.Command(token,0,Rac1ShopNetwork.Action.CLOSE,0,null))) {
            var p=new Rac1PdaNetwork.Command(command);
            assertEquals(p,roundtrip(Rac1PdaNetwork.Command.CODEC,p));
        }
    }
    @Test void higherPriceQuotesReceiptsAndDenialsRoundtripOnSeparateChannel() {
        UUID token=UUID.randomUUID();
        for (var q:List.of(
                new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,25,250,175,25,200),
                new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,2,50,38,2,200,Rac1ShopOffer.BOMB_AMMO)))
            for (var status:List.of(Status.QUOTED,Status.PURCHASED,Status.COMMIT_REJECTED))
                for (boolean replay:new boolean[]{false,true}) {
                    var p=new Rac1PdaNetwork.Reply(token,2,new Result(status,q,replay));
                    assertEquals(p,roundtrip(Rac1PdaNetwork.Reply.CODEC,p));
                }
        var closed=new Rac1PdaNetwork.Reply(token,0,new Result(Status.INVALID_SESSION,null,false));
        assertEquals(closed,roundtrip(Rac1PdaNetwork.Reply.CODEC,closed));
        assertNotEquals(Rac1PdaNetwork.Reply.TYPE,Rac1ShopNetwork.Reply.TYPE);
    }
    @Test void weaponSalesKioskPricesOverCapAndMissingFactsReject() {
        for (var q:List.of(
                new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.WEAPON,1,2500,0,100,200),
                new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.WEAPON,1,0,0,10,200,Rac1ShopOffer.BOMB_STARTER),
                new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,1,5,39,1,200,Rac1ShopOffer.BOMB_AMMO),
                new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,1,1,0,1,200),
                new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,2,20,199,2,200),
                new Quote(UUID.randomUUID(),Rac1ShopDelivery.Kind.AMMO,1,10,0,25,200)))
            assertThrows(IllegalArgumentException.class,() ->
                    new Rac1PdaNetwork.Reply(UUID.randomUUID(),1,new Result(Status.QUOTED,q,false)));
        assertThrows(IllegalArgumentException.class,() ->
                new Rac1PdaNetwork.Reply(UUID.randomUUID(),1,new Result(Status.QUOTED,null,false)));
    }
    @Test void malformedPortableWireRejectsBeforeDispatch() {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            b.writeUUID(UUID.randomUUID()); b.writeByte(255);
            assertThrows(IllegalArgumentException.class,() -> Rac1PdaNetwork.Opened.CODEC.decode(b));
            b.clear(); assertThrows(IndexOutOfBoundsException.class,() -> Rac1PdaNetwork.Command.CODEC.decode(b));
        } finally { b.release(); }
    }
}
