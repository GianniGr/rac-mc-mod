package com.github.giannigr.racmc.block;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.ModSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class Rac1CrateBlastPolicyTest {
    private static AABB box(double nearestX) {
        return new AABB(nearestX, -0.5, -0.3, nearestX + 0.6, 1.3, 0.3);
    }

    @Test
    void playerBoundaryIncludesExactlyTwoButNotJustOutside() {
        assertTrue(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(1.999999), true));
        assertTrue(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(2), true));
        assertFalse(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(2.000001), true));
        assertEquals(2, Rac1CrateBlastPolicy.distance(Vec3.ZERO, box(2)));
    }

    @Test
    void mobBoundaryIncludesExactlyThreeButNotJustOutside() {
        assertTrue(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(2.999999), false));
        assertTrue(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(3), false));
        assertFalse(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(3.000001), false));
        assertFalse(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(3), true));
    }

    @Test
    void boundaryUsesShapeRatherThanEntityOriginOrCubeBroadphase() {
        assertTrue(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(1.9), true));
        assertFalse(Rac1CrateBlastPolicy.inRange(Vec3.ZERO,
                new AABB(2, 2, 2, 3, 3, 3), false));
        assertEquals(0, Rac1CrateBlastPolicy.distance(Vec3.ZERO,
                new AABB(-1, -1, -1, 1, 1, 1)));
    }

    @Test
    void flatFiveHpHasOnlyExposureScalingAndNoOccludedResidualHit() {
        assertEquals(5, Rac1CrateBlastPolicy.damage(1));
        assertEquals(3.75, Rac1CrateBlastPolicy.damage(0.75));
        assertEquals(2.5, Rac1CrateBlastPolicy.damage(0.5));
        assertEquals(1.25, Rac1CrateBlastPolicy.damage(0.25));
        assertEquals(0, Rac1CrateBlastPolicy.damage(0));
        // Damage has no distance input: eligible near/boundary targets share this exact raw hit.
        for (double distance : new double[]{0, 0.1, 1, 2}) {
            assertTrue(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(distance), true));
            assertEquals(5, Rac1CrateBlastPolicy.damage(1));
        }
    }

    @Test
    void knockbackUsesVanillaTntEightBlockOriginEnvelopeIndependentOfDamage() {
        assertEquals(4, Rac1CrateBlastPolicy.TNT_KNOCKBACK_POWER);
        assertEquals(8, Rac1CrateBlastPolicy.KNOCKBACK_RADIUS);
        assertEquals(1, Rac1CrateBlastPolicy.knockback(0, 1));
        assertEquals(0.875, Rac1CrateBlastPolicy.knockback(1, 1));
        assertEquals(0.75, Rac1CrateBlastPolicy.knockback(2, 1));
        assertEquals(0.625, Rac1CrateBlastPolicy.knockback(3, 1));
        assertEquals(0.5, Rac1CrateBlastPolicy.knockback(4, 1));
        assertEquals(0.25, Rac1CrateBlastPolicy.knockback(4, 0.5));
        assertEquals(0, Rac1CrateBlastPolicy.knockback(8, 1));
        assertEquals(0, Rac1CrateBlastPolicy.knockback(8.000001, 1));
        assertEquals(0, Rac1CrateBlastPolicy.knockback(0, 0));
        assertFalse(Rac1CrateBlastPolicy.inRange(Vec3.ZERO, box(4), false));
        assertTrue(Rac1CrateBlastPolicy.knockback(4, 1) > 0);
    }

    @Test
    void malformedExposureAndDistanceFailClosedOrClampToPhysicalRange() {
        assertEquals(5, Rac1CrateBlastPolicy.damage(2));
        assertEquals(0, Rac1CrateBlastPolicy.damage(-1));
        assertEquals(0, Rac1CrateBlastPolicy.damage(Double.NaN));
        assertEquals(0, Rac1CrateBlastPolicy.damage(Double.POSITIVE_INFINITY));
        assertEquals(0, Rac1CrateBlastPolicy.knockback(Double.NaN, 1));
        assertEquals(0, Rac1CrateBlastPolicy.knockback(-1, 1));
    }

    @Test
    void productionUsesNormalHurtAndKeepsItemsOutsideAllEffectSets() {
        String source = ModSource.of("com/github/giannigr/racmc/block/Rac1CrateBlast.java");
        assertTrue(source.contains("entity.hurt(level.damageSources().explosion(context), amount)"));
        assertTrue(source.contains("Explosion.getSeenPercent(center, entity)"));
        assertTrue(source.contains("if (amount <= 0)"));
        assertTrue(source.contains("entity instanceof ItemEntity"));
        assertTrue(source.contains("!(e instanceof ItemEntity)"));
        assertTrue(source.contains("entity instanceof Player || entity instanceof Mob"));
        assertTrue(source.contains("Attributes.EXPLOSION_KNOCKBACK_RESISTANCE"));
        assertTrue(source.contains("EventHooks.getExplosionKnockback"));
        assertFalse(source.contains("setHealth("));
        assertFalse(source.contains("DAMAGE_RADIUS"));
        assertFalse(source.contains("halfTntDamage"));
        assertTrue(source.contains("Set<LivingEntity> hurtTargets"));
        assertTrue(source.contains("Map<ServerPlayer, Vec3> playerPushes"));
        assertTrue(source.indexOf("&& damage(level") < source.indexOf("knockback(level"));
    }
}
