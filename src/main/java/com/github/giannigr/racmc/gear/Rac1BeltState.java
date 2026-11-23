package com.github.giannigr.racmc.gear;

/** Self-only view: no items, components, target UUIDs or effect parameters are sent. */
public record Rac1BeltState(long epoch, Slot boltGrabber) {
    public Rac1BeltState {
        if (epoch <= 0 || boltGrabber == null)
            throw new IllegalArgumentException("Invalid Belt state");
    }

    public Slot slot(Rac1BeltGadget gadget) {
        return switch (gadget) {
            case BOLT_GRABBER -> boltGrabber;
        };
    }

    public enum Availability {
        MISSING(0), READY(1), EQUIPPED(2), RETURNING(3);

        private final int wireId;
        Availability(int wireId) { this.wireId = wireId; }
        int wireId() { return wireId; }

        static Availability fromWire(int id) {
            return switch (id) {
                case 0 -> MISSING;
                case 1 -> READY;
                case 2 -> EQUIPPED;
                case 3 -> RETURNING;
                default -> throw new IllegalArgumentException("Invalid Belt slot availability");
            };
        }
    }

    public record Slot(long revision, Availability availability) {
        public Slot {
            if (revision <= 0 || availability == null)
                throw new IllegalArgumentException("Invalid Belt slot");
        }

        public boolean canRequest(boolean equip) {
            return equip ? availability == Availability.READY : availability == Availability.EQUIPPED;
        }
    }
}
