package com.github.giannigr.racmc.item.weapon;

import net.minecraft.util.Mth;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/** Final vanilla arm after the mod's single-handed aim: HumanoidModel attack, crouch, bob,
 * then PlayerModel's hand-only slim offset. No damage/movement/cadence or animation tuning.
 */
public final class RangedArmPose {
    public record Arm(Vector pivot, Vector degrees) {}
    private RangedArmPose() {}

    public static Arm resolve(float yawRadians, float pitchRadians, boolean left, boolean attackLeft,
                              float attack, float age, boolean crouch, boolean slim) {
        if (!Float.isFinite(yawRadians) || !Float.isFinite(pitchRadians) || !Float.isFinite(attack)
                || !Float.isFinite(age) || attack < 0 || attack > 1)
            throw new IllegalArgumentException("Invalid resolved arm inputs");
        float x = pitchRadians - (float) (Math.PI / 2), y = yawRadians, z = 0;
        float px = left ? 5 : -5, pz = 0;
        if (attack > 0) {
            float body = Mth.sin(Mth.sqrt(attack) * (float) (Math.PI * 2)) * .2f;
            if (attackLeft) body = -body;
            px = (left ? 1 : -1) * Mth.cos(body) * 5;
            pz = (left ? -1 : 1) * Mth.sin(body) * 5;
            y += body;
            if (left) x += body;
            if (left == attackLeft) {
                float eased = 1 - attack;
                eased *= eased;
                eased *= eased;
                float arc = Mth.sin((1 - eased) * (float) Math.PI);
                float head = Mth.sin(attack * (float) Math.PI) * -(pitchRadians - .7f) * .75f;
                x -= arc * 1.2f + head;
                y += body * 2;
                z += Mth.sin(attack * (float) Math.PI) * -.4f;
            }
        }
        if (crouch) x += .4f;
        float sign = left ? -1 : 1;
        x += sign * Mth.sin(age * .067f) * .05f;
        z += sign * (Mth.cos(age * .09f) * .05f + .05f);
        if (slim) px += left ? -.5f : .5f;
        return new Arm(new Vector(px, crouch ? 5.2f : 2, pz),
                new Vector(Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z)));
    }

    /** Vanilla attack's pitch-dependent term, for the narrowly scoped custom-aim bridge. */
    public static float attackHeadTerm(float attack, float headPitchRadians) {
        return Mth.sin(attack * (float) Math.PI) * -(headPitchRadians - .7f) * .75f;
    }
}
