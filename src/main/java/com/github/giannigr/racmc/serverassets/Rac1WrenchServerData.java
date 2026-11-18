package com.github.giannigr.racmc.serverassets;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Headless lifecycle adapter only. P28 owns sampler/capability networking and all action state. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1WrenchServerData {
    private static final ConcurrentHashMap<MinecraftServer, Rac1WrenchDataProvider> PROVIDERS =
            new ConcurrentHashMap<>();

    private Rac1WrenchServerData() { }

    public static Optional<Rac1WrenchData> data(MinecraftServer server) {
        var provider = PROVIDERS.get(server);
        return provider == null ? Optional.empty() : provider.data();
    }

    public static Rac1WrenchDataStatus status(MinecraftServer server) {
        var provider = PROVIDERS.get(server);
        return provider == null ? new Rac1WrenchDataStatus(
                Rac1WrenchDataStatus.State.MISSING, "Server-local wrench provider not initialized.",
                Rac1WrenchDataStatus.Origin.NONE) : provider.status();
    }

    @SubscribeEvent
    public static void onStarting(ServerAboutToStartEvent event) {
        PROVIDERS.computeIfAbsent(event.getServer(), server -> Rac1WrenchDataProvider.open(
                FMLPaths.CONFIGDIR.get().resolve(RacMcConstants.MOD_ID), server.isDedicatedServer(),
                status -> {
                    if (status.state() == Rac1WrenchDataStatus.State.FAILED
                            || status.state() == Rac1WrenchDataStatus.State.MISSING) {
                        RacMcMod.LOGGER.warn("Server-local wrench data: {}. Other content is not gated.",
                                status.detail());
                    } else {
                        RacMcMod.LOGGER.info("Server-local wrench data: {} ({})",
                                status.state(), status.origin());
                    }
                }));
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        var provider = PROVIDERS.remove(event.getServer());
        if (provider != null) {
            try {
                provider.close();
            } catch (IOException failure) {
                RacMcMod.LOGGER.error("The owned server-local wrench import did not stop cleanly.",
                        failure);
            }
        }
    }
}
