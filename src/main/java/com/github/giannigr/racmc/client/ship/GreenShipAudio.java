package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.ship.Rac1TravelShip;
import com.github.giannigr.racmc.ship.ShipEngineBeat;
import com.github.giannigr.racmc.ship.ShipThrust;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Reversible vanilla-sound adaptation, independent of hull imports and the render frame rate. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class GreenShipAudio {
    private static final Map<Rac1TravelShip, View> TRACKS = new IdentityHashMap<>();

    private GreenShipAudio() {}

    private static final class View {
        final GreenShipAudioState state = new GreenShipAudioState();
        EngineLoop loop;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        var level = client.level;
        TRACKS.entrySet().removeIf(entry -> {
            var ship = entry.getKey();
            if (level != null && ship.level() == level && !ship.isRemoved() && level.getEntity(ship.getId()) == ship) {
                return false;
            }
            stop(entry.getValue());
            return true;
        });
        if (level == null) return;
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof Rac1TravelShip ship) || ship.isRemoved()) continue;
            var view = TRACKS.computeIfAbsent(ship, ignored -> new View());
            var thrust = ship.engineThrust();
            if (ship.boostThrust().vertical() != Rac1TravelShip.VerticalBoost.NONE) thrust = ShipThrust.BOOST;
            var frame = view.state.sample(ship.engineState(), thrust, ship.engineBeat(),
                    ship.accessPhase() == Rac1TravelShip.AccessPhase.PILOTING, level.getGameTime());
            if (frame.cue() != null) {
                boolean lift = frame.cue() == ShipEngineBeat.Kind.LIFTOFF;
                level.playLocalSound(ship.getX(), ship.getY(), ship.getZ(),
                        lift ? SoundEvents.FIRECHARGE_USE : SoundEvents.PISTON_CONTRACT,
                        SoundSource.NEUTRAL, lift ? .65f : .60f, lift ? .68f : .72f, false);
            }
            if (!frame.loop()) {
                stop(view);
            } else if (view.loop == null || view.loop.isStopped()) {
                view.loop = new EngineLoop(ship, frame.thrust());
                client.getSoundManager().play(view.loop);
            } else {
                view.loop.thrust = frame.thrust();
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRACKS.values().forEach(GreenShipAudio::stop);
        TRACKS.clear();
    }

    private static void stop(View view) {
        if (view.loop != null) {
            Minecraft.getInstance().getSoundManager().stop(view.loop);
            view.loop = null;
        }
    }

    private static final class EngineLoop extends AbstractTickableSoundInstance {
        private final Rac1TravelShip ship;
        private ShipThrust thrust;

        EngineLoop(Rac1TravelShip ship, ShipThrust thrust) {
            super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.ship = ship;
            this.thrust = thrust;
            looping = true;
            delay = 0;
            tick();
        }

        @Override
        public void tick() {
            if (ship.isRemoved() || ship.level() != Minecraft.getInstance().level) {
                stop();
                return;
            }
            x = ship.getX();
            y = ship.getY();
            z = ship.getZ();
            volume = switch (thrust) { case OFF -> .18f; case NORMAL -> .34f; case BOOST -> .55f; };
            pitch = switch (thrust) { case OFF -> .60f; case NORMAL -> .80f; case BOOST -> 1.04f; };
        }
    }
}
