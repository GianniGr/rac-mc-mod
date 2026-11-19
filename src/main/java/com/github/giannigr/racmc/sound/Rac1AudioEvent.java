package com.github.giannigr.racmc.sound;

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
    BLASTER_FIRE_LOOP("blaster.fire_loop", 168, 1);

    private final String id;
    private final int classId, slot;
    Rac1AudioEvent(String id, int classId, int slot) { this.id = id; this.classId = classId; this.slot = slot; }
    public String id() { return id; }
    public int classId() { return classId; }
    public int slot() { return slot; }
    public int level() { return 1; }
    public boolean bolt() { return classId >= 13 && classId <= 16; }
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
