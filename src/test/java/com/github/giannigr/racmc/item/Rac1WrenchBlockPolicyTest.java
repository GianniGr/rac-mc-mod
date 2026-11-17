package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.item.Rac1WrenchBlockPolicy.Action.*;

import org.junit.jupiter.api.Test;

class Rac1WrenchBlockPolicyTest {
    @Test
    void onlyTheThreeOrdinaryOriginalIdsOptIn() {
        for (String id : new String[] { "bolt_crate", "health_crate", "ammo_crate" }) {
            assertEquals(INSTANT_CRATE, Rac1WrenchBlockPolicy.action("racmc:" + id, false));
        }
        for (String id : new String[] { "minecraft:chest", "minecraft:bedrock", "minecraft:stone",
                "other:bolt_crate", "racmc:future_crate", "racmc:bolt_crate_extra" }) {
            assertEquals(NORMAL, Rac1WrenchBlockPolicy.action(id, false));
        }
    }

    @Test
    void explosiveIsNeverInstantRemovalOrDetonationWithoutAdapter() {
        assertEquals(EXPLOSIVE_ADAPTER_REQUIRED,
                Rac1WrenchBlockPolicy.action("racmc:explosive_crate", false));
    }

    @Test
    void protectedTagOnlyRestrictsAndCannotExpandEligibility() {
        for (String id : new String[] { "racmc:bolt_crate", "racmc:health_crate",
                "racmc:ammo_crate", "racmc:explosive_crate", "minecraft:stone" }) {
            assertEquals(PROTECTED, Rac1WrenchBlockPolicy.action(id, true));
        }
    }

    @Test
    void vendorHardDenyDoesNotDependOnDatapackTagsOrGameMode() {
        assertEquals(PROTECTED, Rac1WrenchBlockPolicy.action("racmc:gadgetron_vendor", false));
        assertEquals(PROTECTED, Rac1WrenchBlockPolicy.action("racmc:gadgetron_vendor", true));
        // No mode parameter exists: creative and survival share the same hard deny.
    }
}
