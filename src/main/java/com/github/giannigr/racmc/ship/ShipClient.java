package com.github.giannigr.racmc.ship;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.clank.Rac1HydroHeldKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class ShipClient {
    private static final KeyMapping BOOST = ShipPilotInput.boostMapping(() -> {
        var mc = Minecraft.getInstance();
        return ShipPilotInput.isSeated(mc) && mc.screen == null;
    });

    @EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    @SuppressWarnings("removal")
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(BOOST);
        }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(Rac1Ships.SHIP.get(), TravelShipPlaceholderRenderer::new);
        }
    }

    @EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
    public static final class Controls {
        @SubscribeEvent
        public static void traceView(RenderLevelStageEvent event) {
            if (!ShipMotionTrace.ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            var camera = event.getCamera().getPosition();
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Rac1TravelShip ship) || !ship.takeMotionViewSample(mc.level.getGameTime())) continue;
                var hull = ship.getPosition(partial);
                var rider = ship.getFirstPassenger();
                var feet = rider == null ? null : rider.getPosition(partial);
                double metres = camera.distanceTo(hull);
                String view = rider == mc.player ? "pilot" : metres <= 16 ? "near_observer" : "far_observer";
                ShipMotionTrace.log(ship, "render_view", "view", view, "camera_type", mc.options.getCameraType(),
                        "distance", metres, "partial", partial, "hull", hull, "rider_feet", feet,
                        "rider_minus_hull", feet == null ? null : feet.subtract(hull), "camera", camera);
            }
        }

        @SubscribeEvent
        public static void suppressNativeSeatInput(MovementInputUpdateEvent event) {
            if (event.getEntity().getVehicle() instanceof Rac1TravelShip) {
                // The physical Sneak mapping is still sampled below, including in Toggle mode.
                event.getInput().shiftKeyDown = false;
            }
        }

        @SubscribeEvent
        public static void interruptInMenu(ScreenEvent.Opening event) {
            if (ShipPilotInput.isSeated(Minecraft.getInstance())) ShipPilotInput.interrupt();
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null
                    || !(mc.player.getVehicle() instanceof Rac1TravelShip ship) || ship.seatSession() == 0) {
                ShipPilotInput.interrupt();
                return;
            }
            var options = mc.options;
            boolean active = mc.isWindowActive() && mc.screen == null
                    && ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING;
            var keys = new ShipPilotInput.Keys(options.keyUp, options.keyDown, options.keyLeft,
                    options.keyRight, options.keyJump, options.keyShift, BOOST);
            PacketDistributor.sendToServer(ShipPilotInput.FRAMES.frame(ship.getId(), ship.seatSession(),
                    ship.groundSerial(), active, keys, key -> Rac1HydroHeldKeys.down(key, true)));
        }

        @SubscribeEvent
        public static void hud(RenderGuiEvent.Post event) {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null
                    || !(mc.player.getVehicle() instanceof Rac1TravelShip ship) || !ship.isBoosted()) return;
            var graphics = event.getGuiGraphics();
            var label = Component.translatable("hud.racmc.travel_ship.boost");
            int x = Math.min(graphics.guiWidth() - mc.font.width(label) - 6, graphics.guiWidth() / 2 + 98);
            graphics.drawString(mc.font, label, Math.max(4, x), graphics.guiHeight() - 26, 0xE2BA65, true);
        }
    }

    private ShipClient() {}
}
