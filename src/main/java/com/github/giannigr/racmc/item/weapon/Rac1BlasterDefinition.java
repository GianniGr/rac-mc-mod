package com.github.giannigr.racmc.item.weapon;

import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/** Blaster-only content choices. In particular ordinary arrows are a PoC ammo adaptation. */
public final class Rac1BlasterDefinition {
    public static final WeaponDefinition INSTANCE = new WeaponDefinition(
            "racmc:blaster", "rac1:level1/class168", new Vector(0, 0, 0),
            new Vector(0, 0, 1), new Vector(1, 0, 0),
            new Vector(.2305, -.0300, .6335), 1,
            new Vector(180, 0, -90), new Vector(180, 0, 90), 1, 4, 2,
            "racmc:blaster_poc_ordinary_arrow", "racmc:blaster_poc_arrow", WeaponDefinition.FireMode.HELD,
            new WeaponDefinition.Presentation(new Vector(0, 180, 90), new Vector(0, 0, 0), .55),
            new WeaponDefinition.Presentation(new Vector(0, 60, 90), new Vector(-4.14 / 16, -3.03 / 16, -9.0 / 16), 1.1));
    private Rac1BlasterDefinition() {}
}
