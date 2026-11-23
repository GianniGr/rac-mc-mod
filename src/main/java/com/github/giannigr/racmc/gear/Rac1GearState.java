package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;

/** Complete private snapshot, ordered by lifecycle epoch and then canonical record revision. */
public record Rac1GearState(long epoch, long revision, Rac1ClankEquipment back,
                            boolean clankInInventory, boolean rewardPending, boolean returnPending, Module fixture) {
    public Rac1GearState(long epoch, long revision, Rac1ClankEquipment back,
                         boolean clankInInventory, boolean rewardPending, boolean returnPending) {
        this(epoch, revision, back, clankInInventory, rewardPending, returnPending, Module.NONE);
    }

    public Rac1GearState {
        if (epoch <= 0 || revision < 0 || back == null || fixture == null
                || (returnPending && back.worn()) || ((rewardPending || returnPending) && !back.acquired()))
            throw new IllegalArgumentException("Invalid Gear snapshot");
    }

    public boolean clankUnlocked() {
        return back.acquired();
    }

    public boolean wearingClank() {
        return fixture != Module.NONE || back.worn();
    }

    public boolean allowsBackRequest(boolean worn) {
        if (worn) return !wearingClank() && clankUnlocked() && clankInInventory && !returnPending;
        return wearingClank();
    }
}
