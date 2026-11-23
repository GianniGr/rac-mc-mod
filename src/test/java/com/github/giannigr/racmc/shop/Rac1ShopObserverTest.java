package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.client.shop.Rac1ShopObserverInbox;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopMotion.Phase.*;

class Rac1ShopObserverTest {
    @Test void physicalClockClosesOnlyAfterLastAggregateViewerAndQueuesReentry() {
        var clock=new Rac1ShopMotion();
        assertEquals(UNFOLD,clock.update(true,0).phase());
        assertEquals(OPEN,clock.update(true,40).phase());
        assertEquals(OPEN,clock.update(true,41).phase()); // A leaves; B remains
        assertEquals(POWER_OFF,clock.update(false,60).phase());
        var fold=clock.update(false,68); assertEquals(FOLD,fold.phase());
        assertEquals(FOLD,clock.update(true,70).phase()); // C enters: finish the original fold
        assertEquals(UNFOLD,clock.update(true,108).phase());
        assertEquals(OPEN,clock.update(true,148).phase());
        clock.update(false,150);
        assertEquals(CLOSED,clock.update(false,198).phase());
        assertEquals(UNFOLD,clock.update(true,200).phase());
    }
    @Test void lateObserverSamplesElapsedPhysicalPoseRatherThanRestartingAnimation() {
        var frame=Rac1ShopMotion.display(UNFOLD,100,112);
        assertEquals(.2f,frame.seconds(),1e-6); assertEquals(UNFOLD,frame.phase());
        assertEquals(OPEN,Rac1ShopMotion.display(UNFOLD,100,140).phase());
        assertEquals(FOLD,Rac1ShopMotion.display(POWER_OFF,100,110).phase());
        assertEquals(CLOSED,Rac1ShopMotion.display(FOLD,100,140).phase());
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopMotion.display(FOLD,100,Double.NaN));
    }
    @Test void wireRoundTripsBoundedPublicFactsOnlyAndRejectsInvalidTimesAndPhases() {
        var state=state(UUID.randomUUID(),1);
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            Rac1ShopObserverNetwork.State.CODEC.encode(b,state);
            assertTrue(b.readableBytes()<256);
            assertEquals(state,Rac1ShopObserverNetwork.State.CODEC.decode(b));
            b.clear();
            var longest=new Rac1ShopObserverNetwork.State(ResourceLocation.parse("a:"+"b".repeat(126)),BlockPos.ZERO,
                    UUID.randomUUID(),1,OPEN,0,1);
            Rac1ShopObserverNetwork.State.CODEC.encode(b,longest);
            assertTrue(b.readableBytes()<256,"Longest valid dimension remains bounded");
            assertEquals(longest,Rac1ShopObserverNetwork.State.CODEC.decode(b));
            b.clear(); Rac1ShopObserverNetwork.State.CODEC.encode(b,state);
            // dimension string + BlockPos + UUID + revision precede the phase byte
            b.readerIndex(0); b.readUtf(128); b.skipBytes(8+16+8); b.setByte(b.readerIndex(),255); b.readerIndex(0);
            assertThrows(IllegalArgumentException.class,() -> Rac1ShopObserverNetwork.State.CODEC.decode(b));
        } finally { b.release(); }
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopObserverNetwork.State(state.dimension(),state.position(),
                state.incarnation(),0,OPEN,0,1));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopObserverNetwork.State(state.dimension(),state.position(),
                state.incarnation(),1,OPEN,4,1));
    }
    @Test void publicSchemaCannotGrowPrivateTransactionFieldsSilently() {
        assertEquals(java.util.List.of("dimension","position","incarnation","revision","phase","startFrame","serverTick"),
                java.util.Arrays.stream(Rac1ShopObserverNetwork.State.class.getRecordComponents()).map(c -> c.getName()).toList());
    }
    @Test void staleReplayForeignIncarnationAndUnloadedInstancesCannotDriveReplacement() {
        var inbox=new Rac1ShopObserverInbox<BlockPos>(); Object first=new Object(),second=new Object();
        var a=state(UUID.randomUUID(),1); var replacement=state(UUID.randomUUID(),3);
        assertTrue(inbox.receive(a.position(),first,a));
        assertFalse(inbox.receive(a.position(),first,a));
        inbox.unload(a.position()); assertNull(inbox.state(a.position(),first));
        assertTrue(inbox.receive(a.position(),second,a),"A late snapshot may bind a new chunk instance");
        assertTrue(inbox.receive(a.position(),second,replacement));
        assertFalse(inbox.receive(a.position(),second,a),"Retired incarnation cannot replace newer public state");
        assertFalse(inbox.receive(a.position(),second,state(a.incarnation(),3)));
        assertNull(inbox.state(a.position(),first));
        inbox.clear(); assertNull(inbox.state(a.position(),second));
    }
    private static Rac1ShopObserverNetwork.State state(UUID id,long revision) {
        return new Rac1ShopObserverNetwork.State(ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO,id,revision,OPEN,0,100);
    }
}
