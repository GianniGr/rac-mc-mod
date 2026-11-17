package com.github.giannigr.racmc.client.actions;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.actions.WrenchActionNetwork;
import com.github.giannigr.racmc.actions.WrenchActionPresentation;
import com.github.giannigr.racmc.actions.WrenchActionState;
import com.github.giannigr.racmc.actions.WrenchActions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Accepted-state view only. The render path does not predict damage or mutate flight simulation. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class WrenchActionClient {
    private static final Map<UUID, WrenchActionNetwork.Snapshot> STATES = new HashMap<>();
    private static long intentSerial;
    private static boolean pressed;
    private static int airTicks;
    private static net.minecraft.client.multiplayer.ClientLevel world;

    static {
        WrenchActionNetwork.clientReceiver(WrenchActionClient::receive);
    }

    private WrenchActionClient() {}

    private static void receive(WrenchActionNetwork.Snapshot snapshot) {
        WrenchActionNetwork.Snapshot previous = STATES.get(snapshot.player());
        if (previous == null || snapshot.serverTick() >= previous.serverTick()) {
            STATES.put(snapshot.player(), snapshot);
        }
    }

    /** P27 renderer consumes this without advancing action time. Idle on stale/missing owner. */
    public static WrenchActionPresentation presentation(Player player) {
        WrenchActionNetwork.Snapshot snapshot = STATES.get(player.getUUID());
        if (snapshot == null || !WrenchActions.isWrench(player.getMainHandItem())
                || player.level().getGameTime() - snapshot.serverTick() > 40) {
            return WrenchActionPresentation.idle();
        }
        return snapshot.view();
    }

    @SubscribeEvent
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || mc.screen != null
                || !WrenchActions.isWrench(mc.player.getMainHandItem())) return;
        boolean repeated = pressed;
        pressed = true;
        WrenchActionNetwork.Snapshot snapshot = STATES.get(mc.player.getUUID());
        if (snapshot == null || !snapshot.available()) return;
        boolean busy = presentation(mc.player).phase() != WrenchActionPresentation.Phase.IDLE;
        boolean crouching = mc.player.isShiftKeyDown();
        // Ordinary direct block mining/crate transactions remain P27-owned and immediate.
        // Crouch+attack still takes priority over block/entity/miss for cosmetic throw.
        double minimumClearance = mc.player.getDeltaMovement().y < 0 ? 0.57 : 0.1;
        boolean airborne = !mc.player.onGround() && airTicks >= 4
                && !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult)
                && WrenchActions.groundClearance(mc.player) >= minimumClearance;
        if (!busy && !crouching && !airborne) return; // P27 owns ordinary attack/mining.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (repeated || busy) return;
        WrenchActionState.Intent action = crouching
                ? WrenchActionState.Intent.THROW : WrenchActionState.Intent.SLAM;
        PacketDistributor.sendToServer(new WrenchActionNetwork.Intent(++intentSerial,
                action.ordinal()));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) {
            STATES.clear();
            world = mc.level;
            airTicks = 0;
            pressed = false;
        }
        if (!mc.options.keyAttack.isDown()) pressed = false;
        airTicks = mc.player == null || mc.player.onGround() ? 0 : Math.min(100, airTicks + 1);
        if (mc.level != null) {
            long now = mc.level.getGameTime();
            STATES.entrySet().removeIf(entry -> now - entry.getValue().serverTick() > 100);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        intentSerial = 0;
        pressed = false;
        airTicks = 0;
        world = null;
    }
}
