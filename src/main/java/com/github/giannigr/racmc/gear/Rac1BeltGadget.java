package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.item.RacMcItems;
import net.minecraft.world.item.ItemStack;

/** Active Belt slot types. IDs are stable on disk and wire. */
public enum Rac1BeltGadget {
    BOLT_GRABBER("bolt_grabber", 1);

    private final String id;
    private final int wireId;

    Rac1BeltGadget(String id, int wireId) {
        this.id = id;
        this.wireId = wireId;
    }

    public String id() { return id; }
    int wireId() { return wireId; }

    static Rac1BeltGadget fromWire(int id) {
        if (id == BOLT_GRABBER.wireId) return BOLT_GRABBER;
        throw new IllegalArgumentException("Unknown Belt gadget");
    }

    static Rac1BeltGadget fromId(String id) {
        if (BOLT_GRABBER.id.equals(id)) return BOLT_GRABBER;
        throw new IllegalArgumentException("Unknown persisted Belt gadget");
    }

    boolean matches(ItemStack stack) {
        return stack.getCount() == 1 && stack.is(switch (this) {
            case BOLT_GRABBER -> RacMcItems.BOLT_GRABBER.get();
        });
    }
}
