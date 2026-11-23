package com.github.giannigr.racmc.sound;

import java.util.Arrays;
import java.util.List;

/** Stable semantic IDs, never bank indices on the network. Level 1 is the curated reference profile. */
public enum Rac1AudioEvent {
    CRATE_BREAK("crate.break", 500, 0),
    HEALTH_CRATE_BREAK("crate.health.break", 501, 0),
    AMMO_CRATE_BREAK("crate.ammo.break", 511, 0),
    TNT_BLAST("crate.tnt.blast", 505, 0),
    TNT_BEEP("crate.tnt.beep", 505, 1),
    BOLT_ATTRACTION("bolt.attraction.start", 13, 0),
    HEXNUT_ATTRACTION("bolt.hexnut.attraction.start", 14, 0),
    STARNUT_ATTRACTION("bolt.starnut.attraction.start", 15, 0),
    CIRCLENUT_ATTRACTION("bolt.circlenut.attraction.start", 16, 0),
    NANOTECH_FIRST_ARRIVAL("nanotech.first_arrival", 501, 1),
    BLASTER_FIRE_LOOP("blaster.fire_loop", 168, 1),
    BLASTER_AMMO_PICKUP("blaster.ammo.pickup", 213, 0),
    BLASTER_EQUIP_START("blaster.equip.start", 168, 2),
    BLASTER_EQUIP_FINISH("blaster.equip.finish", 168, 3),
    // Appended: Rac1AudioSignal encodes enum ordinals. Same original collection cue as Blaster ammo.
    BOMB_GLOVE_AMMO_PICKUP("bomb_glove.ammo.pickup", 213, 0),
    BOMB_GLOVE_DRY_BURST("bomb_glove.dry_burst", 121, 0);

    static final List<Rac1AudioEvent> IMPORTED = Arrays.stream(values())
            .filter(Rac1AudioEvent::importsSample).toList();

    private final String id;
    private final int classId, slot;
    Rac1AudioEvent(String id, int classId, int slot) { this.id = id; this.classId = classId; this.slot = slot; }
    public String id() { return id; }
    public int classId() { return classId; }
    public int slot() { return slot; }
    public int level() { return 1; }
    public boolean bolt() { return classId >= 13 && classId <= 16; }
    public boolean ammoPickup() {
        return this == BLASTER_AMMO_PICKUP || this == BOMB_GLOVE_AMMO_PICKUP;
    }

    /** Physical entries in the persistent RACS sidecar. The dry bomb cue is memory-only. */
    public boolean importsSample() { return sampleSource() == this && this != BOMB_GLOVE_DRY_BURST; }

    Rac1AudioEvent sampleSource() {
        return this == BOMB_GLOVE_AMMO_PICKUP ? BLASTER_AMMO_PICKUP : this;
    }
    public static Rac1AudioEvent attraction(int value) {
        return switch (value) {
            case 5 -> HEXNUT_ATTRACTION;
            case 20 -> STARNUT_ATTRACTION;
            case 50 -> CIRCLENUT_ATTRACTION;
            default -> BOLT_ATTRACTION;
        };
    }
    public static Rac1AudioEvent crateBreak(int classId) {
        return switch (classId) {
            case 500, 506 -> CRATE_BREAK;
            case 501 -> HEALTH_CRATE_BREAK;
            case 511 -> AMMO_CRATE_BREAK;
            case 505 -> TNT_BLAST;
            default -> null;
        };
    }
}
