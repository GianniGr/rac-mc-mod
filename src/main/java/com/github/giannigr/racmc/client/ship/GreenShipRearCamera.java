package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.ship.Rac1TravelShip;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;

/** Rear F5 boom around the native interpolated pilot eye. No camera transform override. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class GreenShipRearCamera {
    // Measured class-531 bounds at the fixed ratio, rounded outward.
    private static final AABB HULL = new AABB(-2.884, -.001, -4.279, 2.884, 3.995, 5.007);
    private static final double CAMERA_MARGIN = .12;

    private GreenShipRearCamera() {}

    @SubscribeEvent
    public static void distance(CalculateDetachedCameraDistanceEvent event) {
        var camera = event.getCamera();
        var viewer = camera.getEntity();
        var level = Minecraft.getInstance().level;
        if (event.isCameraFlipped() || viewer == null || viewer.level() != level
                || !(viewer.getVehicle() instanceof Rac1TravelShip ship) || ship.isRemoved()
                || !ship.hasRefitCockpit() || ship.getFirstPassenger() != viewer) return;

        float partial = camera.getPartialTickTime();
        float yaw = Mth.rotLerp(partial, ship.yRotO, ship.getYRot());
        var eye = camera.getPosition();
        var away = new Vec3(camera.getLookVector()).reverse().normalize();
        if (!Double.isFinite(away.lengthSqr()) || away.lengthSqr() < .99) return;
        var localEye = eye.subtract(ship.getPosition(partial)).yRot(yaw * Mth.DEG_TO_RAD);
        var localAway = away.yRot(yaw * Mth.DEG_TO_RAD);
        float scale = event.getEntityScalingFactor();
        if (scale <= 0 || !Float.isFinite(scale)) return;
        double desired = framingDistance(localEye, localAway);
        double distance = loadedDistance(eye, away, desired, box -> {
            if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()) return false;
            for (int x = SectionPos.blockToSectionCoord(Mth.floor(box.minX));
                 x <= SectionPos.blockToSectionCoord(Mth.floor(box.maxX)); x++) {
                for (int z = SectionPos.blockToSectionCoord(Mth.floor(box.minZ));
                     z <= SectionPos.blockToSectionCoord(Mth.floor(box.maxZ)); z++) {
                    if (level.getChunkSource().getChunkNow(x, z) == null) return false;
                }
            }
            return true;
        });
        // Shorten against actual colliders as well as the vanilla VISUAL near-plane clip,
        // which runs after this event. Never move the eye through a wall or to an unloaded chunk.
        for (int corner = 0; corner < 8 && distance > 0; corner++) {
            var start = eye.add((corner & 1) == 0 ? -.1 : .1, (corner & 2) == 0 ? -.1 : .1,
                    (corner & 4) == 0 ? -.1 : .1);
            var hit = level.clip(new ClipContext(start, start.add(away.scale(distance)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
            if (hit.getType() != HitResult.Type.MISS) {
                distance = Math.min(distance, Math.max(0, hit.getLocation().distanceTo(start) - .10));
            }
        }
        event.setDistance((float) (distance / scale));
    }

    static double framingDistance(Vec3 eye, Vec3 away) {
        var extreme = new Vec3(away.x >= 0 ? HULL.maxX : HULL.minX,
                away.y >= 0 ? HULL.maxY : HULL.minY, away.z >= 0 ? HULL.maxZ : HULL.minZ);
        return Math.clamp(extreme.subtract(eye).dot(away) + 3.35, 6.25, 14);
    }

    static double loadedDistance(Vec3 eye, Vec3 away, double desired, Predicate<AABB> loaded) {
        double confirmed = 0;
        while (confirmed < desired) {
            double next = Math.min(confirmed + .25, desired);
            var segment = new AABB(eye.add(away.scale(confirmed)), eye.add(away.scale(next))).inflate(CAMERA_MARGIN);
            if (!loaded.test(segment)) break;
            confirmed = next;
        }
        return confirmed;
    }
}
