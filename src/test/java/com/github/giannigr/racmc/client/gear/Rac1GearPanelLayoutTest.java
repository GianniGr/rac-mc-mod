package com.github.giannigr.racmc.client.gear;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import com.github.giannigr.racmc.gear.Rac1GearState;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class Rac1GearPanelLayoutTest {
    private static final int LINE = 9;
    private static final Rac1ClankEquipment UNWORN =
            new Rac1ClankEquipment(true, true, false, false, Rac1ClankEquipment.Module.HELI);
    private static final Rac1ClankEquipment WORN =
            new Rac1ClankEquipment(true, true, false, true, Rac1ClankEquipment.Module.HELI);

    @Test
    void compactViewportHasRoomForWrappedBackAndTypedBeltBesideItsPlaceholder() {
        var layout = Rac1GearPanelLayout.of(320, 240, LINE, 4, 3, 1, 1, 1);
        assertTrue(layout.panel().x() >= 0 && layout.panel().right() <= 320);
        assertTrue(layout.panel().y() >= Rac1GearPanelLayout.TAB_HEIGHT);
        assertTrue(layout.panel().bottom() <= 240);
        assertEquals(layout.panel().x(), 320 - layout.panel().right());
        assertSectionsDoNotOverlap(layout, 4);
    }

    @Test
    void longerTranslatedLabelsGetAdditionalLinesWithoutCrossingTheAction() {
        var base = Rac1GearPanelLayout.of(374, 280, LINE, 3, 2, 1, 1, 1);
        var wrapped = Rac1GearPanelLayout.of(374, 280, LINE, 4, 3, 2, 2, 2);
        assertEquals(LINE, wrapped.back().height() - base.back().height());
        assertEquals(2 * LINE, wrapped.aerial().height() - base.aerial().height());
        assertEquals(LINE + 2, wrapped.beltFirst().height() - base.beltFirst().height());
        assertTrue(wrapped.panel().bottom() <= 280);
        assertSectionsDoNotOverlap(wrapped, 4);
    }

    @Test
    void onlyCurrentBackStateDrivesAvailabilityAndStarterHeliStatus() {
        assertFalse(Rac1GearInventory.backEnabled(null, true));
        assertFalse(Rac1GearInventory.backEnabled(state(Rac1ClankEquipment.EMPTY, true, false), true));
        assertFalse(Rac1GearInventory.backEnabled(state(UNWORN, false, false), true));
        assertFalse(Rac1GearInventory.backEnabled(state(UNWORN, true, true), true));
        assertFalse(Rac1GearInventory.backEnabled(state(WORN, false, false), false));
        assertTrue(Rac1GearInventory.backEnabled(state(UNWORN, true, false), true));
        var worn = state(WORN, false, false);
        assertTrue(Rac1GearInventory.backEnabled(worn, true)); // Back keeps working without a loose item.
        var wornText = (TranslatableContents) Rac1GearInventory.backStatus(worn).getContents();
        assertEquals("screen.racmc.gear.worn", wornText.getKey()); // Not the Aerial reservation label.
    }

    @Test
    void fixtureUsesSelectedPackNameAndOffersDismissEvenWithoutProgression() {
        for (var module : new Rac1ClankEquipment.Module[]{
                Rac1ClankEquipment.Module.HELI, Rac1ClankEquipment.Module.THRUSTER}) {
            var state = new Rac1GearState(1, 2, Rac1ClankEquipment.EMPTY, false, false, false, module);
            assertFalse(state.clankUnlocked());
            assertTrue(state.wearingClank());
            assertTrue(Rac1GearInventory.backEnabled(state, true));
            assertFalse(Rac1GearInventory.backEnabled(state, false));
            var text = (TranslatableContents) Rac1GearInventory.backStatus(state).getContents();
            var packName = (net.minecraft.network.chat.Component) text.getArgs()[0];
            assertEquals(module == Rac1ClankEquipment.Module.HELI ? "item.racmc.clank_heli_pack"
                    : "item.racmc.clank_thruster_pack", ((TranslatableContents) packName.getContents()).getKey());
        }
    }

    private static void assertSectionsDoNotOverlap(Rac1GearPanelLayout layout, int backLines) {
        int statusEnd = layout.back().y() + Rac1GearPanelLayout.BACK_STATUS_OFFSET + backLines * LINE;
        assertTrue(statusEnd < layout.backButton().y());
        assertTrue(layout.backButton().bottom() < layout.back().bottom());
        assertTrue(layout.back().bottom() < layout.aerial().y());
        assertTrue(layout.aerial().right() < layout.hydro().x());
        assertEquals(layout.aerial().bottom(), layout.hydro().bottom());
        assertTrue(layout.hydro().bottom() < layout.beltTitleY());
        assertTrue(layout.beltTitleY() < layout.beltFirst().y());
        assertTrue(layout.beltFirst().right() < layout.beltSecond().x());
        assertTrue(layout.beltButton().right() <= layout.beltFirst().right());
        assertTrue(layout.beltButton().bottom() < layout.beltFirst().bottom());
        assertEquals(layout.beltFirst().bottom(), layout.beltSecond().bottom());
        assertTrue(layout.beltSecond().bottom() < layout.panel().bottom());
    }

    private static Rac1GearState state(Rac1ClankEquipment gear, boolean carried, boolean returning) {
        return new Rac1GearState(1, 1, gear, carried, false, returning);
    }
}
