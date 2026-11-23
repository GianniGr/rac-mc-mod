package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.asset.pda.Rac1PdaRemoteAudio.Role;
import com.github.giannigr.racmc.shop.Rac1ShopPurchases.Status;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.client.pda.Rac1PdaClientFlow.Mode;

class Rac1PdaOriginalCuePolicyTest {
    @Test void genuineInitialBuyDenialOnlyNotCapReachOrRequoteOrLateFailure() {
        for (var status:new Status[]{Status.INSUFFICIENT,Status.STOCK_CAP}) {
            assertEquals(Role.DENIED,Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_QUOTE,status,true,false));
            assertNull(Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_QUOTE,status,false,false));
            assertNull(Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_BUY,status,true,false));
            assertNull(Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_QUOTE,status,true,true));
        }
    }
    @Test void successOnlyAfterAcceptedBuyReceiptAndNoCueOnReplay() {
        assertEquals(Role.PURCHASE,Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_BUY,Status.PURCHASED,false,false));
        assertEquals(Role.PURCHASE,Rac1PdaOriginalCuePolicy.receipt(Mode.UNCERTAIN,Status.PURCHASED,false,false));
        assertNull(Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_QUOTE,Status.PURCHASED,true,false));
        assertNull(Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_BUY,Status.PURCHASED,false,true));
    }
    @Test void noBorrowedPickupSpeechMotionOrOtherFailureDispatch() {
        for (var status:Status.values()) if (status!=Status.INSUFFICIENT && status!=Status.STOCK_CAP && status!=Status.PURCHASED)
            assertNull(Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_QUOTE,status,true,false));
        assertNull(Rac1PdaOriginalCuePolicy.receipt(Mode.WAIT_BUY,Status.QUOTED,false,false));
    }
}
