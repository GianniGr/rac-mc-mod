package com.github.giannigr.racmc.clank;

/** Source predicates, NOT complete action admission (contacts/input/body/MC policies still gate). */
public final class Rac1ClankCapability {
    public enum Action { IDLE, GLIDE, LONG_JUMP, HIGH_JUMP }
    public record Context(boolean shown, boolean nativeHeroBody, int slotState, int selectedSourceId) {
        public Context {
            if (slotState < 0 || slotState > 3
                    || (selectedSourceId != 0 && selectedSourceId != 2
                    && selectedSourceId != 3 && selectedSourceId != 4))
                throw new IllegalArgumentException("Invalid original back state");
        }
        public int readyModule() { return slotState == 2 ? selectedSourceId : -1; }
    }
    private Rac1ClankCapability() {}

    public static boolean permits(Rac1ClankEquipment equipment, Context source, Action action) {
        if (action == Action.IDLE || !equipment.worn() || !source.shown()
                || !source.nativeHeroBody()) return false;
        // A context is transient server data, never permission to select an unowned upgrade.
        if (source.selectedSourceId() == 3 && (!equipment.thrusterOwned()
                || equipment.selected() != Rac1ClankEquipment.Module.THRUSTER)) return false;
        if (source.selectedSourceId() == 2
                && equipment.selected() != Rac1ClankEquipment.Module.HELI) return false;
        int ready = source.readyModule();
        if (action == Action.GLIDE)
            return equipment.heliOwned() && ready != 4;
        return (ready == 2 && equipment.heliOwned()) || (ready == 3 && equipment.thrusterOwned());
    }
}
