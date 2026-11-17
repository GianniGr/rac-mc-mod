package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shared, frozen semantic registry; no client/audio-device/asset references. */
public final class Rac1Sounds {
    private static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(Registries.SOUND_EVENT, RacMcConstants.MOD_ID);
    static {
        for (var event : Rac1AudioEvent.values()) {
            REGISTER.register(event.id(), () -> SoundEvent.createVariableRangeEvent(location(event)));
        }
    }
    private Rac1Sounds() {}
    public static ResourceLocation location(Rac1AudioEvent event) {
        return ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, event.id());
    }
    public static void register(IEventBus bus) { REGISTER.register(bus); }
}
