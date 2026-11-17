package com.github.giannigr.racmc.item;

import java.util.Set;

/** Positive registry-ID policy, deliberately independent of datapack allow tags. */
public final class Rac1WrenchBlockPolicy {
    public enum Action { NORMAL, INSTANT_CRATE, EXPLOSIVE_ADAPTER_REQUIRED, PROTECTED }

    private static final Set<String> ORDINARY = Set.of(
            "racmc:bolt_crate", "racmc:health_crate", "racmc:ammo_crate");

    private Rac1WrenchBlockPolicy() { }

    public static Action action(String registryId, boolean protectedTag) {
        // Approved vendor17773dc registry identity; no compile dependency on that branch.
        if ("racmc:gadgetron_vendor".equals(registryId) || protectedTag) {
            return Action.PROTECTED;
        }
        if ("racmc:explosive_crate".equals(registryId)) {
            return Action.EXPLOSIVE_ADAPTER_REQUIRED;
        }
        return ORDINARY.contains(registryId) ? Action.INSTANT_CRATE : Action.NORMAL;
    }
}
