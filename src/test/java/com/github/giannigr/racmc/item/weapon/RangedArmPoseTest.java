package com.github.giannigr.racmc.item.weapon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RangedArmPoseTest {
    @Test void finalArmMatchesIndependentLiteralVanillaAttackCrouchBobAndSlimEquations() {
        // MC source pin6d1662dd...: HumanoidModel381..406,211..240; PlayerModel178..186.
        // Independent high-precision source arithmetic, not production helper calls.
        for (boolean left : new boolean[]{false, true})
            for (boolean attackingLeft : new boolean[]{false, true})
                for (boolean crouch : new boolean[]{false, true})
                    for (boolean slim : new boolean[]{false, true})
                        for (float attack : new float[]{0, .2f, .6f, 1}) {
                            double pitch = -.31, yaw = .63, age = 17.5;
                            double rx = pitch - Math.PI / 2, ry = yaw, rz = 0;
                            double px = left ? 5 : -5, pz = 0;
                            if (attack > 0) {
                                double body = Math.sin(Math.sqrt(attack) * 2 * Math.PI) * .2 * (attackingLeft ? -1 : 1);
                                px = (left ? 1 : -1) * Math.cos(body) * 5;
                                pz = (left ? -1 : 1) * Math.sin(body) * 5;
                                ry += body;
                                if (left) rx += body;
                                if (left == attackingLeft) {
                                    rx -= Math.sin((1 - Math.pow(1 - attack, 4)) * Math.PI) * 1.2
                                            + Math.sin(attack * Math.PI) * -(pitch - .7) * .75;
                                    ry += 2 * body;
                                    rz -= Math.sin(attack * Math.PI) * .4;
                                }
                            }
                            if (crouch) rx += .4;
                            rx += (left ? -1 : 1) * Math.sin(age * .067) * .05;
                            rz += (left ? -1 : 1) * (Math.cos(age * .09) * .05 + .05);
                            if (slim) px += left ? -.5 : .5;
                            var result = RangedArmPose.resolve((float) yaw, (float) pitch, left,
                                    attackingLeft, attack, (float) age, crouch, slim);
                            // Vanilla Mth uses a float lookup sine table, not exact Math.sin.
                            assertEquals(px, result.pivot().x(), .001);
                            assertEquals(pz, result.pivot().z(), .001);
                            assertEquals(crouch ? 5.2 : 2, result.pivot().y(), 1e-6);
                            assertEquals(Math.toDegrees(rx), result.degrees().x(), .015);
                            assertEquals(Math.toDegrees(ry), result.degrees().y(), .015);
                            assertEquals(Math.toDegrees(rz), result.degrees().z(), .015);
                        }
    }
    @Test void customAimCompensationRemovesOnlyVanillaReusedHeadPitchFromAttackTerm() {
        float attack = .37f, agreed = .21f, originalArm = agreed - (float) (Math.PI / 2);
        float expected = originalArm - RangedArmPose.attackHeadTerm(attack, agreed);
        for (float reusedHead : new float[]{-1.4f, -.7853982f, 0, .9f}) {
            float beforeVanilla = originalArm + RangedArmPose.attackHeadTerm(attack, reusedHead)
                    - RangedArmPose.attackHeadTerm(attack, agreed);
            assertEquals(expected, beforeVanilla - RangedArmPose.attackHeadTerm(attack, reusedHead), 2e-7);
        }
    }
    @Test void actualSkinFallbackOffsetIsOnlyThePinnedHandPivotDifferenceForBothHands() {
        for (boolean left : new boolean[]{false, true}) {
            var pose = HandPoseSnapshot.standing(new WeaponDefinition.Vector(0, 0, 0), 37, 29, -18,
                    left, false, 1);
            var def = Rac1BlasterDefinition.INSTANCE;
            var shift = new WeaponDefinition.Vector((left ? -.5 : .5) / 16, 0, 0);
            var local = HandTransform.localForModelDisplacement(def, pose, shift);
            var origin = HandTransform.modelPoint(def, pose, def.muzzle());
            var corrected = HandTransform.modelPoint(def, pose, new WeaponDefinition.Vector(
                    def.muzzle().x() + local.x(), def.muzzle().y() + local.y(), def.muzzle().z() + local.z()));
            assertEquals(shift.x(), corrected.x() - origin.x(), 1e-12);
            assertEquals(0, corrected.y() - origin.y(), 1e-12);
            assertEquals(0, corrected.z() - origin.z(), 1e-12);
        }
    }
}
