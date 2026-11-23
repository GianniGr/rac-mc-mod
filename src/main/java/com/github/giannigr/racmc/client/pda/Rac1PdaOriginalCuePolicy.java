package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.asset.pda.Rac1PdaRemoteAudio.Role;
import com.github.giannigr.racmc.shop.Rac1ShopPurchases.Status;

/** Called only after matching flow.receive accepted the owner/token/sequence/quote result. */
public final class Rac1PdaOriginalCuePolicy {
    private Rac1PdaOriginalCuePolicy() {}
    public static Role receipt(Rac1PdaClientFlow.Mode previous,Status outcome,boolean initialBuy,boolean replay) {
        if (replay) return null;
        if (outcome==Status.PURCHASED
                && (previous==Rac1PdaClientFlow.Mode.WAIT_BUY || previous==Rac1PdaClientFlow.Mode.UNCERTAIN))
            return Role.PURCHASE;
        if (initialBuy && previous==Rac1PdaClientFlow.Mode.WAIT_QUOTE
                && (outcome==Status.INSUFFICIENT || outcome==Status.STOCK_CAP)) return Role.DENIED;
        return null;
    }
}
