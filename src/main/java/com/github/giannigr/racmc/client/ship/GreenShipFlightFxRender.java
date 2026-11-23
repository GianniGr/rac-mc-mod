package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient.ShipArt;
import com.github.giannigr.racmc.ship.Rac1TravelShip;
import com.github.giannigr.racmc.ship.ShipThrust;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

/** Ship-specific client renderer. Only replicated engine facts supply effects. */
public final class GreenShipFlightFxRender {
    private static final int MAX_TRACKED = 128;
    private static final Map<Rac1TravelShip, View> VIEWS = new WeakHashMap<>();

    private GreenShipFlightFxRender() {}

    private static final class View {
        final GreenShipFlightVisual clock = new GreenShipFlightVisual();
        final GreenShipExhaust exhaust = new GreenShipExhaust();
    }

    /** Draw only with the verified hull, already in ship-local axes. Not part of the underside pass. */
    public static void drawRear(Rac1TravelShip ship, PoseStack pose, MultiBufferSource buffers) {
        var thrust = ship.engineThrust();
        if (thrust == ShipThrust.OFF) return;
        GreenShipFlamePass.draw(Rac1EffectClient.shipMaterial(ShipArt.FLAME), pose.last(), buffers,
                GreenShipFxGeometry.rear(thrust, ship.level().getGameTime()));
    }

    public static int draw(Rac1TravelShip ship, float yaw,
                           float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (ship.level() != Minecraft.getInstance().level) {
            return light;
        }
        View view = VIEWS.get(ship);
        if (view == null) {
            if (VIEWS.size() >= MAX_TRACKED) {
                return light;
            }
            view = new View();
            VIEWS.put(ship, view);
        }
        long tick = ship.level().getGameTime();
        var flameType = Rac1EffectClient.shipMaterial(ShipArt.FLAME);
        // Access is only a veto. PILOTING alone can never give the clock an effect.
        boolean accessClear = ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING;
        var frame = view.clock.observe(ship.engineState(), ship.engineBeat(), accessClear,
                flameType != null, tick, partial);
        var exhaustType = Rac1EffectClient.shipMaterial(ShipArt.EXHAUST);
        if (!frame.safeExhaust() || exhaustType == null) {
            view.exhaust.clear();
        } else {
            view.exhaust.emit(frame.newExhaustBursts(), tick, ship.position(), ship.getYRot(),
                    ship.getUUID().getLeastSignificantBits());
            var puffs = view.exhaust.sample(tick, partial);
            if (!puffs.isEmpty()) {
                var out = buffers.getBuffer(exhaustType);
                var shipOrigin = ship.getPosition(partial);
                var camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
                for (var puff : puffs) {
                    var center = puff.position().subtract(shipOrigin);
                    for (int corner = 0; corner < 4; corner++) {
                        float u = corner == 1 || corner == 2 ? 1 : 0;
                        float v = corner >= 2 ? 1 : 0;
                        var edge = new Vector3f((u - .5f) * puff.size(), (.5f - v) * puff.size(), 0)
                                .rotateZ(puff.rotation()).rotate(camera);
                        out.addVertex(pose.last(), (float) center.x + edge.x,
                                        (float) center.y + edge.y, (float) center.z + edge.z)
                                .setUv(u, v).setColor(puff.argb());
                    }
                }
            }
        }
        var sample = frame.sample();
        if (sample == null) {
            return light;
        }
        if (sample.flameQuads() != 0 && flameType != null) {
            pose.pushPose();
            try {
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                GreenShipFlamePass.draw(flameType, pose.last(), buffers,
                        GreenShipFxGeometry.flames(sample, ship.getUUID().hashCode() ^ tick));
            } finally {
                pose.popPose();
            }
        }
        // Neutral class-531 source pulse/ramp adapted to block light, without changing sky light.
        return LightTexture.pack(Math.max(LightTexture.block(light), (sample.glow() * 15 + 127) / 255),
                LightTexture.sky(light));
    }
}
