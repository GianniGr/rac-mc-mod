package com.github.giannigr.racmc;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the mod.
 *
 * <p>The {@link Mod#value() mod id} must match the {@code modId} entry of the generated
 * {@code META-INF/neoforge.mods.toml} and the {@code MOD_ID} constant in {@link RacMcConstants}.
 * The focused tests in {@code src/test/java} verify that wiring.
 *
 * <p>This class currently only establishes the mod and logs its start-up. The selection and the
 * validation of a local disc image live in {@code com.github.giannigr.racmc.iso}, the client side
 * of the importer lives in {@code com.github.giannigr.racmc.client}. Gameplay content, the
 * extraction of the assets and their rendering are tracked in separate issues and are not
 * implemented here.
 */
@Mod(RacMcConstants.MOD_ID)
public class RacMcMod {

    /** Directly referenced slf4j logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The constructor for the mod class is the first code that runs when the mod is loaded.
     * FML recognises the parameter types and passes them in automatically.
     *
     * @param modEventBus the mod specific event bus
     * @param modContainer the container holding this mod's metadata
     */
    public RacMcMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RaC Mod initialised: id={}, name={}, version={}",
                RacMcConstants.MOD_ID,
                RacMcConstants.MOD_NAME,
                modContainer.getModInfo().getVersion().toString());
    }
}
