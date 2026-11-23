package com.github.giannigr.racmc.item;

import java.util.List;
import net.neoforged.neoforge.registries.DeferredItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RacMcCreativeWeaponRowsTest {
    @Test void actualRegisteredWeaponAmmoPairsAppearOnceInOrder() {
        var rows = RacMcCreativeTabs.weaponRows();
        assertEquals(List.of("racmc:blaster", "racmc:blaster_ammo", "racmc:bomb_glove", "racmc:bomb_glove_ammo"),
                rows.stream().map(DeferredItem::getId).map(Object::toString).toList());
        assertSame(RacMcItems.BOMB_GLOVE, rows.get(2));
        assertSame(RacMcItems.BOMB_GLOVE_AMMO, rows.get(3));
        assertEquals(rows.size(), rows.stream().map(DeferredItem::getId).distinct().count());
    }
}
