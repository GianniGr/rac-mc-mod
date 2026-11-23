package com.github.giannigr.racmc.loot;

import static com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class Rac1AmmoCrateRoutingTest {
    @Test void favorsOwnedUnderCapThenOwnedFullAndOnlyFallsBackWithoutOwnedStock() {
        var fullBlaster=new Rac1AmmoCrateRouting.Stock(BLASTER,200);
        var lowBombs=new Rac1AmmoCrateRouting.Stock(BOMB_GLOVE,39);
        var fullBombs=new Rac1AmmoCrateRouting.Stock(BOMB_GLOVE,41);
        assertEquals(BLASTER,Rac1AmmoCrateRouting.choose(List.of(),bound -> fail("Unexpected RNG")));
        assertEquals(BOMB_GLOVE,Rac1AmmoCrateRouting.choose(List.of(fullBlaster,lowBombs),bound -> fail("Unexpected RNG")));
        assertEquals(BOMB_GLOVE,Rac1AmmoCrateRouting.choose(List.of(fullBombs),bound -> fail("Unexpected RNG")));
        assertEquals(BLASTER,Rac1AmmoCrateRouting.choose(List.of(fullBlaster,fullBombs),bound -> { assertEquals(2,bound); return 0; }));
        assertEquals(BOMB_GLOVE,Rac1AmmoCrateRouting.choose(
                List.of(new Rac1AmmoCrateRouting.Stock(BLASTER,199),lowBombs),bound -> { assertEquals(2,bound); return 1; }));
    }

    @Test void bombStockUsesSameReviewedDrawButThreeRoundPickups() {
        int one=0,two=0;
        for (int r=0;r<BlasterAmmoCratePolicy.ROLL_BOUND;r++) {
            int units=BlasterAmmoCratePolicy.pickups(r)*BOMB_GLOVE.pickupUnits();
            if (units==3) one++;
            else if (units==6) two++;
            else fail("Unexpected draw: "+units);
        }
        assertEquals(4,one);
        assertEquals(1,two);
    }
}
